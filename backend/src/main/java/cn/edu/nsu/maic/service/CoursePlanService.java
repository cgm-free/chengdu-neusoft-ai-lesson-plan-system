package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.CoursePlanDtos;
import cn.edu.nsu.maic.dto.UserInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@Service
public class CoursePlanService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CoursePlanContentBuilderService coursePlanContentBuilderService;
    private final CoursePlanAiGenerationService coursePlanAiGenerationService;
    private final CoursePlanAnalysisService coursePlanAnalysisService;

    public CoursePlanService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            CoursePlanContentBuilderService coursePlanContentBuilderService,
            CoursePlanAiGenerationService coursePlanAiGenerationService,
            CoursePlanAnalysisService coursePlanAnalysisService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.coursePlanContentBuilderService = coursePlanContentBuilderService;
        this.coursePlanAiGenerationService = coursePlanAiGenerationService;
        this.coursePlanAnalysisService = coursePlanAnalysisService;
    }

    public CoursePlanDtos.Detail generate(
            MultipartFile templateFile,
            MultipartFile courseStandardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles,
            CoursePlanDtos.GenerateRequest request,
            UserInfo user
    ) throws IOException {
        return generate(templateFile, courseStandardFile, pptFiles, referenceFiles, request, user, CoursePlanGenerationProgress.NOOP);
    }

    public CoursePlanDtos.Detail generate(
            MultipartFile templateFile,
            MultipartFile courseStandardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles,
            CoursePlanDtos.GenerateRequest request,
            UserInfo user,
            CoursePlanGenerationProgress progress
    ) throws IOException {
        validateTemplateFile(templateFile);
        if (courseStandardFile == null || courseStandardFile.isEmpty()) {
            throw new IllegalArgumentException("生成课程教案前必须保留课程标准文件。");
        }
        if (pptFiles == null || pptFiles.stream().noneMatch(file -> file != null && !file.isEmpty())) {
            throw new IllegalArgumentException("生成课程教案前必须保留至少一份 PPT/课件。");
        }
        if (request == null || request.analysis() == null) {
            throw new IllegalArgumentException("缺少课程教案分析结果");
        }
        CoursePlanDtos.AnalysisResult normalizedAnalysis = withTeacherRequirements(request.analysis(), request.teacherRequirements());
        CoursePlanDtos.DocumentContent content = buildAiEnhancedContent(normalizedAnalysis, progress);
        progress.update("saving", 1, 1, "保存课程教案记录");
        Long id = insertCoursePlan(user, templateFile, normalizedAnalysis, content);
        saveMaterials(id, courseStandardFile, pptFiles, referenceFiles);
        return getDetail(id, user);
    }

    public CoursePlanDtos.AnalysisResult analyzeExisting(
            Long id,
            MultipartFile templateFile,
            MultipartFile courseStandardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles,
            String teacherRequirements,
            UserInfo user
    ) throws IOException {
        TemplateBinary existingTemplate = loadTemplate(id, user);
        List<MaterialBinary> existingMaterials = listMaterialBinaries(id);
        MultipartFile effectiveTemplate = templateFile == null || templateFile.isEmpty()
                ? toMultipartFile(existingTemplate)
                : templateFile;
        MultipartFile effectiveCourseStandard = firstUsableCourseStandard(courseStandardFile, existingMaterials);
        List<MultipartFile> effectivePpts = firstUsablePpts(pptFiles, existingMaterials);
        List<MultipartFile> effectiveReferences = firstUsableReferences(referenceFiles, existingMaterials);
        validateTemplateFile(effectiveTemplate);
        if (effectiveCourseStandard == null || effectiveCourseStandard.isEmpty()) {
            throw new IllegalArgumentException("重新解析课程教案前必须保留课程标准文件。");
        }
        if (effectivePpts.isEmpty()) {
            throw new IllegalArgumentException("重新解析课程教案前必须保留至少一份 PPT/课件。");
        }
        return coursePlanAnalysisService.analyze(
                effectiveTemplate,
                effectiveCourseStandard,
                effectivePpts,
                effectiveReferences,
                teacherRequirements
        );
    }

    public CoursePlanDtos.Detail regenerate(
            Long id,
            MultipartFile templateFile,
            MultipartFile courseStandardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles,
            CoursePlanDtos.GenerateRequest request,
            UserInfo user
    ) throws IOException {
        return regenerate(id, templateFile, courseStandardFile, pptFiles, referenceFiles, request, user, CoursePlanGenerationProgress.NOOP);
    }

    public CoursePlanDtos.Detail regenerate(
            Long id,
            MultipartFile templateFile,
            MultipartFile courseStandardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles,
            CoursePlanDtos.GenerateRequest request,
            UserInfo user,
            CoursePlanGenerationProgress progress
    ) throws IOException {
        TemplateBinary existingTemplate = loadTemplate(id, user);
        List<MaterialBinary> existingMaterials = listMaterialBinaries(id);
        MultipartFile effectiveTemplate = templateFile == null || templateFile.isEmpty()
                ? toMultipartFile(existingTemplate)
                : templateFile;
        MultipartFile effectiveCourseStandard = firstUsableCourseStandard(courseStandardFile, existingMaterials);
        List<MultipartFile> effectivePpts = firstUsablePpts(pptFiles, existingMaterials);
        List<MultipartFile> effectiveReferences = firstUsableReferences(referenceFiles, existingMaterials);
        validateTemplateFile(effectiveTemplate);
        if (effectiveCourseStandard == null || effectiveCourseStandard.isEmpty()) {
            throw new IllegalArgumentException("重新生成课程教案前必须保留课程标准文件。");
        }
        if (effectivePpts.isEmpty()) {
            throw new IllegalArgumentException("重新生成课程教案前必须保留至少一份 PPT/课件。");
        }
        if (request == null || request.analysis() == null) {
            throw new IllegalArgumentException("缺少课程教案分析结果");
        }
        CoursePlanDtos.AnalysisResult normalizedAnalysis = withTeacherRequirements(request.analysis(), request.teacherRequirements());
        CoursePlanDtos.DocumentContent content = buildAiEnhancedContent(normalizedAnalysis, progress);
        String courseName = firstNonBlank(
                content.basicInfo() == null ? "" : content.basicInfo().courseName(),
                normalizedAnalysis.basicInfo() == null ? "" : normalizedAnalysis.basicInfo().courseName()
        );
        String title = firstNonBlank(content.title(), courseName + "课程教案");
        progress.update("saving", 1, 1, "保存课程教案记录");
        jdbcTemplate.update(
                "update course_plan set title = ?, course_name = ?, template_file_name = ?, template_file = ?, teacher_requirements = ?, analysis_json = ?, content_json = ?, status = 'draft' where id = ?",
                title,
                courseName,
                fileNameOf(effectiveTemplate),
                effectiveTemplate.getBytes(),
                firstNonBlank(content.teacherRequirements(), normalizedAnalysis.teacherRequirements()),
                writeJson(normalizedAnalysis),
                writeJson(content),
                id
        );
        saveMaterials(id, effectiveCourseStandard, effectivePpts, effectiveReferences);
        return getDetail(id, user);
    }

    public List<CoursePlanDtos.CoursePlanSummary> listLatest(UserInfo user) {
        String sql = "select id, title, course_name, status, updated_at from course_plan where status <> 'deleted' ";
        if (user.isAdmin()) {
            return jdbcTemplate.query(sql + "order by updated_at desc limit 100", this::mapSummary);
        }
        return jdbcTemplate.query(sql + "and user_id = ? order by updated_at desc limit 100", this::mapSummary, user.getId());
    }

    public CoursePlanDtos.Detail getDetail(Long id, UserInfo user) {
        CoursePlanRecord record = jdbcTemplate.queryForObject(
                "select id, user_id, title, course_name, teacher_requirements, analysis_json, content_json, status, created_at, updated_at " +
                        "from course_plan where id = ?",
                (rs, rowNum) -> new CoursePlanRecord(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("title"),
                        rs.getString("course_name"),
                        rs.getString("teacher_requirements"),
                        rs.getString("analysis_json"),
                        rs.getString("content_json"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("updated_at")
                ),
                id
        );
        if (record == null) {
            throw new IllegalArgumentException("课程教案不存在");
        }
        assertAccess(record.userId(), user);
        CoursePlanDtos.AnalysisResult analysis = readAnalysis(record.analysisJson());
        CoursePlanDtos.DocumentContent content = readContent(record.contentJson());
        return new CoursePlanDtos.Detail(
                record.id(),
                record.userId(),
                record.title(),
                record.courseName(),
                record.status(),
                record.teacherRequirements(),
                analysis,
                content,
                listMaterials(record.id()),
                record.createdAt() == null ? null : record.createdAt().toLocalDateTime(),
                record.updatedAt() == null ? null : record.updatedAt().toLocalDateTime()
        );
    }

    public Long createPartialDraft(
            UserInfo user,
            MultipartFile templateFile,
            MultipartFile courseStandardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles,
            CoursePlanDtos.AnalysisResult analysis,
            CoursePlanDtos.DocumentContent content
    ) throws IOException {
        validateTemplateFile(templateFile);
        if (analysis == null || content == null) {
            throw new IllegalArgumentException("缺少课程教案部分草稿内容，不能保存。");
        }
        Long id = insertCoursePlan(user, templateFile, analysis, content, "partial");
        saveMaterials(id, courseStandardFile, pptFiles, referenceFiles);
        return id;
    }

    public CoursePlanDtos.Detail reanalyze(Long id, String teacherRequirements, UserInfo user) throws IOException {
        return reanalyze(id, teacherRequirements, user, CoursePlanGenerationProgress.NOOP);
    }

    public CoursePlanDtos.Detail reanalyze(Long id, String teacherRequirements, UserInfo user, CoursePlanGenerationProgress progress) throws IOException {
        TemplateBinary templateBinary = loadTemplate(id, user);
        List<MaterialBinary> materials = listMaterialBinaries(id);
        MaterialBinary standard = materials.stream()
                .filter(item -> "course-standard".equals(item.role()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("该历史记录未保存原始课程标准文件，需重新上传。"));
        List<MultipartFile> ppts = materials.stream()
                .filter(item -> "ppt".equals(item.role()))
                .map(this::toMultipartFile)
                .toList();
        if (ppts.isEmpty()) {
            throw new IllegalStateException("该历史记录未保存原始 PPT/课件文件，需重新上传。");
        }
        List<MultipartFile> references = materials.stream()
                .filter(item -> "reference".equals(item.role()))
                .map(this::toMultipartFile)
                .toList();
        CoursePlanDtos.AnalysisResult analysis = coursePlanAnalysisService.analyze(
                toMultipartFile(templateBinary),
                toMultipartFile(standard),
                ppts,
                references,
                teacherRequirements
        );
        CoursePlanDtos.AnalysisResult normalizedAnalysis = withTeacherRequirements(analysis, teacherRequirements);
        CoursePlanDtos.DocumentContent content = buildAiEnhancedContent(normalizedAnalysis, progress);
        String courseName = firstNonBlank(
                content.basicInfo() == null ? "" : content.basicInfo().courseName(),
                normalizedAnalysis.basicInfo() == null ? "" : normalizedAnalysis.basicInfo().courseName()
        );
        String title = firstNonBlank(content.title(), courseName + "课程教案");
        progress.update("saving", 1, 1, "保存课程教案记录");
        jdbcTemplate.update(
                "update course_plan set title = ?, course_name = ?, teacher_requirements = ?, analysis_json = ?, content_json = ? where id = ?",
                title,
                courseName,
                firstNonBlank(teacherRequirements, normalizedAnalysis.teacherRequirements()),
                writeJson(normalizedAnalysis),
                writeJson(content),
                id
        );
        return getDetail(id, user);
    }

    public CoursePlanDtos.Detail save(Long id, CoursePlanDtos.SaveRequest request, UserInfo user) {
        CoursePlanRecord existing = jdbcTemplate.queryForObject(
                "select id, user_id, title, course_name, template_file_name, template_file, teacher_requirements, analysis_json, content_json, status, created_at, updated_at " +
                        "from course_plan where id = ?",
                (rs, rowNum) -> new CoursePlanRecord(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("title"),
                        rs.getString("course_name"),
                        rs.getString("teacher_requirements"),
                        rs.getString("analysis_json"),
                        rs.getString("content_json"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("updated_at")
                ),
                id
        );
        if (existing == null) {
            throw new IllegalArgumentException("课程教案不存在");
        }
        assertAccess(existing.userId(), user);

        CoursePlanDtos.AnalysisResult analysis = request != null && request.analysis() != null
                ? withTeacherRequirements(request.analysis(), request.teacherRequirements())
                : readAnalysis(existing.analysisJson());
        CoursePlanDtos.DocumentContent content = request != null && request.content() != null
                ? request.content()
                : readContent(existing.contentJson());
        String teacherRequirements = request == null ? existing.teacherRequirements() : safeText(request.teacherRequirements());
        String status = request == null || safeText(request.status()).isBlank() ? existing.status() : safeText(request.status());
        String courseName = firstNonBlank(
                content.basicInfo() == null ? "" : content.basicInfo().courseName(),
                analysis.basicInfo() == null ? "" : analysis.basicInfo().courseName(),
                existing.courseName()
        );
        String title = firstNonBlank(content.title(), courseName + "课程教案");

        jdbcTemplate.update(
                "update course_plan set title = ?, course_name = ?, teacher_requirements = ?, analysis_json = ?, content_json = ?, status = ? where id = ?",
                title,
                courseName,
                teacherRequirements,
                writeJson(analysis),
                writeJson(content),
                status,
                id
        );
        return getDetail(id, user);
    }

    public void delete(Long id, UserInfo user) {
        CoursePlanRecord existing = jdbcTemplate.queryForObject(
                "select id, user_id, title, course_name, template_file_name, template_file, teacher_requirements, analysis_json, content_json, status, created_at, updated_at " +
                        "from course_plan where id = ?",
                (rs, rowNum) -> new CoursePlanRecord(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("title"),
                        rs.getString("course_name"),
                        rs.getString("teacher_requirements"),
                        rs.getString("analysis_json"),
                        rs.getString("content_json"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"),
                        rs.getTimestamp("updated_at")
                ),
                id
        );
        if (existing == null) {
            throw new IllegalArgumentException("课程教案不存在");
        }
        assertAccess(existing.userId(), user);
        jdbcTemplate.update("update course_plan set status = 'deleted' where id = ?", id);
    }

    public TemplateBinary loadTemplate(Long id, UserInfo user) {
        TemplateBinary templateBinary = jdbcTemplate.queryForObject(
                "select user_id, template_file_name, template_file from course_plan where id = ?",
                (rs, rowNum) -> new TemplateBinary(
                        rs.getLong("user_id"),
                        rs.getString("template_file_name"),
                        rs.getBytes("template_file")
                ),
                id
        );
        if (templateBinary == null) {
            throw new IllegalArgumentException("课程教案不存在");
        }
        assertAccess(templateBinary.userId(), user);
        return templateBinary;
    }

    private Long insertCoursePlan(
            UserInfo user,
            MultipartFile templateFile,
            CoursePlanDtos.AnalysisResult analysis,
            CoursePlanDtos.DocumentContent content
    ) throws IOException {
        return insertCoursePlan(user, templateFile, analysis, content, "draft");
    }

    private Long insertCoursePlan(
            UserInfo user,
            MultipartFile templateFile,
            CoursePlanDtos.AnalysisResult analysis,
            CoursePlanDtos.DocumentContent content,
            String status
    ) throws IOException {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        byte[] templateBytes = templateFile.getBytes();
        String courseName = firstNonBlank(
                content.basicInfo() == null ? "" : content.basicInfo().courseName(),
                analysis.basicInfo() == null ? "" : analysis.basicInfo().courseName()
        );
        String title = firstNonBlank(content.title(), courseName + "课程教案");
        String teacherRequirements = firstNonBlank(content.teacherRequirements(), analysis.teacherRequirements());

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into course_plan (user_id, title, course_name, template_file_name, template_file, teacher_requirements, analysis_json, content_json, status) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, user.getId());
            ps.setString(2, title);
            ps.setString(3, courseName);
            ps.setString(4, fileNameOf(templateFile));
            ps.setBytes(5, templateBytes);
            ps.setString(6, teacherRequirements);
            ps.setString(7, writeJson(analysis));
            ps.setString(8, writeJson(content));
            ps.setString(9, firstNonBlank(status, "draft"));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("保存课程教案失败：未获取到记录 ID");
        }
        return key.longValue();
    }

    private void saveMaterials(
            Long coursePlanId,
            MultipartFile courseStandardFile,
            List<MultipartFile> pptFiles,
            List<MultipartFile> referenceFiles
    ) throws IOException {
        jdbcTemplate.update("delete from course_plan_material where course_plan_id = ?", coursePlanId);
        int sortOrder = 0;
        if (courseStandardFile != null && !courseStandardFile.isEmpty()) {
            insertMaterial(coursePlanId, "course-standard", courseStandardFile, sortOrder++);
        }
        if (pptFiles != null) {
            for (MultipartFile pptFile : pptFiles) {
                if (pptFile != null && !pptFile.isEmpty()) {
                    insertMaterial(coursePlanId, "ppt", pptFile, sortOrder++);
                }
            }
        }
        if (referenceFiles != null) {
            for (MultipartFile referenceFile : referenceFiles) {
                if (referenceFile != null && !referenceFile.isEmpty()) {
                    insertMaterial(coursePlanId, "reference", referenceFile, sortOrder++);
                }
            }
        }
    }

    private MultipartFile firstUsableCourseStandard(MultipartFile uploadedFile, List<MaterialBinary> existingMaterials) {
        if (uploadedFile != null && !uploadedFile.isEmpty()) {
            return uploadedFile;
        }
        return existingMaterials.stream()
                .filter(item -> "course-standard".equals(item.role()))
                .findFirst()
                .map(this::toMultipartFile)
                .orElse(null);
    }

    private List<MultipartFile> firstUsablePpts(List<MultipartFile> uploadedFiles, List<MaterialBinary> existingMaterials) {
        List<MultipartFile> provided = normalizeFiles(uploadedFiles);
        if (!provided.isEmpty()) {
            return provided;
        }
        return existingMaterials.stream()
                .filter(item -> "ppt".equals(item.role()))
                .map(this::toMultipartFile)
                .toList();
    }

    private List<MultipartFile> firstUsableReferences(List<MultipartFile> uploadedFiles, List<MaterialBinary> existingMaterials) {
        List<MultipartFile> provided = normalizeFiles(uploadedFiles);
        if (!provided.isEmpty()) {
            return provided;
        }
        return existingMaterials.stream()
                .filter(item -> "reference".equals(item.role()))
                .map(this::toMultipartFile)
                .toList();
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
    }

    private void insertMaterial(Long coursePlanId, String role, MultipartFile file, int sortOrder) throws IOException {
        jdbcTemplate.update(
                "insert into course_plan_material (course_plan_id, role, file_name, file_type, file_blob, sort_order) values (?, ?, ?, ?, ?, ?)",
                coursePlanId,
                role,
                fileNameOf(file),
                fileTypeOf(fileNameOf(file)),
                file.getBytes(),
                sortOrder
        );
    }

    private List<CoursePlanDtos.MaterialSummary> listMaterials(Long coursePlanId) {
        List<CoursePlanDtos.MaterialSummary> result = new ArrayList<>();
        result.addAll(jdbcTemplate.query(
                "select id, 'template' as role, template_file_name as file_name, 'docx' as file_type, length(template_file) as size, 0 as sort_order, created_at " +
                        "from course_plan where id = ?",
                (rs, rowNum) -> mapMaterialSummary(rs),
                coursePlanId
        ));
        result.addAll(jdbcTemplate.query(
                "select id, role, file_name, file_type, length(file_blob) as size, sort_order, created_at " +
                        "from course_plan_material where course_plan_id = ? order by sort_order, id",
                (rs, rowNum) -> mapMaterialSummary(rs),
                coursePlanId
        ));
        return result;
    }

    private List<MaterialBinary> listMaterialBinaries(Long coursePlanId) {
        return jdbcTemplate.query(
                "select id, role, file_name, file_type, file_blob, sort_order from course_plan_material where course_plan_id = ? order by sort_order, id",
                (rs, rowNum) -> new MaterialBinary(
                        rs.getLong("id"),
                        rs.getString("role"),
                        rs.getString("file_name"),
                        rs.getString("file_type"),
                        rs.getBytes("file_blob"),
                        rs.getInt("sort_order")
                ),
                coursePlanId
        );
    }

    private CoursePlanDtos.MaterialSummary mapMaterialSummary(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new CoursePlanDtos.MaterialSummary(
                rs.getLong("id"),
                rs.getString("role"),
                rs.getString("file_name"),
                rs.getString("file_type"),
                rs.getInt("sort_order"),
                rs.getLong("size"),
                createdAt == null ? null : createdAt.toLocalDateTime()
        );
    }

    private CoursePlanDtos.DocumentContent buildAiEnhancedContent(CoursePlanDtos.AnalysisResult analysis, CoursePlanGenerationProgress progress) {
        CoursePlanDtos.DocumentContent draft = coursePlanContentBuilderService.build(analysis, analysis.teacherRequirements());
        return coursePlanAiGenerationService.enhance(analysis, draft, progress);
    }

    private CoursePlanDtos.AnalysisResult withTeacherRequirements(CoursePlanDtos.AnalysisResult analysis, String teacherRequirements) {
        String normalizedTeacherRequirements = firstNonBlank(teacherRequirements, analysis.teacherRequirements());
        return new CoursePlanDtos.AnalysisResult(
                analysis.templateFileName(),
                analysis.basicInfo(),
                analysis.templateCheck(),
                analysis.units(),
                analysis.conflicts(),
                analysis.valid(),
                normalizedTeacherRequirements,
                analysis.splitStrategy(),
                analysis.sourceContext()
        );
    }

    private CoursePlanDtos.AnalysisResult readAnalysis(String json) {
        try {
            return objectMapper.readValue(json, CoursePlanDtos.AnalysisResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("课程教案分析结果解析失败", e);
        }
    }

    private CoursePlanDtos.DocumentContent readContent(String json) {
        try {
            return objectMapper.readValue(json, CoursePlanDtos.DocumentContent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("课程教案内容解析失败", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("课程教案 JSON 序列化失败", e);
        }
    }

    private void validateTemplateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传教案模板");
        }
        if (!fileNameOf(file).toLowerCase().endsWith(".docx")) {
            throw new IllegalArgumentException("教案模板仅支持 docx 文件");
        }
    }

    private void assertAccess(Long ownerId, UserInfo user) {
        if (user == null) {
            throw new IllegalStateException("用户未登录");
        }
        if (!user.isAdmin() && !user.getId().equals(ownerId)) {
            throw new IllegalStateException("无权访问该课程教案");
        }
    }

    private String fileNameOf(MultipartFile file) {
        return file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
    }

    private String fileTypeOf(String fileName) {
        String value = safeText(fileName);
        int index = value.lastIndexOf('.');
        if (index < 0 || index == value.length() - 1) {
            return "";
        }
        return value.substring(index + 1).toLowerCase();
    }

    private MultipartFile toMultipartFile(TemplateBinary value) {
        return new StoredMultipartFile(value.fileName(), fileTypeOf(value.fileName()), value.bytes());
    }

    private MultipartFile toMultipartFile(MaterialBinary value) {
        return new StoredMultipartFile(value.fileName(), value.fileType(), value.bytes());
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = safeText(value);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return "";
    }

    private CoursePlanDtos.CoursePlanSummary mapSummary(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        return new CoursePlanDtos.CoursePlanSummary(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("course_name"),
                rs.getString("status"),
                updatedAt == null ? null : updatedAt.toLocalDateTime(),
                "course-plan"
        );
    }

    public record TemplateBinary(
            Long userId,
            String fileName,
            byte[] bytes
    ) {
    }

    public record MaterialBinary(
            Long id,
            String role,
            String fileName,
            String fileType,
            byte[] bytes,
            Integer sortOrder
    ) {
    }

    private record CoursePlanRecord(
            Long id,
            Long userId,
            String title,
            String courseName,
            String teacherRequirements,
            String analysisJson,
            String contentJson,
            String status,
            Timestamp createdAt,
            Timestamp updatedAt
    ) {
    }

}
