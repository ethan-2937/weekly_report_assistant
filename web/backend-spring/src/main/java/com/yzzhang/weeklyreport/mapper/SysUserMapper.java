package com.yzzhang.weeklyreport.mapper;

import com.yzzhang.weeklyreport.po.SysUserPO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    public Optional<SysUserPO> findById(Long id) {
        try {
            SysUserPO user = jdbcTemplate.queryForObject(
                "select * from sys_user where id = ? limit 1",
                rowMapper,
                id
            );
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public List<SysUserPO> listUsers(String keyword) {
        if (hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            return jdbcTemplate.query(
                """
                select *
                from sys_user
                where username like ?
                   or real_name like ?
                   or mobile like ?
                   or email like ?
                   or ding_user_id like ?
                   or ding_union_id like ?
                order by updated_at desc, id desc
                """,
                rowMapper,
                like,
                like,
                like,
                like,
                like,
                like
            );
        }
        return jdbcTemplate.query("select * from sys_user order by updated_at desc, id desc", rowMapper);
    }

    public Long insert(SysUserPO user) {
        var keyHolder = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                """
                insert into sys_user(username, password_hash, real_name, mobile, email, ding_user_id, ding_union_id, status)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getRealName());
            ps.setString(4, user.getMobile());
            ps.setString(5, user.getEmail());
            ps.setString(6, user.getDingUserId());
            ps.setString(7, user.getDingUnionId());
            ps.setInt(8, user.getStatus() == null ? 1 : user.getStatus());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? null : key.longValue();
    }

    public void update(SysUserPO user) {
        jdbcTemplate.update(
            """
            update sys_user
            set username = ?,
                real_name = ?,
                mobile = ?,
                email = ?,
                ding_user_id = ?,
                ding_union_id = ?,
                status = ?
            where id = ?
            """,
            user.getUsername(),
            user.getRealName(),
            user.getMobile(),
            user.getEmail(),
            user.getDingUserId(),
            user.getDingUnionId(),
            user.getStatus() == null ? 1 : user.getStatus(),
            user.getId()
        );
    }

    public void updatePassword(Long userId, String passwordHash) {
        jdbcTemplate.update("update sys_user set password_hash = ? where id = ?", passwordHash, userId);
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

    public List<Map<String, Object>> findAllRoles() {
        return jdbcTemplate.queryForList("select id, role_code, role_name, description from sys_role order by id");
    }

    public void replaceRoleCodes(Long userId, List<String> roleCodes) {
        jdbcTemplate.update("delete from sys_user_role where user_id = ?", userId);
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }
        for (String roleCode : roleCodes.stream().filter(this::hasText).distinct().toList()) {
            jdbcTemplate.update(
                """
                insert ignore into sys_user_role(user_id, role_id)
                select ?, id from sys_role where role_code = ?
                """,
                userId,
                roleCode
            );
        }
    }

    public void replaceDeptScopes(Long userId, List<String> deptScopes) {
        jdbcTemplate.update("delete from sys_dept_scope where user_id = ?", userId);
        if (deptScopes == null || deptScopes.isEmpty()) {
            return;
        }
        for (String scope : deptScopes.stream().filter(this::hasText).map(String::trim).distinct().toList()) {
            String scopeType = "ALL".equalsIgnoreCase(scope) ? "ALL" : "DEPT";
            jdbcTemplate.update(
                "insert into sys_dept_scope(user_id, dept_name, scope_type) values (?, ?, ?)",
                userId,
                scope,
                scopeType
            );
        }
    }

    public long countActiveAdmins() {
        Long count = jdbcTemplate.queryForObject(
            """
            select count(*)
            from sys_user u
            join sys_user_role ur on ur.user_id = u.id
            join sys_role r on r.id = ur.role_id
            where u.status = 1 and r.role_code = 'ADMIN'
            """,
            Long.class
        );
        return count == null ? 0 : count;
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
