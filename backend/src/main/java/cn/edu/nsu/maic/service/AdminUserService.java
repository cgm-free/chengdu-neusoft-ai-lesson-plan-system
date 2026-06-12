package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.AdminUserDtos;
import cn.edu.nsu.maic.dto.UserInfo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class AdminUserService {

    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;

    public AdminUserService(JdbcTemplate jdbcTemplate, AuthService authService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
    }

    public List<AdminUserDtos.Summary> listUsers() {
        return jdbcTemplate.query(
                "select id, username, real_name, role, department, enabled, last_login_at, created_at, updated_at " +
                        "from sys_user order by enabled desc, updated_at desc, id desc",
                this::mapSummary
        );
    }

    public AdminUserDtos.Summary createUser(AdminUserDtos.CreateRequest request) {
        String username = cleanRequired(request.getUsername(), "用户名不能为空");
        String realName = cleanRequired(request.getRealName(), "姓名不能为空");
        String role = normalizeRole(request.getRole());
        String department = cleanOptional(request.getDepartment());
        String passwordHash = authService.passwordEncoder().encode(request.getPassword());
        try {
            jdbcTemplate.update(
                    "insert into sys_user (username, password_hash, real_name, role, department, enabled) values (?, ?, ?, ?, ?, 1)",
                    username, passwordHash, realName, role, department
            );
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("用户名已存在");
        }
        return getByUsername(username);
    }

    public AdminUserDtos.Summary updateUser(Long id, AdminUserDtos.UpdateRequest request, UserInfo operator) {
        UserRecord current = requireRecord(id);
        String realName = cleanRequired(request.getRealName(), "姓名不能为空");
        String role = normalizeRole(request.getRole());
        String department = cleanOptional(request.getDepartment());
        boolean enabled = Boolean.TRUE.equals(request.getEnabled());
        assertSelfEditable(current, operator, role, enabled);
        jdbcTemplate.update(
                "update sys_user set real_name = ?, role = ?, department = ?, enabled = ? where id = ?",
                realName, role, department, enabled ? 1 : 0, id
        );
        return getById(id);
    }

    public AdminUserDtos.Summary resetPassword(Long id, AdminUserDtos.ResetPasswordRequest request) {
        requireRecord(id);
        String passwordHash = authService.passwordEncoder().encode(request.getPassword());
        jdbcTemplate.update("update sys_user set password_hash = ? where id = ?", passwordHash, id);
        return getById(id);
    }

    public AdminUserDtos.Summary updateEnabled(Long id, boolean enabled, UserInfo operator) {
        UserRecord current = requireRecord(id);
        if (operator.getId().equals(current.id()) && !enabled) {
            throw new IllegalArgumentException("不能禁用当前登录管理员");
        }
        jdbcTemplate.update("update sys_user set enabled = ? where id = ?", enabled ? 1 : 0, id);
        return getById(id);
    }

    public void disableUser(Long id, UserInfo operator) {
        UserRecord current = requireRecord(id);
        if (operator.getId().equals(current.id())) {
            throw new IllegalArgumentException("不能禁用当前登录管理员");
        }
        jdbcTemplate.update("update sys_user set enabled = 0 where id = ?", id);
    }

    public void deleteUserPermanently(Long id, UserInfo operator) {
        UserRecord current = requireRecord(id);
        if (operator.getId().equals(current.id())) {
            throw new IllegalArgumentException("不能删除当前登录管理员");
        }
        if (current.enabled()) {
            throw new IllegalArgumentException("请先禁用该用户，再执行删除");
        }
        assertNoRelatedBusinessData(id);
        jdbcTemplate.update("delete from sys_user where id = ?", id);
    }

    public AdminUserDtos.Summary getByUsername(String username) {
        return jdbcTemplate.queryForObject(
                "select id, username, real_name, role, department, enabled, last_login_at, created_at, updated_at from sys_user where username = ?",
                this::mapSummary,
                username
        );
    }

    private AdminUserDtos.Summary getById(Long id) {
        return jdbcTemplate.queryForObject(
                "select id, username, real_name, role, department, enabled, last_login_at, created_at, updated_at from sys_user where id = ?",
                this::mapSummary,
                id
        );
    }

    private UserRecord requireRecord(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "select id, username, role, enabled from sys_user where id = ?",
                    (rs, rowNum) -> new UserRecord(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("role"),
                            rs.getInt("enabled") == 1
                    ),
                    id
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    private void assertSelfEditable(UserRecord current, UserInfo operator, String nextRole, boolean nextEnabled) {
        if (!operator.getId().equals(current.id())) {
            return;
        }
        if (!nextEnabled) {
            throw new IllegalArgumentException("不能禁用当前登录管理员");
        }
        if (!"admin".equals(nextRole)) {
            throw new IllegalArgumentException("不能将当前登录管理员改为教师");
        }
    }

    private void assertNoRelatedBusinessData(Long userId) {
        ensureNoRelatedRows("lesson_plan", "user_id", userId, "该用户已有教案数据，不能删除，只能保持禁用");
        ensureNoRelatedRows("generation_record", "user_id", userId, "该用户已有生成记录，不能删除，只能保持禁用");
        ensureNoRelatedRows("lesson_plan_version", "created_by", userId, "该用户已有教案版本记录，不能删除，只能保持禁用");
        ensureNoRelatedRows("course_plan", "user_id", userId, "该用户已有课程教案数据，不能删除，只能保持禁用");
        ensureNoRelatedRows("course_plan_generation_job", "user_id", userId, "该用户已有课程教案生成任务，不能删除，只能保持禁用");
    }

    private void ensureNoRelatedRows(String tableName, String columnName, Long userId, String message) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where " + columnName + " = ?",
                Integer.class,
                userId
        );
        if (count != null && count > 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private AdminUserDtos.Summary mapSummary(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        AdminUserDtos.Summary item = new AdminUserDtos.Summary();
        item.setId(rs.getLong("id"));
        item.setUsername(rs.getString("username"));
        item.setRealName(rs.getString("real_name"));
        item.setRole(rs.getString("role"));
        item.setDepartment(rs.getString("department"));
        item.setEnabled(rs.getInt("enabled") == 1);
        item.setLastLoginAt(toLocalDateTime(rs.getTimestamp("last_login_at")));
        item.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        item.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return item;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String cleanRequired(String value, String message) {
        String text = cleanOptional(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private String cleanOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeRole(String role) {
        String value = cleanOptional(role).toLowerCase();
        if (!"admin".equals(value) && !"teacher".equals(value)) {
            throw new IllegalArgumentException("角色无效");
        }
        return value;
    }

    private record UserRecord(Long id, String username, String role, boolean enabled) {
    }
}
