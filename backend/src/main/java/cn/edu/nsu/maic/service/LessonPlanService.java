package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.LessonPlanRequest;
import cn.edu.nsu.maic.dto.LessonPlanDetail;
import cn.edu.nsu.maic.dto.LessonPlanSaveRequest;
import cn.edu.nsu.maic.dto.LessonPlanSummary;
import cn.edu.nsu.maic.dto.UserInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

@Service
public class LessonPlanService {

    private final JdbcTemplate jdbcTemplate;
    private final AiLessonPlanGenerator aiLessonPlanGenerator;
    private final ObjectMapper objectMapper;
    private final LessonPlanContentNormalizer contentNormalizer;

    public LessonPlanService(
            JdbcTemplate jdbcTemplate,
            AiLessonPlanGenerator aiLessonPlanGenerator,
            ObjectMapper objectMapper,
            LessonPlanContentNormalizer contentNormalizer
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiLessonPlanGenerator = aiLessonPlanGenerator;
        this.objectMapper = objectMapper;
        this.contentNormalizer = contentNormalizer;
    }

    public List<LessonPlanSummary> listLatest(UserInfo user) {
        String sql = "select id, title, course_name, topic, lesson_type, total_minutes, status, updated_at " +
                "from lesson_plan where status <> 'deleted' ";
        if (user.isAdmin()) {
            return jdbcTemplate.query(sql + "order by updated_at desc limit 100", this::mapSummary);
        }
        return jdbcTemplate.query(sql + "and user_id = ? order by updated_at desc limit 100", this::mapSummary, user.getId());
    }

    public Long createDraft(LessonPlanRequest request, UserInfo user) {
        String normalizedContentJson = contentNormalizer.normalizeForRequest(buildDraftContentJson(request), request);
        return createLessonPlan(request, normalizedContentJson, user.getId());
    }

    public LessonPlanDetail generate(LessonPlanRequest request, UserInfo user) {
        LessonPlanGeneration generation;
        try {
            generation = aiLessonPlanGenerator.generate(request);
        } catch (RuntimeException e) {
            jdbcTemplate.update(
                    "insert into generation_record (user_id, lesson_plan_id, provider, model_name, action_type, prompt, response, success, error_message) " +
                            "values (?, null, ?, ?, ?, ?, null, 0, ?)",
                    user.getId(),
                    "deepseek",
                    "deepseek-v4-flash",
                    "generate",
                    buildFailedPromptSummary(request),
                    e.getMessage()
            );
            throw e;
        }
        String normalizedContentJson = contentNormalizer.normalizeForRequest(generation.getContentJson(), request);
        Long id = createLessonPlan(request, normalizedContentJson, user.getId());
        jdbcTemplate.update(
                "insert into generation_record (user_id, lesson_plan_id, provider, model_name, action_type, prompt, response, success, duration_ms) " +
                        "values (?, ?, ?, ?, ?, ?, ?, 1, ?)",
                user.getId(),
                id,
                generation.getProvider(),
                generation.getModelName(),
                "generate",
                generation.getPrompt(),
                normalizedContentJson,
                generation.getDurationMs()
        );
        return getDetail(id, user);
    }

    private String buildFailedPromptSummary(LessonPlanRequest request) {
        return "课程：" + request.getCourseName()
                + "；主题：" + request.getTopic()
                + "；专业：" + request.getMajor()
                + "；年级：" + request.getGrade()
                + "；课程类型：" + request.getLessonType()
                + "；教学模式：" + request.getTeachingMode()
                + "；先修基础：" + request.getPrerequisiteKnowledge()
                + "；常见误区：" + request.getCommonMisconceptions()
                + "；班级情况：" + request.getClassLevelProfile()
                + "；本节重点：" + request.getLessonFocus()
                + "；预期产出：" + request.getExpectedOutputs();
    }

