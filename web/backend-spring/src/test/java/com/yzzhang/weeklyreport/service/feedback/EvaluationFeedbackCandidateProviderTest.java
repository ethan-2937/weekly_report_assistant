package com.yzzhang.weeklyreport.service.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.mapper.SubmissionStatusMapper;
import com.yzzhang.weeklyreport.mapper.WeekFileMapper;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationFeedbackCandidateProviderTest {
    private static final String WEEK = "2026-W29";

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SubmissionStatusMapper submissionStatusMapper;
    private EvaluationFeedbackCandidateProvider provider;
    private Path weekRoot;
    private Path report;

    @BeforeEach
    void setUp() throws IOException {
        submissionStatusMapper = mock(SubmissionStatusMapper.class);
        WeekFileMapper weekFileMapper = mock(WeekFileMapper.class);
        weekRoot = tempDir.resolve(WEEK);
        report = weekRoot.resolve("summary/manager_report.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "虚构正式管理评价", StandardCharsets.UTF_8);
        when(weekFileMapper.weekDir(WEEK)).thenReturn(weekRoot);
        when(weekFileMapper.managerReportPath(WEEK)).thenReturn(report);
        provider = new EvaluationFeedbackCandidateProvider(
            submissionStatusMapper,
            weekFileMapper,
            objectMapper
        );
    }

    @Test
    void readsValidatedFeedbackForSubmittedUsersOnly() throws Exception {
        when(submissionStatusMapper.selectByWeek(WEEK)).thenReturn(List.of(
            row("示例员工甲", "test-user-001", "已提交"),
            row("示例员工乙", "test-user-002", "未提交")
        ));
        writeArtifacts(List.of(feedback("test-user-001")), digest(report));

        EvaluationFeedbackSnapshot snapshot = provider.collect(WEEK);

        assertThat(snapshot.weekLabel()).isEqualTo(WEEK);
        assertThat(snapshot.employees()).singleElement().satisfies(item -> {
            assertThat(item.userId()).isEqualTo("test-user-001");
            assertThat(item.name()).isEqualTo("示例员工甲");
            assertThat(item.praise()).contains("虚构交付物");
            assertThat(item.improvement()).contains("量化效果");
        });
    }

    @Test
    void rejectsMissingOrExtraFeedbackIdentity() throws Exception {
        when(submissionStatusMapper.selectByWeek(WEEK)).thenReturn(List.of(
            row("示例员工甲", "test-user-001", "已提交")
        ));
        writeArtifacts(List.of(feedback("test-user-002")), digest(report));

        assertThatThrownBy(() -> provider.collect(WEEK))
            .isInstanceOf(EvaluationFeedbackException.class)
            .hasMessage("评价反馈人员范围不一致");
    }

    @Test
    void rejectsTamperedReportAndPrivateDataInFeedback() throws Exception {
        when(submissionStatusMapper.selectByWeek(WEEK)).thenReturn(List.of(
            row("示例员工甲", "test-user-001", "已提交")
        ));
        writeArtifacts(List.of(feedback("test-user-001")), "wrong-report-digest");

        assertThatThrownBy(() -> provider.collect(WEEK))
            .isInstanceOf(EvaluationFeedbackException.class)
            .hasMessage("正式评价反馈校验失败");

        writeArtifacts(List.of(Map.of(
            "userid", "test-user-001",
            "praise", "示例员工甲形成了虚构交付物。",
            "improvement", "建议补充量化效果和明确日期。"
        )), digest(report));
        assertThatThrownBy(() -> provider.collect(WEEK))
            .isInstanceOf(EvaluationFeedbackException.class)
            .hasMessage("评价反馈包含不允许的内容");
    }

    private void writeArtifacts(List<Map<String, String>> feedback, String reportDigest) throws IOException {
        Path automation = weekRoot.resolve("automation");
        Files.createDirectories(automation);
        objectMapper.writeValue(automation.resolve("evaluation_state.json").toFile(), Map.of(
            "weekLabel", WEEK,
            "status", "SUCCESS",
            "inputDigest", "fictional-input-digest",
            "reportDigest", reportDigest
        ));
        objectMapper.writeValue(automation.resolve("employee_feedback.json").toFile(), Map.of(
            "version", 1,
            "weekLabel", WEEK,
            "inputDigest", "fictional-input-digest",
            "reportDigest", reportDigest,
            "feedback", feedback
        ));
    }

    private Map<String, String> feedback(String userId) {
        return Map.of(
            "userid", userId,
            "praise", "本周形成了明确的虚构交付物，证据较完整。",
            "improvement", "建议补充量化效果，并为下周计划写明日期和产出。"
        );
    }

    private SubmissionStatusPO row(String name, String userId, String status) {
        SubmissionStatusPO row = new SubmissionStatusPO();
        row.setName(name);
        row.setUserid(userId);
        row.setStatus(status);
        return row;
    }

    private String digest(Path path) throws Exception {
        byte[] value = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return HexFormat.of().formatHex(value);
    }
}
