package com.yzzhang.weeklyreport.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.common.ResourceNotFoundException;
import com.yzzhang.weeklyreport.mapper.SubmissionStatusMapper;
import com.yzzhang.weeklyreport.mapper.WeekFileMapper;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.po.SysUserPO;
import com.yzzhang.weeklyreport.security.AuthenticatedUser;
import com.yzzhang.weeklyreport.vo.WeeklyReportDetailVO;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeeklyReportSourceServiceImplTest {
    private static final String WEEK = "2026-W28";

    @TempDir
    Path tempDir;

    private SubmissionStatusMapper submissionStatusMapper;
    private WeekFileMapper weekFileMapper;
    private WeeklyReportSourceServiceImpl service;
    private Path rawReports;

    @BeforeEach
    void setUp() {
        submissionStatusMapper = mock(SubmissionStatusMapper.class);
        weekFileMapper = mock(WeekFileMapper.class);
        rawReports = tempDir.resolve("reports.json");
        when(weekFileMapper.rawReportsPath(WEEK)).thenReturn(rawReports);
        service = new WeeklyReportSourceServiceImpl(
            submissionStatusMapper,
            weekFileMapper,
            new ReportPermissionServiceImpl(),
            new ObjectMapper()
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reportAllReadsExactReportAndRedactsCredentialShapes() throws IOException {
        SubmissionStatusPO target = row("示例员工甲", "test-user-001", "测试研发部", "report-001", "已提交");
        when(submissionStatusMapper.selectByWeek(WEEK)).thenReturn(List.of(target));
        Files.writeString(rawReports, """
            [
              {
                "report_id": "report-001",
                "creator_id": "different-test-user",
                "create_time": 100,
                "contents": [
                  {"key": "本周完成成果", "value": "完成虚构交付，access_token: fictional-sensitive-token"},
                  {"key": "下周计划", "value": "继续验证 Bearer fictional-bearer-value"},
                  {"key": "附件", "value": "[{\\"fileId\\":\\"fictional-file-id\\"}]"}
                ]
              }
            ]
            """, StandardCharsets.UTF_8);
        authenticate(List.of("REPORT_ALL"), List.of());

        WeeklyReportDetailVO detail = service.getReport(WEEK, "test-user-001");

        assertThat(detail.isAvailable()).isTrue();
        assertThat(detail.getName()).isEqualTo("示例员工甲");
        assertThat(detail.getFields()).extracting(field -> field.label())
            .containsExactly("本周完成成果", "下周计划");
        assertThat(detail.getFields()).extracting(field -> field.value())
            .allSatisfy(value -> assertThat(value)
                .doesNotContain("fictional-sensitive-token", "fictional-bearer-value", "fictional-file-id"));
    }

    @Test
    void scopedUserCannotBypassServiceLayerWithAnotherUserId() {
        SubmissionStatusPO allowed = row("示例员工甲", "test-user-001", "测试研发部", "report-001", "已提交");
        SubmissionStatusPO denied = row("示例员工乙", "test-user-002", "测试市场部", "report-002", "已提交");
        when(submissionStatusMapper.selectByWeek(WEEK)).thenReturn(List.of(allowed, denied));
        authenticate(List.of("USER"), List.of("USERID:test-user-001"));

        assertThatThrownBy(() -> service.getReport(WEEK, "test-user-002"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("report not found");
    }

    @Test
    void creatorIdFallbackUsesNewestReport() throws IOException {
        SubmissionStatusPO target = row("示例员工甲", "test-user-001", "测试研发部", "missing-report-id", "已提交");
        when(submissionStatusMapper.selectByWeek(WEEK)).thenReturn(List.of(target));
        Files.writeString(rawReports, """
            [
              {
                "report_id": "older-report",
                "creator_id": "test-user-001",
                "create_time": 100,
                "contents": [{"key": "本周完成成果", "value": "旧的虚构内容"}]
              },
              {
                "report_id": "newer-report",
                "creator_id": "test-user-001",
                "modified_time": 200,
                "contents": [{"key": "本周完成成果", "value": "新的虚构内容"}]
              }
            ]
            """, StandardCharsets.UTF_8);
        authenticate(List.of("USER"), List.of("DEPT:测试研发部"));

        WeeklyReportDetailVO detail = service.getReport(WEEK, "test-user-001");

        assertThat(detail.getFields()).singleElement()
            .extracting(field -> field.value())
            .isEqualTo("新的虚构内容");
    }

    @Test
    void missingSubmissionReturnsSmallSafeStateWithoutReadingRawContent() {
        SubmissionStatusPO target = row("示例员工甲", "test-user-001", "测试研发部", "", "未提交");
        when(submissionStatusMapper.selectByWeek(WEEK)).thenReturn(List.of(target));
        authenticate(List.of("REPORT_ALL"), List.of());

        WeeklyReportDetailVO detail = service.getReport(WEEK, "test-user-001");

        assertThat(detail.isAvailable()).isFalse();
        assertThat(detail.getFields()).isEmpty();
        assertThat(detail.getMessage()).isEqualTo("该成员本周未提交，没有可查看的周报原文。");
    }

    @Test
    void oversizedReportIsNotReturnedAsAResponsePayload() throws IOException {
        SubmissionStatusPO target = row("示例员工甲", "test-user-001", "测试研发部", "report-001", "已提交");
        when(submissionStatusMapper.selectByWeek(WEEK)).thenReturn(List.of(target));
        String oversized = "x".repeat(WeeklyReportSourceServiceImpl.MAX_PREVIEW_CHARACTERS + 1);
        new ObjectMapper().writeValue(rawReports.toFile(), List.of(
            java.util.Map.of(
                "report_id", "report-001",
                "creator_id", "test-user-001",
                "contents", List.of(java.util.Map.of("key", "本周完成成果", "value", oversized))
            )
        ));
        authenticate(List.of("REPORT_ALL"), List.of());

        WeeklyReportDetailVO detail = service.getReport(WEEK, "test-user-001");

        assertThat(detail.isAvailable()).isFalse();
        assertThat(detail.getFields()).isEmpty();
        assertThat(detail.getMessage()).contains("内容过大").doesNotContain(oversized);
    }

    @Test
    void malformedRawReportReturnsOnlySafeUnavailableState() throws IOException {
        SubmissionStatusPO target = row("示例员工甲", "test-user-001", "测试研发部", "report-001", "已提交");
        when(submissionStatusMapper.selectByWeek(WEEK)).thenReturn(List.of(target));
        Files.writeString(rawReports, "{fictional-token: 虚构周报正文", StandardCharsets.UTF_8);
        authenticate(List.of("REPORT_ALL"), List.of());

        WeeklyReportDetailVO detail = service.getReport(WEEK, "test-user-001");

        assertThat(detail.isAvailable()).isFalse();
        assertThat(detail.getFields()).isEmpty();
        assertThat(detail.getMessage())
            .isEqualTo("周报原文暂时不可用，请联系开发人员。")
            .doesNotContain("fictional-token", "虚构周报正文");
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

    private SubmissionStatusPO row(
        String name,
        String userId,
        String department,
        String reportId,
        String status
    ) {
        SubmissionStatusPO row = new SubmissionStatusPO();
        row.setName(name);
        row.setUserid(userId);
        row.setDept(department);
        row.setReportDept(department);
        row.setReportId(reportId);
        row.setStatus(status);
        row.setTitle("测试岗位");
        row.setSubmitTime("2026-07-10 10:00:00");
        return row;
    }
}
