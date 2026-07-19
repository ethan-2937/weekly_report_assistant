package com.yzzhang.weeklyreport.service.impl;

import java.util.Map;

record OriginalReportRow(
    String reportId,
    String templateName,
    String userId,
    String employeeNumber,
    String name,
    String department,
    String createdAt,
    String modifiedAt,
    Map<String, String> fields,
    String images,
    String remark
) {
}
