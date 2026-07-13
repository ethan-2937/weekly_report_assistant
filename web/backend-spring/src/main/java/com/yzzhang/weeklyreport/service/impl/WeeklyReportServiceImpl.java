package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.common.ResourceNotFoundException;
import com.yzzhang.weeklyreport.mapper.SubmissionStatusMapper;
import com.yzzhang.weeklyreport.mapper.WeekFileMapper;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.service.ReportPermissionService;
import com.yzzhang.weeklyreport.service.TemplateComplianceService;
import com.yzzhang.weeklyreport.service.WeeklyReportService;
import com.yzzhang.weeklyreport.util.WeekLabelUtils;
import com.yzzhang.weeklyreport.vo.AnalysisVO;
import com.yzzhang.weeklyreport.vo.SubmissionStatusVO;
import com.yzzhang.weeklyreport.vo.SummaryVO;
import com.yzzhang.weeklyreport.vo.WeekOverviewVO;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class WeeklyReportServiceImpl implements WeeklyReportService {
    private static final String STATUS_SUBMITTED = "\u5df2\u63d0\u4ea4";
    private static final String STATUS_MISSING = "\u672a\u63d0\u4ea4";
    private static final String YES = "\u662f";

    private final SubmissionStatusMapper submissionStatusMapper;
    private final WeekFileMapper weekFileMapper;
    private final ReportPermissionService reportPermissionService;
    private final TemplateComplianceService templateComplianceService;
    private final ReportContentFilter reportContentFilter = new ReportContentFilter();

    public WeeklyReportServiceImpl(
        SubmissionStatusMapper submissionStatusMapper,
        WeekFileMapper weekFileMapper,
        ReportPermissionService reportPermissionService,
        TemplateComplianceService templateComplianceService
    ) {
        this.submissionStatusMapper = submissionStatusMapper;
        this.weekFileMapper = weekFileMapper;
        this.reportPermissionService = reportPermissionService;
        this.templateComplianceService = templateComplianceService;
    }

    @Override
    public List<WeekOverviewVO> listWeeks() {
        ReportPermissionService.ReportPermission permission = reportPermissionService.currentPermission();
        return weekFileMapper.listWeekLabels().stream().map(week -> overview(week, permission)).toList();
    }

    @Override
    public WeekOverviewVO latestWeek() {
        return listWeeks().stream().findFirst().orElse(new WeekOverviewVO());
    }

    @Override
    public List<SubmissionStatusVO> listSubmissionStatus(String week) {
        assertWeek(week);
        ReportPermissionService.ReportPermission permission = reportPermissionService.currentPermission();
        return templateComplianceService.enrich(week, visibleRows(week, permission))
            .stream()
            .map(this::toVO)
            .toList();
    }

    @Override
    public SummaryVO getSummary(String week) {
        assertWeek(week);
        ReportPermissionService.ReportPermission permission = reportPermissionService.currentPermission();
        List<SubmissionStatusPO> allRows = templateComplianceService.enrich(week, selectRows(week));
        List<SubmissionStatusPO> visibleRows = reportPermissionService.filterRows(allRows, permission);
        WeekOverviewVO base = overview(week, visibleRows);
        SummaryVO vo = new SummaryVO();
        copyOverview(base, vo);
        String submissionSummary = weekFileMapper.readIfExists(weekFileMapper.submissionSummaryPath(week));
        String managerReport = weekFileMapper.readIfExists(weekFileMapper.managerReportPath(week));
        if (permission.fullAccess()) {
            vo.setSubmissionSummary(submissionSummary);
            vo.setManagerReport(managerReport);
        } else {
            vo.setSubmissionSummary(reportContentFilter.buildSubmissionSummary(week, visibleRows));
            vo.setManagerReport(reportContentFilter.filterManagerReport(managerReport, week, allRows, visibleRows));
        }
        return vo;
    }

    @Override
    public AnalysisVO getAnalysis(String week) {
        assertWeek(week);
        ReportPermissionService.ReportPermission permission = reportPermissionService.currentPermission();
        Path manager = weekFileMapper.managerReportPath(week);
        Path analysis = weekFileMapper.analysisInputPath(week);
        Path source = Files.exists(manager) ? manager : analysis;
        String content = weekFileMapper.readIfExists(source);
        if (!permission.fullAccess()) {
            List<SubmissionStatusPO> allRows = templateComplianceService.enrich(week, selectRows(week));
            List<SubmissionStatusPO> visibleRows = reportPermissionService.filterRows(allRows, permission);
            content = Files.exists(manager)
                ? reportContentFilter.filterManagerReport(content, week, allRows, visibleRows)
                : reportContentFilter.filterAnalysisInput(content, week, allRows, visibleRows);
        }
        AnalysisVO vo = new AnalysisVO();
        vo.setWeek(week);
        vo.setSource(weekFileMapper.relativize(source));
        vo.setContent(content);
        vo.setManagerReport(Files.exists(manager));
        return vo;
    }

    @Override
    public Path getSubmissionStatusCsv(String week) {
        assertWeek(week);
        ReportPermissionService.ReportPermission permission = reportPermissionService.currentPermission();
        List<SubmissionStatusPO> rows = templateComplianceService.enrich(week, visibleRows(week, permission));
        try {
            Path exported = Files.createTempFile("submission_status_" + week + "_", ".csv");
            Files.writeString(exported, reportContentFilter.toCsv(rows), StandardCharsets.UTF_8);
            exported.toFile().deleteOnExit();
            return exported;
        } catch (IOException e) {
            throw new ResourceNotFoundException("failed to create submission_status.csv: " + e.getMessage());
        }
    }

    private WeekOverviewVO overview(String week, ReportPermissionService.ReportPermission permission) {
        return overview(week, visibleRowsOrEmpty(week, permission));
    }

    private WeekOverviewVO overview(String week, List<SubmissionStatusPO> rows) {
        long submitted = rows.stream().filter(row -> STATUS_SUBMITTED.equals(row.getStatus())).count();
        long missing = rows.stream().filter(row -> STATUS_MISSING.equals(row.getStatus())).count();
        long leaders = rows.stream().filter(row -> YES.equals(row.getLeaderCandidate())).count();
        WeekOverviewVO vo = new WeekOverviewVO();
        vo.setWeek(week);
        vo.setExpectedCount(rows.size());
        vo.setSubmittedCount(submitted);
        vo.setMissingCount(missing);
        vo.setLeaderCandidateCount(leaders);
        vo.setHasManagerReport(weekFileMapper.exists(weekFileMapper.managerReportPath(week)));
        vo.setGeneratedAt(weekFileMapper.latestModified(week).map(Object::toString).orElse(""));
        return vo;
    }

    private List<SubmissionStatusPO> visibleRows(String week, ReportPermissionService.ReportPermission permission) {
        return reportPermissionService.filterRows(selectRows(week), permission);
    }

    private List<SubmissionStatusPO> visibleRowsOrEmpty(String week, ReportPermissionService.ReportPermission permission) {
        try {
            return visibleRows(week, permission);
        } catch (ResourceNotFoundException e) {
            return List.of();
        }
    }

    private List<SubmissionStatusPO> selectRows(String week) {
        return submissionStatusMapper.selectByWeek(week);
    }

    private SubmissionStatusVO toVO(SubmissionStatusPO po) {
        SubmissionStatusVO vo = new SubmissionStatusVO();
        vo.setStatus(po.getStatus());
        vo.setName(po.getName());
        vo.setUserid(po.getUserid());
        vo.setDept(po.getDept());
        vo.setLeaderCandidate(po.getLeaderCandidate());
        vo.setTitle(po.getTitle());
        vo.setReportDept(po.getReportDept());
        vo.setSubmitTime(po.getSubmitTime());
        vo.setReportId(po.getReportId());
        vo.setTemplateName(po.getTemplateName());
        vo.setTemplateComplianceRate(po.getTemplateComplianceRate());
        vo.setTemplateComplianceStatus(po.getTemplateComplianceStatus());
        vo.setTemplateComplianceMissingFields(po.getTemplateComplianceMissingFields());
        vo.setTemplateCompliancePresentFields(po.getTemplateCompliancePresentFields());
        vo.setTemplateComplianceDetail(po.getTemplateComplianceDetail());
        return vo;
    }

    private void copyOverview(WeekOverviewVO source, WeekOverviewVO target) {
        target.setWeek(source.getWeek());
        target.setExpectedCount(source.getExpectedCount());
        target.setSubmittedCount(source.getSubmittedCount());
        target.setMissingCount(source.getMissingCount());
        target.setLeaderCandidateCount(source.getLeaderCandidateCount());
        target.setHasManagerReport(source.isHasManagerReport());
        target.setGeneratedAt(source.getGeneratedAt());
    }

    private void assertWeek(String week) {
        if (!WeekLabelUtils.isValid(week)) {
            throw new IllegalArgumentException("Invalid week label: " + week);
        }
    }
}
