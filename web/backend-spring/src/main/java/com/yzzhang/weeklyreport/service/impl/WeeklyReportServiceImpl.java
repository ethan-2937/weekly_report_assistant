package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.common.ResourceNotFoundException;
import com.yzzhang.weeklyreport.mapper.SubmissionStatusMapper;
import com.yzzhang.weeklyreport.mapper.WeekFileMapper;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.service.WeeklyReportService;
import com.yzzhang.weeklyreport.util.WeekLabelUtils;
import com.yzzhang.weeklyreport.vo.AnalysisVO;
import com.yzzhang.weeklyreport.vo.SubmissionStatusVO;
import com.yzzhang.weeklyreport.vo.SummaryVO;
import com.yzzhang.weeklyreport.vo.WeekOverviewVO;
import org.springframework.stereotype.Service;

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

    public WeeklyReportServiceImpl(SubmissionStatusMapper submissionStatusMapper, WeekFileMapper weekFileMapper) {
        this.submissionStatusMapper = submissionStatusMapper;
        this.weekFileMapper = weekFileMapper;
    }

    @Override
    public List<WeekOverviewVO> listWeeks() {
        return weekFileMapper.listWeekLabels().stream().map(this::overview).toList();
    }

    @Override
    public WeekOverviewVO latestWeek() {
        return listWeeks().stream().findFirst().orElse(new WeekOverviewVO());
    }

    @Override
    public List<SubmissionStatusVO> listSubmissionStatus(String week) {
        assertWeek(week);
        return submissionStatusMapper.selectByWeek(week).stream().map(this::toVO).toList();
    }

    @Override
    public SummaryVO getSummary(String week) {
        assertWeek(week);
        WeekOverviewVO base = overview(week);
        SummaryVO vo = new SummaryVO();
        copyOverview(base, vo);
        vo.setSubmissionSummary(weekFileMapper.readIfExists(weekFileMapper.submissionSummaryPath(week)));
        vo.setManagerReport(weekFileMapper.readIfExists(weekFileMapper.managerReportPath(week)));
        return vo;
    }

    @Override
    public AnalysisVO getAnalysis(String week) {
        assertWeek(week);
        Path manager = weekFileMapper.managerReportPath(week);
        Path analysis = weekFileMapper.analysisInputPath(week);
        Path source = Files.exists(manager) ? manager : analysis;
        AnalysisVO vo = new AnalysisVO();
        vo.setWeek(week);
        vo.setSource(weekFileMapper.relativize(source));
        vo.setContent(weekFileMapper.readIfExists(source));
        vo.setManagerReport(Files.exists(manager));
        return vo;
    }

    @Override
    public Path getSubmissionStatusCsv(String week) {
        assertWeek(week);
        Path csv = submissionStatusMapper.csvPath(week);
        if (!Files.exists(csv)) {
            throw new ResourceNotFoundException("submission_status.csv not found for " + week);
        }
        return csv;
    }

    private WeekOverviewVO overview(String week) {
        List<SubmissionStatusPO> rows;
        try {
            rows = submissionStatusMapper.selectByWeek(week);
        } catch (ResourceNotFoundException e) {
            rows = List.of();
        }
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
