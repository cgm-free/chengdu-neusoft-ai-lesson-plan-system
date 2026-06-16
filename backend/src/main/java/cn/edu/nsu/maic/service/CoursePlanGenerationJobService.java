package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.CoursePlanDtos;
import cn.edu.nsu.maic.dto.UserInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class CoursePlanGenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(CoursePlanGenerationJobService.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CoursePlanService coursePlanService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public CoursePlanGenerationJobService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            CoursePlanService coursePlanService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.coursePlanService = coursePlanService;
    }

    @PostConstruct
    public void cancelInterruptedJobs() {
        int count = jdbcTemplate.update(
                "update course_plan_generation_job set status = 'cancelled', stage = 'cancelled', message = ?, error_json = null where status in ('pending', 'running')",
                "服务器重启，已停止未完成的课程教案生成任务，请重新提交。"
        );
        if (count > 0) {
            log.warn("Cancelled {} interrupted course plan generation jobs on startup", count);
        }
    }

    public CoursePlanDtos.GenerationJobSummary submit(
            Long coursePlanId,
            Long sourceCoursePlanId,
            MultipartFile templateFile,
            MultipartFile courseStandardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles,
            CoursePlanDtos.GenerateRequest request,
            UserInfo user
    ) throws IOException {
        validateSubmit(coursePlanId, sourceCoursePlanId, templateFile, courseStandardFile, pptFiles, referenceFiles, request, user);
        CoursePlanDtos.AnalysisResult analysis = request.analysis();
        String courseName = firstNonBlank(analysis.basicInfo() == null ? "" : analysis.basicInfo().courseName());
        String semester = firstNonBlank(analysis.basicInfo() == null ? "" : analysis.basicInfo().semester());
        Long activeJobId = findActiveJob(coursePlanId, courseName, semester, user);
        if (activeJobId != null) {
            return getJob(activeJobId, user);
        }

        Long jobId = insertJob(coursePlanId, courseName, semester, request, user);
        saveJobMaterials(jobId, sourceCoursePlanId, templateFile, courseStandardFile, pptFiles, referenceFiles);
        executorService.submit(() -> runJob(jobId, user));
        return getJob(jobId, user);
    }

    public CoursePlanDtos.GenerationJobSummary getJob(Long jobId, UserInfo user) {
        JobRecord record = loadJob(jobId);
        assertAccess(record.userId(), user);
        return toSummary(record);
    }

    public CoursePlanDtos.GenerationJobSummary cancelJob(Long jobId, UserInfo user) {
        JobRecord record = loadJob(jobId);
        assertAccess(record.userId(), user);
        if ("succeeded".equals(record.status())) {
            throw new IllegalStateException("课程教案已生成完成，不能停止。");
        }
        if ("failed".equals(record.status())) {
            throw new IllegalStateException("课程教案生成已失败，不能停止。");
        }
        if (!"cancelled".equals(record.status())) {
            jdbcTemplate.update(
                    "update course_plan_generation_job set status = 'cancelled', stage = 'cancelled', message = ?, error_json = null where id = ? and status in ('pending', 'running')",
                    "已停止课程教案生成",
                    jobId
            );
        }
        return getJob(jobId, user);
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private void runJob(Long jobId, UserInfo user) {
        try {
            JobRecord job = loadJob(jobId);
            ensureJobNotCancelled(jobId);
            updateProgress(jobId, "running", "prepare", 0, 1, "读取课程教案生成任务");
            CoursePlanDtos.GenerateRequest request = readRequest(job.requestJson());
            List<JobMaterialBinary> materials = listJobMaterials(jobId);
            MultipartFile template = firstMaterial(materials, "template");
            MultipartFile courseStandard = firstMaterial(materials, "course-standard");
            List<MultipartFile> ppts = materialsByRole(materials, "ppt");
            List<MultipartFile> references = materialsByRole(materials, "reference");
            CoursePlanGenerationProgress progress = new CoursePlanGenerationProgress() {
                @Override
                public void update(String stage, int current, int total, String message) {
                    updateProgress(jobId, "running", stage, current, total, message);
                }

                @Override
                public void snapshot(CoursePlanDtos.AnalysisResult analysis, CoursePlanDtos.DocumentContent content) {
                    savePartialSnapshot(jobId, analysis, content);
                }

                @Override
                public void assertNotCancelled() {
                    ensureJobNotCancelled(jobId);
                }
            };

            CoursePlanDtos.Detail detail;
            if (job.coursePlanId() == null) {
                detail = coursePlanService.generate(template, courseStandard, ppts, references, request, user, progress);
            } else {
                detail = coursePlanService.regenerate(job.coursePlanId(), template, courseStandard, ppts, references, request, user, progress);
            }
            ensureJobNotCancelled(jobId);
            jdbcTemplate.update(
                    "update course_plan_generation_job set status = 'succeeded', stage = 'completed', progress_current = progress_total, message = ?, course_plan_id = ?, error_json = null where id = ? and status <> 'cancelled'",
                    "课程教案生成完成",
                    detail.id(),
                    jobId
            );
        } catch (CancellationException exception) {
            log.info("Course plan generation job {} cancelled", jobId);
            markJobCancelled(jobId);
        } catch (CoursePlanGenerationException exception) {
            if (isJobCancelled(jobId)) {
                markJobCancelled(jobId);
                return;
            }
            log.warn("Course plan generation job {} failed: {}", jobId, exception.getMessage());
            Long partialCoursePlanId = publishPartialDraftIfAvailable(jobId, user);
            failJob(jobId, exception.error(), partialCoursePlanId);
        } catch (Exception exception) {
            if (isJobCancelled(jobId)) {
                markJobCancelled(jobId);
                return;
            }
            log.error("Course plan generation job " + jobId + " failed", exception);
            Long partialCoursePlanId = publishPartialDraftIfAvailable(jobId, user);
            failJob(jobId, new CoursePlanDtos.GenerationError(
                    null,
                    null,
                    "",
                    "job",
                    "",
                    null,
                    null,
                    readableMessage(exception)
            ), partialCoursePlanId);
        }
    }

    private void validateSubmit(
            Long coursePlanId,
            Long sourceCoursePlanId,
            MultipartFile templateFile,
            MultipartFile courseStandardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles,
            CoursePlanDtos.GenerateRequest request,
            UserInfo user
    ) {
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("用户未登录");
        }
        if (request == null || request.analysis() == null) {
            throw new IllegalArgumentException("缺少课程教案分析结果，不能启动生成任务。");
        }
        CoursePlanDtos.BasicInfo basicInfo = request.analysis().basicInfo();
        if (basicInfo == null || firstNonBlank(basicInfo.courseName()).isBlank()) {
            throw new IllegalArgumentException("缺少课程名称，不能启动生成任务。");
        }
        if (coursePlanId != null && sourceCoursePlanId != null) {
            throw new IllegalArgumentException("不能同时指定覆盖记录和另存来源记录。");
        }
        if (sourceCoursePlanId != null) {
            assertSourceCoursePlanUsable(sourceCoursePlanId, user);
        }
        if (coursePlanId == null) {
            if (sourceCoursePlanId != null && !hasCoreUpload(courseStandardFile, pptFiles, referenceFiles)) {
                return;
            }
            if (templateFile == null || templateFile.isEmpty()) {
                throw new IllegalArgumentException("生成课程教案前必须保留教案模板文件。");
            }
            if (courseStandardFile == null || courseStandardFile.isEmpty()) {
                throw new IllegalArgumentException("生成课程教案前必须保留课程标准文件。");
            }
            if (normalizeFiles(pptFiles).isEmpty()) {
                throw new IllegalArgumentException("生成课程教案前必须保留至少一份 PPT/课件。");
            }
            if (normalizeFiles(referenceFiles).isEmpty()) {
                throw new IllegalArgumentException("生成课程教案前必须保留教学日历文件。");
            }
            return;
        }

        if (hasCoreUpload(courseStandardFile, pptFiles, referenceFiles) && !hasCompleteCoreUpload(courseStandardFile, pptFiles, referenceFiles)) {
            throw new IllegalArgumentException("重新上传材料时，请同时上传课程标准、PPT/课件和教学日历。");
        }
    }

    private Long findActiveJob(Long coursePlanId, String courseName, String semester, UserInfo user) {
        List<Long> activeJobIds;
        if (coursePlanId != null) {
            activeJobIds = jdbcTemplate.query(
                    "select id from course_plan_generation_job where user_id = ? and course_plan_id = ? and status in ('pending', 'running') order by id desc limit 1",
                    (rs, rowNum) -> rs.getLong("id"),
                    user.getId(),
                    coursePlanId
            );
        } else {
            activeJobIds = jdbcTemplate.query(
                    "select id from course_plan_generation_job where user_id = ? and course_plan_id is null and course_name = ? and coalesce(semester, '') = ? and status in ('pending', 'running') order by id desc limit 1",
                    (rs, rowNum) -> rs.getLong("id"),
                    user.getId(),
                    courseName,
                    firstNonBlank(semester)
            );
        }
        return activeJobIds.isEmpty() ? null : activeJobIds.get(0);
    }

    private Long insertJob(
            Long coursePlanId,
            String courseName,
            String semester,
            CoursePlanDtos.GenerateRequest request,
            UserInfo user
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String requestJson = writeJson(request);
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into course_plan_generation_job (user_id, course_plan_id, course_name, semester, status, stage, progress_current, progress_total, message, request_json) " +
                            "values (?, ?, ?, ?, 'pending', 'queued', 0, 1, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, user.getId());
            if (coursePlanId == null) {
                ps.setObject(2, null);
            } else {
                ps.setLong(2, coursePlanId);
            }
            ps.setString(3, courseName);
            ps.setString(4, firstNonBlank(semester));
            ps.setString(5, "课程教案生成任务已提交");
            ps.setString(6, requestJson);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建课程教案生成任务失败：未获取到任务 ID");
        }
        return key.longValue();
    }

    private void saveJobMaterials(
            Long jobId,
            Long sourceCoursePlanId,
            MultipartFile templateFile,
            MultipartFile courseStandardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles
    ) throws IOException {
        if (sourceCoursePlanId != null && !hasCoreUpload(courseStandardFile, pptFiles, referenceFiles)) {
            copyCoursePlanMaterialsToJob(jobId, sourceCoursePlanId);
            return;
        }
        int sortOrder = 0;
        if (templateFile != null && !templateFile.isEmpty()) {
            insertJobMaterial(jobId, "template", templateFile, sortOrder++);
        }
        if (courseStandardFile != null && !courseStandardFile.isEmpty()) {
            insertJobMaterial(jobId, "course-standard", courseStandardFile, sortOrder++);
        }
        for (MultipartFile pptFile : normalizeFiles(pptFiles)) {
            insertJobMaterial(jobId, "ppt", pptFile, sortOrder++);
        }
        for (MultipartFile referenceFile : normalizeFiles(referenceFiles)) {
            insertJobMaterial(jobId, "reference", referenceFile, sortOrder++);
        }
    }

    private void insertJobMaterial(Long jobId, String role, MultipartFile file, int sortOrder) throws IOException {
        jdbcTemplate.update(
                "insert into course_plan_generation_job_material (job_id, role, file_name, file_type, file_blob, sort_order) values (?, ?, ?, ?, ?, ?)",
                jobId,
                role,
                fileNameOf(file),
                fileTypeOf(fileNameOf(file)),
                file.getBytes(),
                sortOrder
        );
    }

    private void copyCoursePlanMaterialsToJob(Long jobId, Long sourceCoursePlanId) {
        SourceTemplate template = loadSourceTemplate(sourceCoursePlanId);
        int sortOrder = 0;
        insertJobMaterial(jobId, "template", template.fileName(), fileTypeOf(template.fileName()), template.bytes(), sortOrder++);
        List<JobMaterialBinary> materials = jdbcTemplate.query(
                "select id, role, file_name, file_type, file_blob, sort_order from course_plan_material where course_plan_id = ? order by sort_order, id",
                (rs, rowNum) -> new JobMaterialBinary(
                        rs.getLong("id"),
                        rs.getString("role"),
                        rs.getString("file_name"),
                        rs.getString("file_type"),
                        rs.getBytes("file_blob"),
                        rs.getInt("sort_order")
                ),
                sourceCoursePlanId
        );
        for (JobMaterialBinary material : materials) {
            insertJobMaterial(jobId, material.role(), material.fileName(), material.fileType(), material.bytes(), sortOrder++);
        }
    }

    private void insertJobMaterial(Long jobId, String role, String fileName, String fileType, byte[] bytes, int sortOrder) {
        jdbcTemplate.update(
                "insert into course_plan_generation_job_material (job_id, role, file_name, file_type, file_blob, sort_order) values (?, ?, ?, ?, ?, ?)",
                jobId,
                role,
                fileName,
                fileType,
                bytes,
                sortOrder
        );
    }

    private JobRecord loadJob(Long jobId) {
        List<JobRecord> records = jdbcTemplate.query(
                "select id, user_id, course_plan_id, course_name, semester, status, stage, progress_current, progress_total, message, request_json, error_json, partial_analysis_json, partial_content_json, created_at, updated_at " +
                        "from course_plan_generation_job where id = ?",
                (rs, rowNum) -> new JobRecord(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getObject("course_plan_id") == null ? null : rs.getLong("course_plan_id"),
                        rs.getString("course_name"),
                        rs.getString("semester"),
                        rs.getString("status"),
                        rs.getString("stage"),
                        rs.getInt("progress_current"),
                        rs.getInt("progress_total"),
                        rs.getString("message"),
                        rs.getString("request_json"),
                        rs.getString("error_json"),
                        rs.getString("partial_analysis_json"),
                        rs.getString("partial_content_json"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("updated_at")
                ),
                jobId
        );
        if (records.isEmpty()) {
            throw new IllegalArgumentException("课程教案生成任务不存在");
        }
        return records.get(0);
    }

    private List<JobMaterialBinary> listJobMaterials(Long jobId) {
        return jdbcTemplate.query(
                "select id, role, file_name, file_type, file_blob, sort_order from course_plan_generation_job_material where job_id = ? order by sort_order, id",
                (rs, rowNum) -> new JobMaterialBinary(
                        rs.getLong("id"),
                        rs.getString("role"),
                        rs.getString("file_name"),
                        rs.getString("file_type"),
                        rs.getBytes("file_blob"),
                        rs.getInt("sort_order")
                ),
                jobId
        );
    }

    private MultipartFile firstMaterial(List<JobMaterialBinary> materials, String role) {
        return materials.stream()
                .filter(item -> role.equals(item.role()))
                .findFirst()
                .map(this::toMultipartFile)
                .orElse(null);
    }

    private List<MultipartFile> materialsByRole(List<JobMaterialBinary> materials, String role) {
        return materials.stream()
                .filter(item -> role.equals(item.role()))
                .map(this::toMultipartFile)
                .toList();
    }

    private MultipartFile toMultipartFile(JobMaterialBinary value) {
        return new StoredMultipartFile(value.fileName(), value.fileType(), value.bytes());
    }

    private void updateProgress(Long jobId, String status, String stage, int current, int total, String message) {
        ensureJobNotCancelled(jobId);
        jdbcTemplate.update(
                "update course_plan_generation_job set status = ?, stage = ?, progress_current = ?, progress_total = ?, message = ? where id = ? and status <> 'cancelled'",
                status,
                firstNonBlank(stage),
                Math.max(0, current),
                Math.max(1, total),
                firstNonBlank(message),
                jobId
        );
        log.info("Course plan generation job {} [{}] {}/{} {}", jobId, stage, current, total, message);
    }

    private void savePartialSnapshot(Long jobId, CoursePlanDtos.AnalysisResult analysis, CoursePlanDtos.DocumentContent content) {
        if (isJobCancelled(jobId)) {
            return;
        }
        if (analysis == null || content == null) {
            return;
        }
        jdbcTemplate.update(
                "update course_plan_generation_job set partial_analysis_json = ?, partial_content_json = ? where id = ?",
                writeJson(analysis),
                writeJson(content),
                jobId
        );
    }

    private Long publishPartialDraftIfAvailable(Long jobId, UserInfo user) {
        try {
            JobRecord job = loadJob(jobId);
            if (job.coursePlanId() != null) {
                return job.coursePlanId();
            }
            if (job.partialAnalysisJson() == null || job.partialAnalysisJson().isBlank()
                    || job.partialContentJson() == null || job.partialContentJson().isBlank()) {
                return null;
            }
            List<JobMaterialBinary> materials = listJobMaterials(jobId);
            MultipartFile template = firstMaterial(materials, "template");
            MultipartFile courseStandard = firstMaterial(materials, "course-standard");
            List<MultipartFile> ppts = materialsByRole(materials, "ppt");
            List<MultipartFile> references = materialsByRole(materials, "reference");
            Long partialCoursePlanId = coursePlanService.createPartialDraft(
                    user,
                    template,
                    courseStandard,
                    ppts,
                    references,
                    readAnalysis(job.partialAnalysisJson()),
                    readContent(job.partialContentJson())
            );
            jdbcTemplate.update("update course_plan_generation_job set course_plan_id = ? where id = ?", partialCoursePlanId, jobId);
            return partialCoursePlanId;
        } catch (Exception exception) {
            log.warn("Course plan generation job {} partial draft publish failed: {}", jobId, readableMessage(exception));
            return null;
        }
    }

    private void failJob(Long jobId, CoursePlanDtos.GenerationError error, Long partialCoursePlanId) {
        if (isJobCancelled(jobId)) {
            markJobCancelled(jobId);
            return;
        }
        String message = error == null ? "课程教案生成失败" : error.message();
        if (partialCoursePlanId != null) {
            message = message + "。已保留已生成部分，可进入编辑页查看。";
        }
        jdbcTemplate.update(
                "update course_plan_generation_job set status = 'failed', stage = 'failed', message = ?, error_json = ?, course_plan_id = coalesce(?, course_plan_id) where id = ? and status <> 'cancelled'",
                message,
                writeJson(error),
                partialCoursePlanId,
                jobId
        );
    }

    private void ensureJobNotCancelled(Long jobId) {
        if (isJobCancelled(jobId)) {
            throw new CancellationException("课程教案生成已停止");
        }
    }

    private boolean isJobCancelled(Long jobId) {
        String status = jdbcTemplate.queryForObject(
                "select status from course_plan_generation_job where id = ?",
                String.class,
                jobId
        );
        return "cancelled".equals(status);
    }

    private void markJobCancelled(Long jobId) {
        jdbcTemplate.update(
                "update course_plan_generation_job set status = 'cancelled', stage = 'cancelled', message = ?, error_json = null where id = ? and status <> 'succeeded'",
                "已停止课程教案生成",
                jobId
        );
    }

    private CoursePlanDtos.GenerationJobSummary toSummary(JobRecord record) {
        return new CoursePlanDtos.GenerationJobSummary(
                record.id(),
                record.status(),
                record.stage(),
                record.progressCurrent(),
                record.progressTotal(),
                record.message(),
                record.coursePlanId(),
                readError(record.errorJson()),
                record.createdAt() == null ? null : record.createdAt().toLocalDateTime(),
                record.updatedAt() == null ? null : record.updatedAt().toLocalDateTime()
        );
    }

    private CoursePlanDtos.GenerateRequest readRequest(String json) {
        try {
            return objectMapper.readValue(json, CoursePlanDtos.GenerateRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("课程教案生成任务参数解析失败", e);
        }
    }

    private CoursePlanDtos.AnalysisResult readAnalysis(String json) {
        try {
            return objectMapper.readValue(json, CoursePlanDtos.AnalysisResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("课程教案部分草稿分析结果解析失败", e);
        }
    }

    private CoursePlanDtos.DocumentContent readContent(String json) {
        try {
            return objectMapper.readValue(json, CoursePlanDtos.DocumentContent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("课程教案部分草稿内容解析失败", e);
        }
    }

    private CoursePlanDtos.GenerationError readError(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, CoursePlanDtos.GenerationError.class);
        } catch (JsonProcessingException e) {
            return new CoursePlanDtos.GenerationError(null, null, "", "job", "", null, null, "生成错误详情解析失败");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("课程教案生成任务 JSON 序列化失败", e);
        }
    }

    private void assertAccess(Long ownerId, UserInfo user) {
        if (user == null) {
            throw new IllegalStateException("用户未登录");
        }
        if (!user.isAdmin() && !user.getId().equals(ownerId)) {
            throw new IllegalStateException("无权访问该课程教案生成任务");
        }
    }

    private void assertSourceCoursePlanUsable(Long sourceCoursePlanId, UserInfo user) {
        List<Long> owners = jdbcTemplate.query(
                "select user_id from course_plan where id = ? and status <> 'deleted'",
                (rs, rowNum) -> rs.getLong("user_id"),
                sourceCoursePlanId
        );
        if (owners.isEmpty()) {
            throw new IllegalArgumentException("另存为新教案的来源记录不存在。");
        }
        assertAccess(owners.get(0), user);
        SourceTemplate template = loadSourceTemplate(sourceCoursePlanId);
        if (template.bytes() == null || template.bytes().length == 0) {
            throw new IllegalArgumentException("来源记录缺少教案模板文件，不能另存为新教案。");
        }
        Integer standardCount = materialCount(sourceCoursePlanId, "course-standard");
        Integer pptCount = materialCount(sourceCoursePlanId, "ppt");
        Integer referenceCount = materialCount(sourceCoursePlanId, "reference");
        if (standardCount == null || standardCount <= 0) {
            throw new IllegalArgumentException("来源记录缺少课程标准文件，不能另存为新教案。");
        }
        if (pptCount == null || pptCount <= 0) {
            throw new IllegalArgumentException("来源记录缺少 PPT/课件文件，不能另存为新教案。");
        }
        if (referenceCount == null || referenceCount <= 0) {
            throw new IllegalArgumentException("来源记录缺少教学日历文件，不能另存为新教案。");
        }
    }

    private Integer materialCount(Long coursePlanId, String role) {
        return jdbcTemplate.queryForObject(
                "select count(*) from course_plan_material where course_plan_id = ? and role = ?",
                Integer.class,
                coursePlanId,
                role
        );
    }

    private SourceTemplate loadSourceTemplate(Long coursePlanId) {
        List<SourceTemplate> templates = jdbcTemplate.query(
                "select template_file_name, template_file from course_plan where id = ? and status <> 'deleted'",
                (rs, rowNum) -> new SourceTemplate(
                        firstNonBlank(rs.getString("template_file_name"), "course-plan-template.docx"),
                        rs.getBytes("template_file")
                ),
                coursePlanId
        );
        if (templates.isEmpty()) {
            throw new IllegalArgumentException("课程教案来源记录不存在。");
        }
        return templates.get(0);
    }

    private boolean hasCoreUpload(
            MultipartFile courseStandardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles
    ) {
        return courseStandardFile != null && !courseStandardFile.isEmpty()
                || !normalizeFiles(pptFiles).isEmpty()
                || !normalizeFiles(referenceFiles).isEmpty();
    }

    private boolean hasCompleteCoreUpload(
            MultipartFile courseStandardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles
    ) {
        return courseStandardFile != null && !courseStandardFile.isEmpty()
                && !normalizeFiles(pptFiles).isEmpty()
                && !normalizeFiles(referenceFiles).isEmpty();
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
    }

    private String fileNameOf(MultipartFile file) {
        return file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
    }

    private String fileTypeOf(String fileName) {
        String value = firstNonBlank(fileName);
        int index = value.lastIndexOf('.');
        if (index < 0 || index == value.length() - 1) {
            return "";
        }
        return value.substring(index + 1).toLowerCase();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = value == null ? "" : value.trim();
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return "";
    }

    private String readableMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record JobRecord(
            Long id,
            Long userId,
            Long coursePlanId,
            String courseName,
            String semester,
            String status,
            String stage,
            Integer progressCurrent,
            Integer progressTotal,
            String message,
            String requestJson,
            String errorJson,
            String partialAnalysisJson,
            String partialContentJson,
            Timestamp createdAt,
            Timestamp updatedAt
    ) {
    }

    private record JobMaterialBinary(
            Long id,
            String role,
            String fileName,
            String fileType,
            byte[] bytes,
            Integer sortOrder
    ) {
    }

    private record SourceTemplate(
            String fileName,
            byte[] bytes
    ) {
    }
}