    public LessonPlanDetail getDetail(Long id, UserInfo user) {
        LessonPlanDetail detail = jdbcTemplate.queryForObject(
                "select id, title, course_name, major, grade, target_students, topic, lesson_type, teaching_mode, " +
                        "user_id, period_count, minutes_per_period, total_minutes, student_analysis, textbook, experiment_env, " +
                        "include_ideology, include_obe, extra_requirements, content_json, status, created_at, updated_at " +
                        "from lesson_plan where id = ? and status <> 'deleted'",
                (rs, rowNum) -> {
                    LessonPlanDetail item = new LessonPlanDetail();
                    item.setId(rs.getLong("id"));
                    item.setTitle(rs.getString("title"));
                    item.setCourseName(rs.getString("course_name"));
                    item.setMajor(rs.getString("major"));
                    item.setGrade(rs.getString("grade"));
                    item.setTargetStudents(rs.getString("target_students"));
                    item.setTopic(rs.getString("topic"));
                    item.setLessonType(rs.getString("lesson_type"));
                    item.setTeachingMode(rs.getString("teaching_mode"));
                    item.setUserId(rs.getLong("user_id"));
                    item.setPeriodCount(rs.getInt("period_count"));
                    item.setMinutesPerPeriod(rs.getInt("minutes_per_period"));
                    item.setTotalMinutes(rs.getInt("total_minutes"));
                    item.setStudentAnalysis(rs.getString("student_analysis"));
                    item.setTextbook(rs.getString("textbook"));
                    item.setExperimentEnv(rs.getString("experiment_env"));
                    item.setIncludeIdeology(rs.getInt("include_ideology") == 1);
                    item.setIncludeObe(rs.getInt("include_obe") == 1);
                    item.setExtraRequirements(rs.getString("extra_requirements"));
                    item.setContentJson(rs.getString("content_json"));
                    item.setStatus(rs.getString("status"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    item.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                    item.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
                    return item;
                },
                id
        );
        assertAccess(detail, user);
        detail.setContentJson(contentNormalizer.normalizeForDetail(detail.getContentJson(), detail));
        return detail;
    }

    public LessonPlanDetail save(Long id, LessonPlanSaveRequest request, UserInfo user) {
        LessonPlanDetail old = getDetail(id, user);
        String normalizedContentJson = contentNormalizer.normalizeForSave(request.getContentJson(), old, request);
        int periodCount = request.getPeriodCount() == null || request.getPeriodCount() <= 0
                ? (old.getPeriodCount() == null || old.getPeriodCount() <= 0 ? 2 : old.getPeriodCount())
                : request.getPeriodCount();
        int minutesPerPeriod = request.getMinutesPerPeriod() == null || request.getMinutesPerPeriod() <= 0
                ? (old.getMinutesPerPeriod() == null || old.getMinutesPerPeriod() <= 0 ? 40 : old.getMinutesPerPeriod())
                : request.getMinutesPerPeriod();
        int totalMinutes = periodCount * minutesPerPeriod;
        jdbcTemplate.update(
                "insert into lesson_plan_version (lesson_plan_id, content_json, version_note, created_by) values (?, ?, ?, ?)",
                id,
                old.getContentJson() == null || old.getContentJson().isBlank() ? "{}" : old.getContentJson(),
                "保存前自动备份",
                user.getId()
        );
        jdbcTemplate.update(
                "update lesson_plan set title = ?, course_name = ?, major = ?, grade = ?, target_students = ?, topic = ?, " +
                        "lesson_type = ?, teaching_mode = ?, period_count = ?, minutes_per_period = ?, total_minutes = ?, " +
                        "content_json = ?, status = ? where id = ?",
                request.getTitle(),
                chooseText(request.getCourseName(), old.getCourseName()),
                chooseText(request.getMajor(), old.getMajor()),
                chooseText(request.getGrade(), old.getGrade()),
                chooseText(request.getTargetStudents(), old.getTargetStudents()),
                chooseText(request.getTopic(), old.getTopic()),
                chooseText(request.getLessonType(), old.getLessonType()),
                chooseText(request.getTeachingMode(), old.getTeachingMode()),
                periodCount,
                minutesPerPeriod,
                totalMinutes,
                normalizedContentJson,
                request.getStatus() == null || request.getStatus().isBlank() ? "draft" : request.getStatus(),
                id
        );
        return getDetail(id, user);
    }

    public void delete(Long id, UserInfo user) {
        getDetail(id, user);
        jdbcTemplate.update("update lesson_plan set status = 'deleted' where id = ?", id);
    }

    public LessonPlanDetail copy(Long id, UserInfo user) {
        LessonPlanDetail source = getDetail(id, user);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into lesson_plan (" +
                            "user_id, title, course_name, major, grade, target_students, topic, lesson_type, teaching_mode, " +
                            "period_count, minutes_per_period, total_minutes, student_analysis, textbook, experiment_env, include_ideology, " +
                            "include_obe, extra_requirements, content_json, status" +
                            ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'draft')",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, user.getId());
            ps.setString(2, source.getTitle() + " - 副本");
            ps.setString(3, source.getCourseName());
            ps.setString(4, source.getMajor());
            ps.setString(5, source.getGrade());
            ps.setString(6, source.getTargetStudents());
            ps.setString(7, source.getTopic());
            ps.setString(8, source.getLessonType());
            ps.setString(9, source.getTeachingMode());
            ps.setInt(10, source.getPeriodCount() == null || source.getPeriodCount() <= 0 ? 2 : source.getPeriodCount());
            ps.setInt(11, source.getMinutesPerPeriod() == null || source.getMinutesPerPeriod() <= 0 ? 40 : source.getMinutesPerPeriod());
            ps.setInt(12, source.getTotalMinutes() == null || source.getTotalMinutes() <= 0
                    ? (source.getPeriodCount() == null ? 2 : source.getPeriodCount()) * (source.getMinutesPerPeriod() == null ? 40 : source.getMinutesPerPeriod())
                    : source.getTotalMinutes());
            ps.setString(13, source.getStudentAnalysis());
            ps.setString(14, source.getTextbook());
            ps.setString(15, source.getExperimentEnv());
            ps.setInt(16, Boolean.TRUE.equals(source.getIncludeIdeology()) ? 1 : 0);
            ps.setInt(17, Boolean.TRUE.equals(source.getIncludeObe()) ? 1 : 0);
            ps.setString(18, source.getExtraRequirements());
            ps.setString(19, source.getContentJson());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("复制教案失败：未获取到新教案ID");
        }
        return getDetail(key.longValue(), user);
    }

    private Long createLessonPlan(LessonPlanRequest request, String contentJson, Long userId) {
        int periodCount = request.getPeriodCount() == null ? 2 : request.getPeriodCount();
        int minutesPerPeriod = request.getMinutesPerPeriod() == null ? 40 : request.getMinutesPerPeriod();
        int totalMinutes = periodCount * minutesPerPeriod;
        String title = request.getCourseName() + " - " + request.getTopic();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into lesson_plan (" +
                            "user_id, title, course_name, major, grade, target_students, topic, lesson_type, " +
                            "teaching_mode, period_count, minutes_per_period, total_minutes, student_analysis, " +
                            "textbook, experiment_env, include_ideology, include_obe, extra_requirements, content_json, status" +
                            ") values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'draft')",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, userId);
            ps.setString(2, title);
            ps.setString(3, request.getCourseName());
            ps.setString(4, request.getMajor());
            ps.setString(5, request.getGrade());
            ps.setString(6, request.getTargetStudents());
            ps.setString(7, request.getTopic());
            ps.setString(8, request.getLessonType());
            ps.setString(9, request.getTeachingMode());
            ps.setInt(10, periodCount);
            ps.setInt(11, minutesPerPeriod);
            ps.setInt(12, totalMinutes);
            ps.setString(13, request.getStudentAnalysis());
            ps.setString(14, request.getTextbook());
            ps.setString(15, limitText(request.getExperimentEnv(), 240));
            ps.setInt(16, Boolean.FALSE.equals(request.getIncludeIdeology()) ? 0 : 1);
            ps.setInt(17, Boolean.FALSE.equals(request.getIncludeObe()) ? 0 : 1);
            ps.setString(18, request.getExtraRequirements());
            ps.setString(19, contentJson);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建教案失败：未获取到新教案ID");
        }
        return key.longValue();
    }

