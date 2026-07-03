package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.security.AuthenticatedUser;
import com.yzzhang.weeklyreport.service.ReportPermissionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class ReportPermissionServiceImpl implements ReportPermissionService {
    @Override
    public ReportPermission currentPermission() {
        AuthenticatedUser user = currentAuthenticatedUser();
        List<String> roles = user.getRoles();
        List<String> scopes = user.getDeptScopes().stream()
            .map(String::trim)
            .filter(this::hasText)
            .distinct()
            .toList();
        boolean fullAccess = roles.contains("ADMIN")
            || roles.contains("REPORT_ALL")
            || scopes.stream().anyMatch(scope -> "ALL".equalsIgnoreCase(scope));
        ReportPermission permission = new ReportPermission(fullAccess, scopes);
        if (!permission.hasScopedAccess()) {
            throw new AccessDeniedException("当前账号没有周报查看范围，请联系管理员配置权限范围");
        }
        return permission;
    }

    @Override
    public List<SubmissionStatusPO> filterRows(List<SubmissionStatusPO> rows, ReportPermission permission) {
        if (permission.fullAccess()) {
            return rows;
        }
        return rows.stream()
            .filter(row -> canView(row, permission.scopes()))
            .toList();
    }

    private boolean canView(SubmissionStatusPO row, List<String> scopes) {
        for (String rawScope : scopes) {
            Scope scope = parseScope(rawScope);
            if (scope.matches(row)) {
                return true;
            }
        }
        return false;
    }

    private Scope parseScope(String rawScope) {
        String scope = rawScope == null ? "" : rawScope.trim();
        String upper = scope.toUpperCase(Locale.ROOT);
        if (upper.startsWith("DEPT:") || scope.startsWith("部门:") || scope.startsWith("团队:")) {
            return new Scope(ScopeType.DEPT, afterColon(scope));
        }
        if (upper.startsWith("USER:") || upper.startsWith("NAME:") || scope.startsWith("人员:") || scope.startsWith("姓名:")) {
            return new Scope(ScopeType.NAME, afterColon(scope));
        }
        if (upper.startsWith("USERID:") || upper.startsWith("ID:")) {
            return new Scope(ScopeType.USERID, afterColon(scope));
        }
        return new Scope(ScopeType.AUTO, scope);
    }

    private String afterColon(String value) {
        int index = value.indexOf(':');
        return index < 0 ? value.trim() : value.substring(index + 1).trim();
    }

    private AuthenticatedUser currentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AccessDeniedException("请先登录");
        }
        return user;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private enum ScopeType {
        AUTO,
        DEPT,
        NAME,
        USERID
    }

    private record Scope(ScopeType type, String value) {
        private boolean matches(SubmissionStatusPO row) {
            if (value == null || value.isBlank()) {
                return false;
            }
            return switch (type) {
                case DEPT -> deptMatches(row);
                case NAME -> value.equals(normalize(row.getName()));
                case USERID -> value.equals(normalize(row.getUserid()));
                case AUTO -> value.equals(normalize(row.getUserid()))
                    || value.equals(normalize(row.getName()))
                    || deptMatches(row);
            };
        }

        private boolean deptMatches(SubmissionStatusPO row) {
            return deptFieldMatches(row.getDept()) || deptFieldMatches(row.getReportDept());
        }

        private boolean deptFieldMatches(String deptField) {
            if (deptField == null || deptField.isBlank()) {
                return false;
            }
            return Arrays.stream(deptField.split("[/、,，;；]+"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .anyMatch(this::segmentMatches);
        }

        private boolean segmentMatches(String segment) {
            String normalizedValue = normalize(value);
            String normalizedSegment = normalize(segment);
            if (normalizedSegment.equals(normalizedValue)) {
                return true;
            }
            // 允许“财务”匹配“财务组”，但避免“重客研发组”误匹配“重客研发组1”。
            return !normalizedValue.endsWith("组") && normalizedSegment.equals(normalizedValue + "组");
        }

        private String normalize(String text) {
            return text == null ? "" : text.trim();
        }
    }
}
