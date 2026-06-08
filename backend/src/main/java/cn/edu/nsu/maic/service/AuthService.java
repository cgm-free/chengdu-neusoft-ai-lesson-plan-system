package cn.edu.nsu.maic.service;

import cn.edu.nsu.maic.dto.LoginResponse;
import cn.edu.nsu.maic.dto.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Map<String, UserInfo> sessions = new ConcurrentHashMap<>();

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LoginResponse login(String username, String password, String role) {
        UserRecord record = findUserRecord(username);
        if (record == null || !passwordEncoder.matches(password, record.passwordHash)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!normalizeRole(role).equalsIgnoreCase(record.role)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        UserInfo user = new UserInfo(record.id, record.username, record.realName, record.role, record.department);
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, user);
        jdbcTemplate.update("update sys_user set last_login_at = ? where id = ?", new Timestamp(System.currentTimeMillis()), record.id);
        return new LoginResponse(token, user);
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public UserInfo currentUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return sessions.get(token);
    }

    public UserInfo requireUser(HttpServletRequest request) {
        UserInfo user = currentUser(extractToken(request));
        if (user == null) {
            throw new IllegalStateException("请先登录");
        }
        return user;
    }

    public UserInfo requireAdmin(HttpServletRequest request) {
        UserInfo user = requireUser(request);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问管理员功能");
        }
        return user;
    }

    public String extractToken(HttpServletRequest request) {
        String token = request.getHeader("X-Auth-Token");
        if (token == null || token.isBlank()) {
            token = request.getParameter("token");
        }
        return token;
    }

    public BCryptPasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }

    private String normalizeRole(String role) {
        String value = role == null ? "" : role.trim().toLowerCase();
        if (!"admin".equals(value) && !"teacher".equals(value)) {
            throw new IllegalArgumentException("登录身份无效");
        }
        return value;
    }

    private UserRecord findUserRecord(String username) {
        try {
            return jdbcTemplate.queryForObject(
                    "select id, username, password_hash, real_name, role, department from sys_user where username = ? and enabled = 1",
                    (rs, rowNum) -> new UserRecord(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("real_name"),
                            rs.getString("role"),
                            rs.getString("department")
                    ),
                    username
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private static class UserRecord {
        private final Long id;
        private final String username;
        private final String passwordHash;
        private final String realName;
        private final String role;
        private final String department;

        private UserRecord(Long id, String username, String passwordHash, String realName, String role, String department) {
            this.id = id;
            this.username = username;
            this.passwordHash = passwordHash;
            this.realName = realName;
            this.role = role;
            this.department = department;
        }
    }
}
