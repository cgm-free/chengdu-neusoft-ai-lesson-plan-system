package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.AccountRequestDtos;
import cn.edu.nsu.maic.dto.AdminUserDtos;
import cn.edu.nsu.maic.dto.UserInfo;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Service
public class AccountRequestService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";

    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final AdminUserService adminUserService;

    public AccountRequestService(JdbcTemplate jdbcTemplate, AuthService authService, AdminUserService adminUserService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.adminUserService = adminUserService;
    }

    @Transactional
    public AccountRequestDtos.Summary submit(AccountRequestDtos.CreateRequest request) {
        String username = cleanRequired(request.getUsername(), "用户名不能为空");
        ensureUsernameAvailable(username);
        String passwordHash = authService.passwordEncoder().encode(request.getPassword());
        jdbcTemplate.update(
                "insert into teacher_account_request " +
                        "(username, password_hash, real_name, employee_no, phone, college, department, major, course_name, status) " +
                        "values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending')",
                username,
                passwordHash,
                cleanRequired(request.getRealName(), "教师姓名不能为空"),
                cleanOptional(request.getEmployeeNo()),
                cleanOptional(request.getPhone()),
                cleanRequired(request.getCollege(), "学院不能为空"),
                cleanRequired(request.getDepartment(), "系部不能为空"),
                cleanRequired(request.getMajor(), "专业不能为空"),
                cleanOptional(request.getCourseName())
        );
        return getLatestByUsername(username);
    }

    public List<AccountRequestDtos.Summary> list(String status) {
        String normalizedStatus = cleanOptional(status).toLowerCase();
        String sql = "select id, username, real_name, employee_no, phone, college, department, major, course_name, " +
                "status, review_note, reviewed_by, reviewed_at, created_at, updated_at from teacher_account_request ";
        if (normalizedStatus.isBlank()) {
            return jdbcTemplate.query(sql + "order by created_at desc, id desc", this::mapSummary);
        }
        return jdbcTemplate.query(sql + "where status = ? order by created_at desc, id desc", this::mapSummary, normalizedStatus);
    }

    @Transactional
    public AdminUserDtos.Summary approve(Long id, AccountRequestDtos.ReviewRequest request, UserInfo operator) {
        RequestRecord record = requireRecord(id);
        if (!STATUS_PENDING.equals(record.status())) {
            throw new IllegalArgumentException("该申请已审核，不能重复处理");
        }
        ensureUsernameAvailable(record.username());
        jdbcTemplate.update(
                "insert into sys_user (username, password_hash, real_name, role, department, enabled) values (?, ?, ?, 'teacher', ?, 1)",
                record.username(),
                record.passwordHash(),
                record.realName(),
                organizationLabel(record)
        );
        updateReviewStatus(id, STATUS_APPROVED, request, operator);
        return adminUserService.getByUsername(record.username());
    }

    @Transactional
    public AccountRequestDtos.Summary reject(Long id, AccountRequestDtos.ReviewRequest request, UserInfo operator) {
        RequestRecord record = requireRecord(id);
        if (!STATUS_PENDING.equals(record.status())) {
            throw new IllegalArgumentException("该申请已审核，不能重复处理");
        }
        updateReviewStatus(id, STATUS_REJECTED, request, operator);
        return getById(id);
    }

    private void updateReviewStatus(Long id, String status, AccountRequestDtos.ReviewRequest request, UserInfo operator) {
        jdbcTemplate.update(
                "update teacher_account_request set status = ?, review_note = ?, reviewed_by = ?, reviewed_at = ? where id = ?",
                status,
                request == null ? "" : cleanOptional(request.getReviewNote()),
                operator.getId(),
                new Timestamp(System.currentTimeMillis()),
                id
        );
    }

    private void ensureUsernameAvailable(String username) {
        Integer userCount = jdbcTemplate.queryForObject("select count(*) from sys_user where username = ?", Integer.class, username);
        if (userCount != null && userCount > 0) {
            throw new IllegalArgumentException("用户名已存在，请更换用户名");
        }
        Integer pendingCount = jdbcTemplate.queryForObject(
                "select count(*) from teacher_account_request where username = ? and status = 'pending'",
                Integer.class,
                username
        );
        if (pendingCount != null && pendingCount > 0) {
            throw new IllegalArgumentException("该用户名已有待审核申请，请等待管理员处理");
        }
    }

    private AccountRequestDtos.Summary getLatestByUsername(String username) {
        return jdbcTemplate.queryForObject(
                "select id, username, real_name, employee_no, phone, college, department, major, course_name, " +
                        "status, review_note, reviewed_by, reviewed_at, created_at, updated_at " +
                        "from teacher_account_request where username = ? order by id desc limit 1",
                this::mapSummary,
                username
        );
    }

    private AccountRequestDtos.Summary getById(Long id) {
        return jdbcTemplate.queryForObject(
                "select id, username, real_name, employee_no, phone, college, department, major, course_name, " +
                        "status, review_note, reviewed_by, reviewed_at, created_at, updated_at " +
                        "from teacher_account_request where id = ?",
                this::mapSummary,
                id
        );
    }

    private RequestRecord requireRecord(Long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "select id, username, password_hash, real_name, employee_no, phone, college, department, major, course_name, status " +
                            "from teacher_account_request where id = ?",
                    (rs, rowNum) -> new RequestRecord(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("real_name"),
                            rs.getString("employee_no"),
                            rs.getString("phone"),
                            rs.getString("college"),
                            rs.getString("department"),
                            rs.getString("major"),
                            rs.getString("course_name"),
                            rs.getString("status")
                    ),
                    id
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("账号申请不存在");
        }
    }

    private AccountRequestDtos.Summary mapSummary(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        AccountRequestDtos.Summary item = new AccountRequestDtos.Summary();
        item.setId(rs.getLong("id"));
        item.setUsername(rs.getString("username"));
        item.setRealName(rs.getString("real_name"));
        item.setEmployeeNo(rs.getString("employee_no"));
        item.setPhone(rs.getString("phone"));
        item.setCollege(rs.getString("college"));
        item.setDepartment(rs.getString("department"));
        item.setMajor(rs.getString("major"));
        item.setCourseName(rs.getString("course_name"));
        item.setStatus(rs.getString("status"));
        item.setReviewNote(rs.getString("review_note"));
        Number reviewedBy = (Number) rs.getObject("reviewed_by");
        item.setReviewedBy(reviewedBy == null ? null : reviewedBy.longValue());
        item.setReviewedAt(toLocalDateTime(rs.getTimestamp("reviewed_at")));
        item.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        item.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return item;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String organizationLabel(RequestRecord record) {
        return record.college() + " / " + record.department() + " / " + record.major();
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

    private record RequestRecord(
            Long id,
            String username,
            String passwordHash,
            String realName,
            String employeeNo,
            String phone,
            String college,
            String department,
            String major,
            String courseName,
            String status
    ) {
    }
}
