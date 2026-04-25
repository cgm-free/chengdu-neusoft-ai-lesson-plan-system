package cn.edu.nsu.maic.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CoursePlanSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public CoursePlanSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute("""
                create table if not exists course_plan (
                    id bigint primary key auto_increment,
                    user_id bigint not null,
                    title varchar(255) not null,
                    course_name varchar(255) not null,
                    template_file_name varchar(255) not null,
                    template_file longblob not null,
                    teacher_requirements text null,
                    analysis_json longtext not null,
                    content_json longtext not null,
                    status varchar(32) not null default 'draft',
                    created_at datetime not null default current_timestamp,
                    updated_at datetime not null default current_timestamp on update current_timestamp,
                    index idx_course_plan_user_updated (user_id, updated_at),
                    index idx_course_plan_status (status)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists course_plan_material (
                    id bigint primary key auto_increment,
                    course_plan_id bigint not null,
                    role varchar(32) not null,
                    file_name varchar(255) not null,
                    file_type varchar(32) not null,
                    file_blob longblob not null,
                    sort_order int not null default 0,
                    created_at datetime not null default current_timestamp,
                    index idx_course_plan_material_plan (course_plan_id, role, sort_order)
                )
                """);
        jdbcTemplate.execute("""
                create table if not exists course_plan_generation_job (
                    id bigint primary key auto_increment,
                    user_id bigint not null,
                    course_plan_id bigint null,
                    course_name varchar(255) not null,
                    semester varchar(255) null,
                    status varchar(32) not null,
                    stage varchar(64) not null,
                    progress_current int not null default 0,
                    progress_total int not null default 0,
                    message varchar(1024) not null,
                    request_json longtext not null,
                    error_json longtext null,
                    partial_analysis_json longtext null,
                    partial_content_json longtext null,
                    created_at datetime not null default current_timestamp,
                    updated_at datetime not null default current_timestamp on update current_timestamp,
                    index idx_course_plan_job_user_status (user_id, status, updated_at),
                    index idx_course_plan_job_plan_status (course_plan_id, status, updated_at)
                )
                """);
        addColumnIfMissing("course_plan_generation_job", "partial_analysis_json", "longtext null");
        addColumnIfMissing("course_plan_generation_job", "partial_content_json", "longtext null");
        jdbcTemplate.execute("""
                create table if not exists course_plan_generation_job_material (
                    id bigint primary key auto_increment,
                    job_id bigint not null,
                    role varchar(32) not null,
                    file_name varchar(255) not null,
                    file_type varchar(32) not null,
                    file_blob longblob not null,
                    sort_order int not null default 0,
                    created_at datetime not null default current_timestamp,
                    index idx_course_plan_job_material_job (job_id, role, sort_order)
                )
                """);
    }

    private void addColumnIfMissing(String tableName, String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from information_schema.columns
                        where table_schema = database()
                          and table_name = ?
                          and column_name = ?
                        """,
                Integer.class,
                tableName,
                columnName
        );
        if (count == null || count == 0) {
            jdbcTemplate.execute("alter table " + tableName + " add column " + columnName + " " + columnDefinition);
        }
    }
}
