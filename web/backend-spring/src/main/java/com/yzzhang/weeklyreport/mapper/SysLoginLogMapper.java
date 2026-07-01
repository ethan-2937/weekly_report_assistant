package com.yzzhang.weeklyreport.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SysLoginLogMapper {
    private final JdbcTemplate jdbcTemplate;

    public SysLoginLogMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Long userId, String username, String loginType, boolean success, String ip, String userAgent, String message) {
        jdbcTemplate.update(
            """
            insert into sys_login_log(user_id, username, login_type, success, ip, user_agent, message)
            values (?, ?, ?, ?, ?, ?, ?)
            """,
            userId,
            username,
            loginType,
            success ? 1 : 0,
            ip,
            userAgent,
            message
        );
    }
}
