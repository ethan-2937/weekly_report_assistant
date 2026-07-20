package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.config.EvaluationFeedbackProperties;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackCandidateProvider;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackException;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackRunStore;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackRunStore.RunState;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackSnapshot;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackSnapshot.EmployeeFeedback;
import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient;
import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient.PersonalNotice;
import com.yzzhang.weeklyreport.service.notification.FeedbackRecipientResolver;
import com.yzzhang.weeklyreport.service.notification.WorkNoticeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;

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

class EvaluationFeedbackServiceTest {
    private EvaluationFeedbackCandidateProvider candidateProvider;
    private EvaluationFeedbackRunStore runStore;
    private DingTalkWorkNoticeClient workNoticeClient;
    private EvaluationFeedbackService service;

    @BeforeEach
    void setUp() {
        candidateProvider = mock(EvaluationFeedbackCandidateProvider.class);
        runStore = mock(EvaluationFeedbackRunStore.class);
        workNoticeClient = mock(DingTalkWorkNoticeClient.class);
        FeedbackRecipientResolver recipientResolver = mock(FeedbackRecipientResolver.class);
        when(recipientResolver.resolve()).thenReturn(
            new FeedbackRecipientResolver.Recipient("示例管理员", List.of("test-admin-001"))
        );
        when(runStore.load(anyString())).thenReturn(Optional.empty());
        when(runStore.state(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
            .thenAnswer(invocation -> new RunState(
                invocation.getArgument(0),
                invocation.getArgument(1),
                invocation.getArgument(2),
                invocation.getArgument(3),
                "2026-07-20T04:00:00Z"
            ));
        org.mockito.Mockito.doAnswer(invocation -> {
            List<PersonalNotice> notices = invocation.getArgument(0);
            IntConsumer callback = invocation.getArgument(1);
            callback.accept(notices.size());
            return null;
        }).when(workNoticeClient).sendPersonalMarkdown(anyList(), any(IntConsumer.class));
        EvaluationFeedbackProperties properties = new EvaluationFeedbackProperties();
        properties.setHrContactName("示例HR联系人");
        Clock clock = Clock.fixed(Instant.parse("2026-07-20T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new EvaluationFeedbackService(
            candidateProvider,
            runStore,
            workNoticeClient,
            recipientResolver,
            properties,
            clock
        );
    }

    @Test
    void sendsPrivatePraiseThenImprovementAndConfiguredHrFooter() {
        when(candidateProvider.collect("2026-W29")).thenReturn(snapshot());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PersonalNotice>> notices = ArgumentCaptor.forClass(List.class);

        service.runOnce();

        verify(workNoticeClient).sendPersonalMarkdown(notices.capture(), any(IntConsumer.class));
        assertThat(notices.getValue()).singleElement().satisfies(notice -> {
            assertThat(notice.title()).isEqualTo("周报个人评价");
            assertThat(notice.userId()).isEqualTo("test-user-001");
        });
        assertThat(notices.getValue().get(0).markdown())
            .contains(
                "示例员工甲",
                "做得好的地方",
                "建议重点改进",
                "感谢您本周通过形成明确交付物推动工作落地",
                "团队因您的认真投入而更加稳健",
                "如有疑问请联系HR示例HR联系人"
            )
            .satisfies(message -> assertThat(message.indexOf("做得好的地方"))
                .isLessThan(message.indexOf("建议重点改进")))
            .satisfies(message -> assertThat(message.indexOf("建议重点改进"))
                .isLessThan(message.indexOf("感谢您")))
            .satisfies(message -> assertThat(message.indexOf("感谢您"))
                .isLessThan(message.indexOf("如有疑问请联系HR")))
            .doesNotContain("test-user-001");
        verify(workNoticeClient).sendMarkdown(
            eq("周报个人评价通知：成功"),
            anyString(),
            eq(List.of("test-admin-001"))
        );
        verify(runStore).save(new RunState("2026-W29", "COMPLETE", 1, 1, "2026-07-20T04:00:00Z"));
    }

    @Test
    void sourceFailureSendsSafeAdministratorResultWithoutEmployeeMessage() {
        when(candidateProvider.collect("2026-W29"))
            .thenThrow(new EvaluationFeedbackException("fictional-token 虚构评价正文"));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);

        service.runOnce();

        verify(workNoticeClient).sendMarkdown(
            eq("周报个人评价通知：失败"),
            body.capture(),
            eq(List.of("test-admin-001"))
        );
        assertThat(body.getValue())
            .contains("正式评价未就绪或校验失败")
            .doesNotContain("fictional-token", "虚构评价正文");
        verify(workNoticeClient, never()).sendPersonalMarkdown(anyList(), any(IntConsumer.class));
    }

    @Test
    void uncertainEmployeeDeliveryIsRecordedAndNeverAutomaticallyRetried() {
        when(candidateProvider.collect("2026-W29")).thenReturn(snapshot());
        doThrow(new WorkNoticeException("远程结果未知"))
            .when(workNoticeClient)
            .sendPersonalMarkdown(anyList(), any(IntConsumer.class));

        service.runOnce();

        verify(runStore).save(new RunState("2026-W29", "UNKNOWN", 1, 0, "2026-07-20T04:00:00Z"));
        verify(workNoticeClient).sendMarkdown(
            eq("周报个人评价通知：失败"),
            anyString(),
            eq(List.of("test-admin-001"))
        );
    }

    @Test
    void completedOrPartiallySentWeekDoesNotSendDuplicateEmployeeFeedback() {
        when(candidateProvider.collect("2026-W29")).thenReturn(snapshot());
        when(runStore.load("2026-W29")).thenReturn(Optional.of(
            new RunState("2026-W29", "COMPLETE", 1, 1, "2026-07-20T04:00:00Z")
        ));

        service.runOnce();

        verify(workNoticeClient, never()).sendMarkdown(anyString(), anyString(), org.mockito.ArgumentMatchers.anyList());

        when(runStore.load("2026-W29")).thenReturn(Optional.of(
            new RunState("2026-W29", "EMPLOYEE_SENDING", 2, 1, "2026-07-20T04:00:00Z")
        ));
        service.runOnce();
        verify(workNoticeClient, never()).sendPersonalMarkdown(anyList(), any(IntConsumer.class));
        verify(workNoticeClient, times(1)).sendMarkdown(
            eq("周报个人评价通知：失败"),
            anyString(),
            eq(List.of("test-admin-001"))
        );
    }

    private EvaluationFeedbackSnapshot snapshot() {
        return new EvaluationFeedbackSnapshot("2026-W29", List.of(
            new EmployeeFeedback(
                "test-user-001",
                "示例员工甲",
                "本周形成了明确的虚构交付物。",
                "建议补充量化效果和明确日期。",
                "感谢您本周通过形成明确交付物推动工作落地。团队因您的认真投入而更加稳健，也更有力量。"
            )
        ));
    }
}
