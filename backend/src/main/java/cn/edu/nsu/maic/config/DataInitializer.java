package cn.edu.nsu.maic.config;

import cn.edu.nsu.maic.service.AuthService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;

    public DataInitializer(JdbcTemplate jdbcTemplate, AuthService authService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureUser("admin", "admin123456", "系统管理员", "admin", "成都东软学院");
        ensureUser("teacher01", "teacher123456", "测试教师", "teacher", "计算机与软件学院");
    }

    private void ensureUser(String username, String rawPassword, String realName, String role, String department) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from sys_user where username = ?", Integer.class, username);
        String hash = authService.passwordEncoder().encode(rawPassword);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                    "insert into sys_user (username, password_hash, real_name, role, department, enabled) values (?, ?, ?, ?, ?, 1)",
                    username, hash, realName, role, department
            );
            return;
        }
        jdbcTemplate.update(
                "update sys_user set password_hash = ?, real_name = ?, role = ?, department = ?, enabled = 1 " +
                        "where username = ? and (password_hash like '%replace_with_real_bcrypt_hash%' or password_hash = '')",
                hash, realName, role, department, username
        );
    }
}

