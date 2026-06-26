package com.yzzhang.weeklyreport.service;

import com.yzzhang.weeklyreport.vo.AnalysisVO;
import com.yzzhang.weeklyreport.vo.SubmissionStatusVO;
import com.yzzhang.weeklyreport.vo.SummaryVO;
import com.yzzhang.weeklyreport.vo.WeekOverviewVO;

import java.nio.file.Path;
import java.util.List;

public interface WeeklyReportService {
    List<WeekOverviewVO> listWeeks();
    WeekOverviewVO latestWeek();
    List<SubmissionStatusVO> listSubmissionStatus(String week);
    SummaryVO getSummary(String week);
    AnalysisVO getAnalysis(String week);
    Path getSubmissionStatusCsv(String week);
}
