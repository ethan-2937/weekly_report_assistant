package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient;
import com.yzzhang.weeklyreport.service.notification.FeedbackRecipientResolver;
import com.yzzhang.weeklyreport.service.notification.WorkNoticeException;
import com.yzzhang.weeklyreport.service.reminder.ReminderCandidateProvider;
import com.yzzhang.weeklyreport.service.reminder.ReminderCandidateSnapshot;
import com.yzzhang.weeklyreport.service.reminder.ReminderRunStore;
import com.yzzhang.weeklyreport.service.reminder.ReminderRunStore.ReminderRunState;
import com.yzzhang.weeklyreport.service.reminder.SubmissionReminderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionReminderServiceTest {
    private ReminderCandidateProvider candidateProvider;
    private ReminderRunStore runStore;
    private DingTalkWorkNoticeClient workNoticeClient;
    private FeedbackRecipientResolver recipientResolver;
    private SubmissionReminderService service;

    @BeforeEach
    void setUp() {
        candidateProvider = mock(ReminderCandidateProvider.class);
        runStore = mock(ReminderRunStore.class);
        workNoticeClient = mock(DingTalkWorkNoticeClient.class);
        recipientResolver = mock(FeedbackRecipientResolver.class);
        when(recipientResolver.resolve()).thenReturn(
            new FeedbackRecipientResolver.Recipient("示例管理员", List.of("test-admin-001"))
        );
        when(runStore.load(anyString())).thenReturn(Optional.empty());
        when(runStore.state(any(), anyString())).thenAnswer(invocation -> {
            ReminderCandidateSnapshot snapshot = invocation.getArgument(0);
            String phase = invocation.getArgument(1);
            return state(snapshot, phase);
        });
        service = new SubmissionReminderService(candidateProvider, runStore, workNoticeClient, recipientResolver);
    }

    @Test
    void remindsMissingUsersAndAlwaysSendsAdministratorSuccessSummary() {
        ReminderCandidateSnapshot snapshot = snapshot(List.of("test-user-002"), 2, 1);
        when(candidateProvider.collect()).thenReturn(snapshot);

        service.runOnce();

        ArgumentCaptor<String> employeeBody = ArgumentCaptor.forClass(String.class);
        verify(workNoticeClient).sendMarkdown(
            eq("周报提交提醒"),
            employeeBody.capture(),
            eq(List.of("test-user-002"))
        );
        assertThat(employeeBody.getValue())
            .contains("如有其他疑问请联系张艺政")
            .doesNotContain("补交说明", "周一至周三提交仍归入本业务周");
        ArgumentCaptor<String> adminBody = ArgumentCaptor.forClass(String.class);
        verify(workNoticeClient).sendMarkdown(
            eq("周报提醒自动检测：成功"),
            adminBody.capture(),
            eq(List.of("test-admin-001"))
        );
        assertThat(adminBody.getValue())
            .contains("任务执行**：成功", "未交人数**：1")
            .doesNotContain("test-user-002");
    }

    @Test
    void noMissingUsersStillProducesAdministratorSuccessNotification() {
        when(candidateProvider.collect()).thenReturn(snapshot(List.of(), 2, 2));

        service.runOnce();

        verify(workNoticeClient, times(1)).sendMarkdown(
            eq("周报提醒自动检测：成功"),
            anyString(),
            eq(List.of("test-admin-001"))
        );
        verify(workNoticeClient, never()).sendMarkdown(eq("周报提交提醒"), anyString(), anyList());
    }

    @Test
    void collectionFailureSendsSanitizedAdministratorFailureWithoutEmployeeNotice() {
        when(candidateProvider.collect()).thenThrow(
            new SubmissionReminderException("采集失败 access_token=fictional-sensitive-token")
        );
        ArgumentCaptor<String> adminBody = ArgumentCaptor.forClass(String.class);

        service.runOnce();

        verify(workNoticeClient).sendMarkdown(
            eq("周报提醒自动检测：失败"),
            adminBody.capture(),
            eq(List.of("test-admin-001"))
        );
        assertThat(adminBody.getValue())
            .contains("任务执行**：失败", "access_token=***")
            .doesNotContain("fictional-sensitive-token");
        verify(workNoticeClient, never()).sendMarkdown(eq("周报提交提醒"), anyString(), anyList());
    }

    @Test
    void uncertainEmployeeDeliveryIsNotRetriedAndAdministratorGetsFailure() {
        ReminderCandidateSnapshot snapshot = snapshot(List.of("test-user-002"), 2, 1);
        when(candidateProvider.collect()).thenReturn(snapshot);
        doThrow(new WorkNoticeException("钉钉工作通知请求失败"))
            .when(workNoticeClient)
            .sendMarkdown(eq("周报提交提醒"), anyString(), eq(List.of("test-user-002")));

        service.runOnce();

        verify(workNoticeClient).sendMarkdown(
            eq("周报提醒自动检测：失败"),
            anyString(),
            eq(List.of("test-admin-001"))
        );
        verify(runStore).save(state(snapshot, SubmissionReminderService.PHASE_UNKNOWN));
    }

    @Test
    void completedWeekDoesNotSendDuplicateNotifications() {
        ReminderCandidateSnapshot snapshot = snapshot(List.of("test-user-002"), 2, 1);
        when(candidateProvider.collect()).thenReturn(snapshot);
        when(runStore.load("2026-W29")).thenReturn(Optional.of(state(snapshot, SubmissionReminderService.PHASE_COMPLETE)));

        service.runOnce();

        verify(workNoticeClient, never()).sendMarkdown(anyString(), anyString(), anyList());
    }

    private ReminderCandidateSnapshot snapshot(List<String> missing, int expected, int submitted) {
        return new ReminderCandidateSnapshot(
            "2026-W29",
            "2026-07-19T18:00:00+08:00",
            expected,
            submitted,
            missing,
            0
        );
    }

    private ReminderRunState state(ReminderCandidateSnapshot snapshot, String phase) {
        return new ReminderRunState(
            snapshot.weekLabel(),
            phase,
            snapshot.expectedCount(),
            snapshot.submittedCount(),
            snapshot.missingUserIds().size(),
            "2026-07-19T10:00:00Z"
        );
    }
}