    private String buildDraftContentJson(LessonPlanRequest request) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode basicInfo = objectMapper.createObjectNode();
        basicInfo.put("courseName", chooseText(request.getCourseName(), ""));
        basicInfo.put("topic", chooseText(request.getTopic(), ""));
        basicInfo.put("major", chooseText(request.getMajor(), ""));
        basicInfo.put("grade", chooseText(request.getGrade(), ""));
        basicInfo.put("targetStudents", chooseText(request.getTargetStudents(), ""));
        basicInfo.put("lessonType", chooseText(request.getLessonType(), ""));
        basicInfo.put("teachingMode", chooseText(request.getTeachingMode(), ""));
        root.set("basicInfo", basicInfo);

        ObjectNode generationContext = objectMapper.createObjectNode();
        generationContext.put("prerequisiteKnowledge", chooseText(request.getPrerequisiteKnowledge(), ""));
        generationContext.put("commonMisconceptions", chooseText(request.getCommonMisconceptions(), ""));
        generationContext.put("classLevelProfile", chooseText(request.getClassLevelProfile(), ""));
        generationContext.put("lessonFocus", chooseText(request.getLessonFocus(), ""));
        generationContext.put("expectedOutputs", chooseText(request.getExpectedOutputs(), ""));
        root.set("generationContext", generationContext);
        ArrayNode referenceMaterials = objectMapper.valueToTree(request.getReferenceMaterials() == null ? List.of() : request.getReferenceMaterials());
        root.set("referenceMaterials", referenceMaterials);
        root.set("teachingCalendar", objectMapper.valueToTree(request.getTeachingCalendar()));
        root.put("studentAnalysis", chooseText(request.getStudentAnalysis(), ""));
        root.put("reflection", "");
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("创建草稿失败：上下文序列化异常", e);
        }
    }

    private LessonPlanSummary mapSummary(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        LessonPlanSummary item = new LessonPlanSummary();
        item.setId(rs.getLong("id"));
        item.setTitle(rs.getString("title"));
        item.setCourseName(rs.getString("course_name"));
        item.setTopic(rs.getString("topic"));
        item.setLessonType(rs.getString("lesson_type"));
        item.setTotalMinutes(rs.getInt("total_minutes"));
        item.setStatus(rs.getString("status"));
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        item.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
        return item;
    }

    private void assertAccess(LessonPlanDetail detail, UserInfo user) {
        if (detail == null) {
            throw new IllegalArgumentException("教案不存在");
        }
        if (!user.isAdmin() && !user.getId().equals(detail.getUserId())) {
            throw new IllegalStateException("无权访问该教案");
        }
    }

    private String limitText(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String chooseText(String nextValue, String oldValue) {
        if (nextValue == null || nextValue.isBlank()) {
            return oldValue;
        }
        return nextValue.trim();
    }
}
