package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.GenerationRecordSummary;
import cn.edu.nsu.maic.dto.UserInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GenerationRecordService {

    private final JdbcTemplate jdbcTemplate;

    public GenerationRecordService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<GenerationRecordSummary> listRecent(UserInfo user) {
        String sql = "select gr.id, gr.lesson_plan_id, gr.provider, gr.model_name, gr.action_type, gr.success, " +
                "gr.duration_ms, gr.error_message, gr.created_at " +
                "from generation_record gr " +
                "left join lesson_plan lp on lp.id = gr.lesson_plan_id ";
        if (user.isAdmin()) {
            return jdbcTemplate.query(sql + "order by gr.created_at desc limit 50", this::mapRecord);
        }
        return jdbcTemplate.query(sql + "where gr.user_id = ? order by gr.created_at desc limit 50", this::mapRecord, user.getId());
    }

    public Map<String, Object> detail(Long id, UserInfo user) {
        String sql = "select gr.id, gr.lesson_plan_id, gr.provider, gr.model_name, gr.action_type, gr.prompt, gr.response, " +
                "gr.success, gr.error_message, gr.duration_ms, gr.created_at, gr.user_id " +
                "from generation_record gr where gr.id = ?";
        Map<String, Object> data = jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("lessonPlanId", rs.getObject("lesson_plan_id"));
            item.put("provider", rs.getString("provider"));
            item.put("modelName", rs.getString("model_name"));
            item.put("actionType", rs.getString("action_type"));
            item.put("prompt", rs.getString("prompt"));
            item.put("response", rs.getString("response"));
            item.put("success", rs.getInt("success") == 1);
            item.put("errorMessage", rs.getString("error_message"));
            item.put("durationMs", rs.getObject("duration_ms"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            item.put("createdAt", createdAt == null ? null : createdAt.toLocalDateTime());
            item.put("userId", rs.getLong("user_id"));
            return item;
        }, id);
        if (!user.isAdmin() && !user.getId().equals(data.get("userId"))) {
            throw new IllegalStateException("无权访问该生成记录");
        }
        data.remove("userId");
        return data;
    }

    private GenerationRecordSummary mapRecord(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        GenerationRecordSummary item = new GenerationRecordSummary();
        item.setId(rs.getLong("id"));
        item.setLessonPlanId(rs.getLong("lesson_plan_id"));
        item.setProvider(rs.getString("provider"));
        item.setModelName(rs.getString("model_name"));
        item.setActionType(rs.getString("action_type"));
        item.setSuccess(rs.getInt("success") == 1);
        item.setDurationMs((Integer) rs.getObject("duration_ms"));
        item.setErrorMessage(rs.getString("error_message"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        item.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
        return item;
    }
}
