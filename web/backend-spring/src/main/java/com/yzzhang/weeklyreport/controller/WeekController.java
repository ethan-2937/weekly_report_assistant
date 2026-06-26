package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.service.WeeklyReportService;
import com.yzzhang.weeklyreport.vo.AnalysisVO;
import com.yzzhang.weeklyreport.vo.SubmissionStatusVO;
import com.yzzhang.weeklyreport.vo.SummaryVO;
import com.yzzhang.weeklyreport.vo.WeekOverviewVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/weeks")
public class WeekController {
    private final WeeklyReportService weeklyReportService;

    public WeekController(WeeklyReportService weeklyReportService) {
        this.weeklyReportService = weeklyReportService;
    }

    @GetMapping
    public List<WeekOverviewVO> listWeeks() {
        return weeklyReportService.listWeeks();
    }

    @GetMapping("/latest")
    public WeekOverviewVO latestWeek() {
        return weeklyReportService.latestWeek();
    }

    @GetMapping("/{week}/submission-status")
    public List<SubmissionStatusVO> submissionStatus(@PathVariable String week) {
        return weeklyReportService.listSubmissionStatus(week);
    }

    @GetMapping("/{week}/summary")
    public SummaryVO summary(@PathVariable String week) {
        return weeklyReportService.getSummary(week);
    }

    @GetMapping("/{week}/analysis")
    public AnalysisVO analysis(@PathVariable String week) {
        return weeklyReportService.getAnalysis(week);
    }
}
