package com.yzzhang.weeklyreport.service;

import com.yzzhang.weeklyreport.po.SubmissionStatusPO;

import java.util.List;

public interface TemplateComplianceService {
    List<SubmissionStatusPO> enrich(String week, List<SubmissionStatusPO> rows);
}
