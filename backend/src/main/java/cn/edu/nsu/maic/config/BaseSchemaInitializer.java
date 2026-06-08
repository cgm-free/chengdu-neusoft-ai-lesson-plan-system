package cn.edu.nsu.maic.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class BaseSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public BaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                create table if not exists sys_user (
                    id bigint primary key auto_increment,
                    username varchar(64) not null,
                    password_hash varchar(255) not null,
                    real_name varchar(64) not null,
                    role varchar(32) not null,
                    department varchar(128) null,
                    enabled tinyint not null default 1,
                    last_login_at datetime null,
                    created_at datetime not null default current_timestamp,
                    updated_at datetime not null default current_timestamp on update current_timestamp,
                    unique key uk_sys_user_username (username)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists teacher_account_request (
                    id bigint primary key auto_increment,
                    username varchar(64) not null,
                    real_name varchar(64) not null,
                    college varchar(128) not null,
                    department varchar(128) not null,
                    major varchar(128) not null,
                    course_name varchar(255) null,
                    status varchar(32) not null default 'pending',
                    review_note varchar(255) null,
                    reviewed_by bigint null,
                    reviewed_at datetime null,
                    created_at datetime not null default current_timestamp,
                    updated_at datetime not null default current_timestamp on update current_timestamp,
                    index idx_teacher_account_request_status_created (status, created_at),
                    index idx_teacher_account_request_username_status (username, status)
                )
                """);
        dropColumnIfExists("teacher_account_request", "password_hash");
        dropColumnIfExists("teacher_account_request", "employee_no");
        dropColumnIfExists("teacher_account_request", "phone");
        jdbcTemplate.execute("""
                create table if not exists lesson_plan (
                    id bigint primary key auto_increment,
                    user_id bigint not null,
                    title varchar(255) not null,
                    course_name varchar(255) not null,
                    major varchar(255) null,
                    grade varchar(255) null,
                    target_students varchar(255) null,
                    topic varchar(255) not null,
                    lesson_type varchar(64) null,
                    teaching_mode varchar(128) null,
                    period_count int not null default 2,
                    minutes_per_period int not null default 40,
                    total_minutes int not null default 80,
                    student_analysis text null,
                    textbook text null,
                    experiment_env text null,
                    include_ideology tinyint not null default 1,
                    include_obe tinyint not null default 1,
                    extra_requirements text null,
                    content_json longtext not null,
                    status varchar(32) not null default 'draft',
                    created_at datetime not null default current_timestamp,
                    updated_at datetime not null default current_timestamp on update current_timestamp,
                    index idx_lesson_plan_user_updated (user_id, updated_at),
                    index idx_lesson_plan_status (status)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists lesson_plan_version (
                    id bigint primary key auto_increment,
                    lesson_plan_id bigint not null,
                    content_json longtext not null,
                    version_note varchar(255) null,
                    created_by bigint null,
                    created_at datetime not null default current_timestamp,
                    index idx_lesson_plan_version_plan (lesson_plan_id, created_at)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists generation_record (
                    id bigint primary key auto_increment,
                    user_id bigint not null,
                    lesson_plan_id bigint null,
                    provider varchar(64) not null,
                    model_name varchar(128) not null,
                    action_type varchar(64) not null,
                    prompt longtext null,
                    response longtext null,
                    success tinyint not null default 0,
                    error_message text null,
                    duration_ms int null,
                    created_at datetime not null default current_timestamp,
                    index idx_generation_record_user_created (user_id, created_at),
                    index idx_generation_record_lesson_plan (lesson_plan_id)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists system_config (
                    id bigint primary key auto_increment,
                    config_key varchar(128) not null,
                    config_value text null,
                    description varchar(255) null,
                    created_at datetime not null default current_timestamp,
                    updated_at datetime not null default current_timestamp on update current_timestamp,
                    unique key uk_system_config_key (config_key)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists lesson_template (
                    id bigint primary key auto_increment,
                    name varchar(128) not null,
                    lesson_type varchar(64) not null,
                    description varchar(255) null,
                    enabled tinyint not null default 1,
                    created_at datetime not null default current_timestamp,
                    updated_at datetime not null default current_timestamp on update current_timestamp,
                    unique key uk_lesson_template_name_type (name, lesson_type)
                )
                """);
        seedConfig();
    }

    private void dropColumnIfExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = database()
                  and table_name = ?
                  and column_name = ?
                """, Integer.class, tableName, columnName);
        if (count != null && count > 0) {
            jdbcTemplate.execute("alter table `" + tableName + "` drop column `" + columnName + "`");
        }
    }

    private void seedConfig() {
        jdbcTemplate.update("""
                insert ignore into system_config (config_key, config_value, description)
                values ('school_name', '成都东软学院', '默认学校名称')
                """);
        jdbcTemplate.update("""
                insert ignore into lesson_template (name, lesson_type, description, enabled)
                values
                ('通用理论课模板', '理论课', '适用于常规理论课教案', 1),
                ('实验课模板', '实验课', '适用于实验课教案', 1),
                ('项目实训模板', '项目实训课', '适用于项目实训类教案', 1)
                """);
    }
}
