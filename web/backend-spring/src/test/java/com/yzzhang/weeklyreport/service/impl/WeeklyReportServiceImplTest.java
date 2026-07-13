package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.mapper.SubmissionStatusMapper;
import com.yzzhang.weeklyreport.mapper.WeekFileMapper;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.po.SysUserPO;
import com.yzzhang.weeklyreport.security.AuthenticatedUser;
import com.yzzhang.weeklyreport.service.TemplateComplianceService;
import com.yzzhang.weeklyreport.vo.AnalysisVO;
import com.yzzhang.weeklyreport.vo.SummaryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyReportServiceImplTest {
    private static final String WEEK = "2026-W28";

    @TempDir
    Path tempDir;

    private SubmissionStatusMapper submissionStatusMapper;
    private WeekFileMapper weekFileMapper;
    private WeeklyReportServiceImpl service;
    private Path managerReport;
    private Path analysisInput;
    private Path submissionSummary;

    @BeforeEach
    void setUp() throws IOException {
        submissionStatusMapper = mock(SubmissionStatusMapper.class);
        weekFileMapper = mock(WeekFileMapper.class);
        TemplateComplianceService complianceService = mock(TemplateComplianceService.class);
        managerReport = tempDir.resolve("manager_report.md");
        analysisInput = tempDir.resolve("analysis_input.md");
        submissionSummary = tempDir.resolve("submission_check.md");
        Files.writeString(managerReport, managerMarkdown(), StandardCharsets.UTF_8);
        Files.writeString(submissionSummary, "完整提交摘要：示例员工乙的受限内容", StandardCharsets.UTF_8);
        when(submissionStatusMapper.selectByWeek(WEEK)).thenReturn(sampleRows());
        when(complianceService.enrich(eq(WEEK), anyList())).thenAnswer(invocation -> invocation.getArgument(1));
        when(weekFileMapper.managerReportPath(WEEK)).thenReturn(managerReport);
        when(weekFileMapper.submissionSummaryPath(WEEK)).thenReturn(submissionSummary);
        when(weekFileMapper.analysisInputPath(WEEK)).thenReturn(analysisInput);
        when(weekFileMapper.readIfExists(managerReport)).thenReturn(managerMarkdown());
        when(weekFileMapper.readIfExists(submissionSummary)).thenReturn("完整提交摘要：示例员工乙的受限内容");
        when(weekFileMapper.relativize(managerReport)).thenReturn("fictional/manager_report.md");
        service = new WeeklyReportServiceImpl(
            submissionStatusMapper,
            weekFileMapper,
            new ReportPermissionServiceImpl(),
            complianceService
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void scopedListAndCsvUseTheSameServiceLayerFilter() throws IOException {
        authenticate(List.of("USER"), List.of("USERID:test-user-001"));

        assertThat(service.listSubmissionStatus(WEEK))
            .extracting(item -> item.getUserid())
            .containsExactly("test-user-001");

        Path csv = service.getSubmissionStatusCsv(WEEK);
        String content = Files.readString(csv, StandardCharsets.UTF_8);
        assertThat(content)
            .contains("示例员工甲", "test-user-001")
            .doesNotContain("示例员工乙", "test-user-002", "受限周报正文");
    }

    @Test
    void scopedSummaryAndAnalysisRemoveUnauthorizedMarkdownContent() {
        authenticate(List.of("USER"), List.of("DEPT:测试研发部"));

        SummaryVO summary = service.getSummary(WEEK);
        AnalysisVO analysis = service.getAnalysis(WEEK);

        assertThat(summary.getSubmissionSummary())
            .contains("示例员工甲")
            .doesNotContain("示例员工乙", "受限周报正文");
        assertThat(summary.getManagerReport())
            .contains("示例员工甲", "授权周报正文", "附件待解析：授权负责人证据")
            .doesNotContain("示例员工乙", "受限周报正文", "未见证据：受限负责人证据");
        assertThat(analysis.getContent())
            .contains("示例员工甲", "授权周报正文", "附件待解析：授权负责人证据")
            .doesNotContain("示例员工乙", "受限周报正文", "未见证据：受限负责人证据");
    }

    @Test
    void scopedRawAnalysisKeepsOnlyAuthorizedTeamLeadEvidence() throws IOException {
        Files.delete(managerReport);
        Files.writeString(analysisInput, analysisMarkdown(), StandardCharsets.UTF_8);
        when(weekFileMapper.readIfExists(analysisInput)).thenReturn(analysisMarkdown());
        when(weekFileMapper.relativize(analysisInput)).thenReturn("fictional/analysis_input.md");
        authenticate(List.of("USER"), List.of("USERID:test-user-001"));

        AnalysisVO analysis = service.getAnalysis(WEEK);

        assertThat(analysis.getContent())
            .contains("团队负责人履职输入（授权范围）", "示例员工甲", "附件待解析：授权负责人证据")
            .doesNotContain("示例员工乙", "未见证据：受限负责人证据");
    }

    @Test
    void reportAllReceivesTheUnmodifiedFullMarkdownAndEveryRow() {
        authenticate(List.of("REPORT_ALL"), List.of());

        SummaryVO summary = service.getSummary(WEEK);
        AnalysisVO analysis = service.getAnalysis(WEEK);

        assertThat(service.listSubmissionStatus(WEEK)).hasSize(2);
        assertThat(summary.getSubmissionSummary()).isEqualTo("完整提交摘要：示例员工乙的受限内容");
        assertThat(summary.getManagerReport()).isEqualTo(managerMarkdown());
        assertThat(analysis.getContent()).isEqualTo(managerMarkdown());
    }

    private void authenticate(List<String> roles, List<String> scopes) {
        SysUserPO user = new SysUserPO();
        user.setId(201L);
        user.setUsername("test-account-002");
        user.setStatus(1);
        AuthenticatedUser principal = new AuthenticatedUser(user, roles, scopes);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private List<SubmissionStatusPO> sampleRows() {
        SubmissionStatusPO allowed = row("示例员工甲", "test-user-001", "测试研发部");
        SubmissionStatusPO denied = row("示例员工乙", "test-user-002", "测试市场部");
        return List.of(allowed, denied);
    }

    private SubmissionStatusPO row(String name, String userId, String dept) {
        SubmissionStatusPO row = new SubmissionStatusPO();
        row.setStatus("已提交");
        row.setName(name);
        row.setUserid(userId);
        row.setDept(dept);
        row.setReportDept(dept);
        row.setLeaderCandidate("是");
        row.setTitle("测试岗位");
        row.setSubmitTime("2026-07-10 10:00:00");
        return row;
    }

    private String managerMarkdown() {
        return """
            # 2026-W28 虚构周报评价

            ## 员工五维评价

            ### 示例员工甲
            - 授权周报正文：虚构交付物甲。

            ### 示例员工乙
            - 受限周报正文：虚构交付物乙。

            ## 团队负责人履职检查

            | 负责人 | 管理团队/部门 | 个人周报是否提交 | 团队汇总是否提交 | 履职结论 |
            |---|---|---|---|---|
            | 示例员工甲 | 测试研发部 | 已提交 | 附件待解析 | 附件待解析：授权负责人证据 |
            | 示例员工乙 | 测试市场部 | 已提交 | 未见证据 | 未见证据：受限负责人证据，提及示例员工甲 |
            """;
    }

    private String analysisMarkdown() {
        return """
            # Weekly Report Analysis Pack

            ## 团队负责人履职输入（确定性证据）

            | 负责人 | userid | 管理团队/部门 | 团队汇总证据 |
            |---|---|---|---|
            | 示例员工甲 | test-user-001 | 测试研发部 | 附件待解析：授权负责人证据 |
            | 示例员工乙 | test-user-002 | 测试市场部 | 未见证据：受限负责人证据，提及示例员工甲 |

            ## Submitted Reports

            ### Report 1: 示例员工甲 (test-user-001)
            - 授权周报正文：虚构交付物甲。

            ### Report 2: 示例员工乙 (test-user-002)
            - 受限周报正文：虚构交付物乙。
            """;
    }
}
