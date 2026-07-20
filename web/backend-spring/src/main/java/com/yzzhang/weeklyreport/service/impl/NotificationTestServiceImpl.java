package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.common.BizException;
import com.yzzhang.weeklyreport.service.NotificationTestService;
import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient;
import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient.PersonalNotice;
import com.yzzhang.weeklyreport.service.notification.FeedbackRecipientResolver;
import com.yzzhang.weeklyreport.vo.NotificationTestRequestVO;
import com.yzzhang.weeklyreport.vo.NotificationTestResultVO;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class NotificationTestServiceImpl implements NotificationTestService {
    static final String SUNDAY_REMINDER = "SUNDAY_REMINDER";
    static final String MONDAY_EVALUATION = "MONDAY_EVALUATION";

    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FeedbackRecipientResolver recipientResolver;
    private final DingTalkWorkNoticeClient workNoticeClient;

    public NotificationTestServiceImpl(
        FeedbackRecipientResolver recipientResolver,
        DingTalkWorkNoticeClient workNoticeClient
    ) {
        this.recipientResolver = recipientResolver;
        this.workNoticeClient = workNoticeClient;
    }

    @Override
    public NotificationTestResultVO send(NotificationTestRequestVO request) {
        FeedbackRecipientResolver.Recipient recipient = confirmedRecipient(request.getConfirmRecipientName());
        String type = request.getType();
        if (SUNDAY_REMINDER.equals(type)) {
            workNoticeClient.sendMarkdown(
                "周报提交提醒（测试）",
                sundayTestMessage(),
                recipient.userIds()
            );
        } else if (MONDAY_EVALUATION.equals(type)) {
            workNoticeClient.sendPersonalMarkdown(
                List.of(new PersonalNotice(
                    "周报个人评价（测试）",
                    mondayTestMessage(recipient.name()),
                    recipient.userIds().getFirst()
                )),
                ignored -> { }
            );
        } else {
            throw new BizException("unsupported notification test type");
        }
        return new NotificationTestResultVO(type, true, recipient.name(), "测试通知已提交到钉钉");
    }

    private FeedbackRecipientResolver.Recipient confirmedRecipient(String confirmedName) {
        FeedbackRecipientResolver.Recipient recipient = recipientResolver.resolve();
        String expected = confirmedName == null ? "" : confirmedName.trim();
        if (!expected.equals(recipient.name()) || recipient.userIds().size() != 1) {
            throw new BizException("notification test recipient confirmation failed");
        }
        return recipient;
    }

    private String sundayTestMessage() {
        return """
            ### 周报提交提醒（测试）

            > 这是系统管理员发起的发送链路测试，不代表您未提交周报。

            - **测试内容**：周日未交提醒消息格式与批量发送接口
            - **正式任务**：每周日 18:00（Asia/Shanghai）
            - **测试时间**：%s
            - **正式状态**：本次试发不读取提交数据，也不写正式提醒状态

            收到本消息表示钉钉凭据、应用可见范围和周日提醒发送链路可用。
            """.formatted(nowText()).strip();
    }

    private String mondayTestMessage(String recipientName) {
        return """
            ### %s，周报个人评价通知（测试）

            > 这是系统管理员发起的发送链路测试，不包含真实周报评价。

            #### 做得好的地方

            测试消息已通过逐人发送链路生成。

            #### 建议重点改进

            正式内容将在评价文件通过覆盖、摘要和隐私校验后发送。

            感谢您参与本次通知链路验证。团队因您的认真确认而更有保障，也更有信心。

            - **正式任务**：每周一 12:00（Asia/Shanghai）
            - **测试时间**：%s
            - **正式状态**：本次试发不读取评价文件，也不写正式通知状态

            收到本消息表示钉钉凭据、应用可见范围和周一逐人发送链路可用。
            """.formatted(recipientName, nowText()).strip();
    }

    private String nowText() {
        return DISPLAY_TIME.format(ZonedDateTime.now(CN_ZONE));
    }
}
