package com.yzzhang.weeklyreport.service;

import com.yzzhang.weeklyreport.vo.WeeklyReportDetailVO;

public interface WeeklyReportSourceService {
    WeeklyReportDetailVO getReport(String week, String userId);
}
