package com.yzzhang.weeklyreport.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AuthSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final WeeklyReportProperties properties;

    public AuthSchemaInitializer(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, WeeklyReportProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        createTables();
        seedRoles();
        seedAdmin();
    }

    private void createTables() {
        jdbcTemplate.execute("""
            create table if not exists sys_user (
              id bigint primary key auto_increment,
              username varchar(64) not null,
              password_hash varchar(255) null,
              real_name varchar(64) null,
              mobile varchar(32) null,
              email varchar(128) null,
              ding_user_id varchar(128) null,
              ding_union_id varchar(128) null,
              status tinyint not null default 1,
              last_login_time datetime null,
              created_at datetime not null default current_timestamp,
              updated_at datetime not null default current_timestamp on update current_timestamp,
              unique key uk_sys_user_username (username),
              unique key uk_sys_user_ding_user_id (ding_user_id),
              unique key uk_sys_user_ding_union_id (ding_union_id),
              key idx_sys_user_status (status)
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """);

        jdbcTemplate.execute("""
            create table if not exists sys_role (
              id bigint primary key auto_increment,
              role_code varchar(32) not null,
              role_name varchar(64) not null,
              description varchar(255) null,
              created_at datetime not null default current_timestamp,
              unique key uk_sys_role_code (role_code)
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """);

        jdbcTemplate.execute("""
            create table if not exists sys_user_role (
              user_id bigint not null,
              role_id bigint not null,
              created_at datetime not null default current_timestamp,
              primary key (user_id, role_id),
              key idx_sys_user_role_role_id (role_id)
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """);

        jdbcTemplate.execute("""
            create table if not exists sys_dept_scope (
              id bigint primary key auto_increment,
              user_id bigint not null,
              dept_id varchar(64) null,
              dept_name varchar(128) null,
              scope_type varchar(32) not null default 'DEPT',
              created_at datetime not null default current_timestamp,
              key idx_sys_dept_scope_user_id (user_id)
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """);

        jdbcTemplate.execute("""
            create table if not exists sys_login_log (
              id bigint primary key auto_increment,
              user_id bigint null,
              username varchar(64) null,
              login_type varchar(32) not null,
              success tinyint not null,
              ip varchar(64) null,
              user_agent varchar(512) null,
              message varchar(512) null,
              created_at datetime not null default current_timestamp,
              key idx_sys_login_log_user_id (user_id),
              key idx_sys_login_log_created_at (created_at)
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """);
    }

    private void seedRoles() {
        insertRole("ADMIN", "系统管理员", "系统配置、用户权限和全部周报数据");
        insertRole("HR", "HR", "查看全部周报评价和提交统计");
        insertRole("MANAGER", "团队负责人", "预留：查看授权团队范围内周报");
        insertRole("USER", "普通用户", "预留：普通员工访问身份");
    }

    private void seedAdmin() {
        String username = properties.getAuth().getBootstrapAdminUsername();
        Integer exists = jdbcTemplate.queryForObject(
            "select count(*) from sys_user where username = ?",
            Integer.class,
            username
        );
        if (exists != null && exists == 0) {
            jdbcTemplate.update(
                "insert into sys_user(username, password_hash, real_name, status) values (?, ?, ?, 1)",
                username,
                passwordEncoder.encode(properties.getAuth().getBootstrapAdminPassword()),
                properties.getAuth().getBootstrapAdminRealName()
            );
        }

        Long userId = jdbcTemplate.queryForObject("select id from sys_user where username = ?", Long.class, username);
        Long adminRoleId = jdbcTemplate.queryForObject("select id from sys_role where role_code = 'ADMIN'", Long.class);
        Integer bound = jdbcTemplate.queryForObject(
            "select count(*) from sys_user_role where user_id = ? and role_id = ?",
            Integer.class,
            userId,
            adminRoleId
        );
        if (bound != null && bound == 0) {
            jdbcTemplate.update("insert into sys_user_role(user_id, role_id) values (?, ?)", userId, adminRoleId);
        }
    }

    private void insertRole(String roleCode, String roleName, String description) {
        jdbcTemplate.update(
            """
            insert into sys_role(role_code, role_name, description)
            values (?, ?, ?)
            on duplicate key update role_name = values(role_name), description = values(description)
            """,
            roleCode,
            roleName,
            description
        );
    }
}
