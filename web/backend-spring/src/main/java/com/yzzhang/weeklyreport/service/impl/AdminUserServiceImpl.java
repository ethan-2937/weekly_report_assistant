package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.common.BizException;
import com.yzzhang.weeklyreport.mapper.SysUserMapper;
import com.yzzhang.weeklyreport.po.SysUserPO;
import com.yzzhang.weeklyreport.service.AdminUserService;
import com.yzzhang.weeklyreport.vo.AdminUserSaveRequestVO;
import com.yzzhang.weeklyreport.vo.AdminUserVO;
import com.yzzhang.weeklyreport.vo.PasswordResetRequestVO;
import com.yzzhang.weeklyreport.vo.RoleVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class AdminUserServiceImpl implements AdminUserService {
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserServiceImpl(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<AdminUserVO> listUsers(String keyword) {
        return sysUserMapper.listUsers(keyword).stream().map(this::toVO).toList();
    }

    @Override
    @Transactional
    public AdminUserVO createUser(AdminUserSaveRequestVO request) {
        if (!hasText(request.getPassword()) && !hasText(request.getDingUserId()) && !hasText(request.getDingUnionId())) {
            throw new BizException("新建用户至少需要设置密码或绑定钉钉身份");
        }
        if (hasText(request.getPassword()) && request.getPassword().length() < 6) {
            throw new BizException("密码至少需要6位");
        }
        SysUserPO user = fromRequest(new SysUserPO(), request);
        user.setPasswordHash(hasText(request.getPassword()) ? passwordEncoder.encode(request.getPassword()) : null);
        try {
            Long userId = sysUserMapper.insert(user);
            sysUserMapper.replaceRoleCodes(userId, normalizeRoles(request.getRoles()));
            sysUserMapper.replaceDeptScopes(userId, request.getDeptScopes());
            return sysUserMapper.findById(userId).map(this::toVO).orElseThrow(() -> new BizException("用户创建失败"));
        } catch (DuplicateKeyException ex) {
            throw new BizException("用户名或钉钉身份已存在");
        }
    }

    @Override
    @Transactional
    public AdminUserVO updateUser(Long id, AdminUserSaveRequestVO request) {
        SysUserPO existing = sysUserMapper.findById(id).orElseThrow(() -> new BizException("用户不存在"));
        List<String> roles = normalizeRoles(request.getRoles());
        ensureNotRemovingLastAdmin(existing, request.getStatus(), roles);
        SysUserPO user = fromRequest(existing, request);
        try {
            sysUserMapper.update(user);
            sysUserMapper.replaceRoleCodes(id, roles);
            sysUserMapper.replaceDeptScopes(id, request.getDeptScopes());
            return sysUserMapper.findById(id).map(this::toVO).orElseThrow(() -> new BizException("用户更新失败"));
        } catch (DuplicateKeyException ex) {
            throw new BizException("用户名或钉钉身份已存在");
        }
    }

    @Override
    public void resetPassword(Long id, PasswordResetRequestVO request) {
        sysUserMapper.findById(id).orElseThrow(() -> new BizException("用户不存在"));
        sysUserMapper.updatePassword(id, passwordEncoder.encode(request.getPassword()));
    }

    @Override
    public List<RoleVO> listRoles() {
        return sysUserMapper.findAllRoles().stream().map(this::toRoleVO).toList();
    }

    private SysUserPO fromRequest(SysUserPO user, AdminUserSaveRequestVO request) {
        user.setUsername(normalizeRequired(request.getUsername()));
        user.setRealName(normalizeNullable(request.getRealName()));
        user.setMobile(normalizeNullable(request.getMobile()));
        user.setEmail(normalizeNullable(request.getEmail()));
        user.setDingUserId(normalizeNullable(request.getDingUserId()));
        user.setDingUnionId(normalizeNullable(request.getDingUnionId()));
        user.setStatus(request.getStatus() != null && request.getStatus() == 0 ? 0 : 1);
        return user;
    }

    private List<String> normalizeRoles(List<String> roles) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (roles != null) {
            for (String role : roles) {
                if (hasText(role)) {
                    normalized.add(role.trim().toUpperCase());
                }
            }
        }
        if (normalized.isEmpty()) {
            normalized.add("USER");
        }
        return new ArrayList<>(normalized);
    }

    private void ensureNotRemovingLastAdmin(SysUserPO existing, Integer nextStatus, List<String> nextRoles) {
        List<String> currentRoles = sysUserMapper.findRoleCodesByUserId(existing.getId());
        boolean wasActiveAdmin = existing.getStatus() != null
            && existing.getStatus() == 1
            && currentRoles.contains("ADMIN");
        boolean remainsActiveAdmin = (nextStatus == null || nextStatus == 1) && nextRoles.contains("ADMIN");
        if (wasActiveAdmin && !remainsActiveAdmin && sysUserMapper.countActiveAdmins() <= 1) {
            throw new BizException("至少需要保留一个启用状态的系统管理员");
        }
    }

    private AdminUserVO toVO(SysUserPO user) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setMobile(user.getMobile());
        vo.setEmail(user.getEmail());
        vo.setDingUserId(user.getDingUserId());
        vo.setDingUnionId(user.getDingUnionId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(formatInstant(user.getLastLoginTime()));
        vo.setCreatedAt(formatInstant(user.getCreatedAt()));
        vo.setUpdatedAt(formatInstant(user.getUpdatedAt()));
        vo.setRoles(sysUserMapper.findRoleCodesByUserId(user.getId()));
        vo.setDeptScopes(sysUserMapper.findDeptScopesByUserId(user.getId()));
        return vo;
    }

    private RoleVO toRoleVO(Map<String, Object> row) {
        RoleVO vo = new RoleVO();
        vo.setId(numberValue(row.get("id")));
        vo.setRoleCode(stringValue(row.get("role_code")));
        vo.setRoleName(stringValue(row.get("role_name")));
        vo.setDescription(stringValue(row.get("description")));
        return vo;
    }

    private Long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeRequired(String value) {
        if (!hasText(value)) {
            throw new BizException("请输入用户名");
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
