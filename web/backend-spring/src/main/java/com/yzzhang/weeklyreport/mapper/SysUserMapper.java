package com.yzzhang.weeklyreport.mapper;

import com.yzzhang.weeklyreport.po.SysUserPO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class SysUserMapper {
    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<SysUserPO> rowMapper = new SysUserRowMapper();

    public SysUserMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SysUserPO> findByUsername(String username) {
        try {
            SysUserPO user = jdbcTemplate.queryForObject(
                "select * from sys_user where username = ? limit 1",
                rowMapper,
                username
            );
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<SysUserPO> findByDingIdentity(String dingUserId, String dingUnionId) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("select * from sys_user where ");
        if (hasText(dingUserId) && hasText(dingUnionId)) {
            sql.append("ding_user_id = ? or ding_union_id = ?");
            args.add(dingUserId);
            args.add(dingUnionId);
        } else if (hasText(dingUserId)) {
            sql.append("ding_user_id = ?");
            args.add(dingUserId);
        } else if (hasText(dingUnionId)) {
            sql.append("ding_union_id = ?");
            args.add(dingUnionId);
        } else {
            return Optional.empty();
        }
        sql.append(" limit 1");
        try {
            SysUserPO user = jdbcTemplate.queryForObject(sql.toString(), rowMapper, args.toArray());
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<String> findRoleCodesByUserId(Long userId) {
        return jdbcTemplate.queryForList(
            """
            select r.role_code
            from sys_role r
            join sys_user_role ur on ur.role_id = r.id
            where ur.user_id = ?
            order by r.role_code
            """,
            String.class,
            userId
        );
    }

    public List<String> findDeptScopesByUserId(Long userId) {
        return jdbcTemplate.queryForList(
            """
            select coalesce(dept_name, dept_id, scope_type)
            from sys_dept_scope
            where user_id = ?
            order by id
            """,
            String.class,
            userId
        );
    }

    public void updateLastLoginTime(Long userId) {
        jdbcTemplate.update("update sys_user set last_login_time = now() where id = ?", userId);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static class SysUserRowMapper implements RowMapper<SysUserPO> {
        @Override
        public SysUserPO mapRow(ResultSet rs, int rowNum) throws SQLException {
            SysUserPO user = new SysUserPO();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setPasswordHash(rs.getString("password_hash"));
            user.setRealName(rs.getString("real_name"));
            user.setMobile(rs.getString("mobile"));
            user.setEmail(rs.getString("email"));
            user.setDingUserId(rs.getString("ding_user_id"));
            user.setDingUnionId(rs.getString("ding_union_id"));
            user.setStatus(rs.getInt("status"));
            user.setLastLoginTime(toInstant(rs.getTimestamp("last_login_time")));
            user.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            user.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
            return user;
        }

        private Instant toInstant(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toInstant();
        }
    }
}
