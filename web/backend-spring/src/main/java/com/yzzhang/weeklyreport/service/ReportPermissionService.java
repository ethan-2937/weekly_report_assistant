package com.yzzhang.weeklyreport.service;

import com.yzzhang.weeklyreport.po.SubmissionStatusPO;

import java.util.List;

public interface ReportPermissionService {
    ReportPermission currentPermission();

    List<SubmissionStatusPO> filterRows(List<SubmissionStatusPO> rows, ReportPermission permission);

    record ReportPermission(boolean fullAccess, List<String> scopes) {
        public boolean hasScopedAccess() {
            return fullAccess || (scopes != null && !scopes.isEmpty());
        }
    }
}
