package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.common.BizException;
import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient;
import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient.PersonalNotice;
import com.yzzhang.weeklyreport.service.notification.FeedbackRecipientResolver;
import com.yzzhang.weeklyreport.vo.NotificationTestRequestVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationTestServiceImplTest {
    private final FeedbackRecipientResolver recipientResolver = mock(FeedbackRecipientResolver.class);
    private final DingTalkWorkNoticeClient workNoticeClient = mock(DingTalkWorkNoticeClient.class);
    private final NotificationTestServiceImpl service = new NotificationTestServiceImpl(
        recipientResolver,
        workNoticeClient
    );

    @Test
    void sendsSundaySampleThroughBatchMarkdownWithoutRealSubmissionClaims() {
        when(recipientResolver.resolve()).thenReturn(recipient("测试接收人"));

        var result = service.send(request("SUNDAY_REMINDER", "测试接收人"));

        verify(workNoticeClient).sendMarkdown(
            eq("周报提交提醒（测试）"),
            contains("不代表您未提交周报"),
            eq(List.of("test-user-001"))
        );
        assertThat(result.delivered()).isTrue();
        assertThat(result.targetName()).isEqualTo("测试接收人");
    }

    @Test
    void sendsMondaySampleThroughPersonalMarkdown() {
        when(recipientResolver.resolve()).thenReturn(recipient("测试接收人"));
        ArgumentCaptor<List<PersonalNotice>> notices = ArgumentCaptor.forClass(List.class);

        service.send(request("MONDAY_EVALUATION", "测试接收人"));

        verify(workNoticeClient).sendPersonalMarkdown(notices.capture(), any(IntConsumer.class));
        assertThat(notices.getValue()).hasSize(1);
        assertThat(notices.getValue().getFirst().userId()).isEqualTo("test-user-001");
        assertThat(notices.getValue().getFirst().markdown())
            .contains("不包含真实周报评价", "感谢您", "团队因您");
    }

    @Test
    void rejectsNameMismatchOrMultipleRecipientsBeforeSending() {
        when(recipientResolver.resolve()).thenReturn(
            new FeedbackRecipientResolver.Recipient(
                "测试接收人",
                List.of("test-user-001", "test-user-002")
            )
        );

        assertThatThrownBy(() -> service.send(request("SUNDAY_REMINDER", "其他接收人")))
            .isInstanceOf(BizException.class);
        verify(workNoticeClient, never()).sendMarkdown(any(), any(), any());
        verify(workNoticeClient, never()).sendPersonalMarkdown(any(), any());
    }

    private FeedbackRecipientResolver.Recipient recipient(String name) {
        return new FeedbackRecipientResolver.Recipient(name, List.of("test-user-001"));
    }

    private NotificationTestRequestVO request(String type, String recipientName) {
        NotificationTestRequestVO request = new NotificationTestRequestVO();
        request.setType(type);
        request.setConfirmRecipientName(recipientName);
        return request;
    }
}
