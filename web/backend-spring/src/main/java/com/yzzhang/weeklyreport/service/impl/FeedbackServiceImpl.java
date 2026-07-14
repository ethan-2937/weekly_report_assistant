package com.yzzhang.weeklyreport.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.common.BizException;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import com.yzzhang.weeklyreport.security.AuthenticatedUser;
import com.yzzhang.weeklyreport.service.FeedbackService;
import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient;
import com.yzzhang.weeklyreport.service.notification.FeedbackRecipientResolver;
import com.yzzhang.weeklyreport.service.notification.WorkNoticeException;
import com.yzzhang.weeklyreport.vo.FeedbackRequestVO;
import com.yzzhang.weeklyreport.vo.FeedbackResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FeedbackServiceImpl implements FeedbackService {
    private static final ZoneId CN_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TICKET_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(CN_ZONE);
    private static final DateTimeFormatter LOG_MONTH = DateTimeFormatter.ofPattern("yyyy-MM").withZone(CN_ZONE);
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(CN_ZONE);

    private final WeeklyReportProperties properties;
    private final ProjectPathConfig pathConfig;
    private final ObjectMapper objectMapper;
    private final FeedbackRecipientResolver recipientResolver;
    private final DingTalkWorkNoticeClient workNoticeClient;
    private final Object logLock = new Object();

    public FeedbackServiceImpl(
        WeeklyReportProperties properties,
        ProjectPathConfig pathConfig,
        ObjectMapper objectMapper,
        FeedbackRecipientResolver recipientResolver,
        DingTalkWorkNoticeClient workNoticeClient
    ) {
        this.properties = properties;
        this.pathConfig = pathConfig;
        this.objectMapper = objectMapper;
        this.recipientResolver = recipientResolver;
        this.workNoticeClient = workNoticeClient;
    }

    @Override
    public FeedbackResponseVO submit(FeedbackRequestVO requestVO, HttpServletRequest request) {
        AuthenticatedUser user = currentAuthenticatedUser();
        WeeklyReportProperties.Feedback feedback = properties.getFeedback();
        FeedbackRecipientResolver.Recipient recipient = recipientResolver.resolve();
        String targetName = recipient.name();
        String ticketNo = ticketNo();
        Instant now = Instant.now();
        String copyText = plainText(ticketNo, now, user, requestVO, request, targetName);

        DeliveryResult delivery = deliver(feedback, recipient, requestVO, ticketNo, now, user, copyText, targetName);
        writeLog(ticketNo, now, user, requestVO, request, targetName, delivery, copyText);

        if (delivery.delivered()) {
            return new FeedbackResponseVO(
                ticketNo,
                true,
                "已通过钉钉发送给" + targetName + "，反馈编号：" + ticketNo,
                targetName,
                copyText
            );
        }
        return new FeedbackResponseVO(
            ticketNo,
            false,
            "已记录反馈，但钉钉消息未直接发送：" + delivery.message() + "。请复制内容在钉钉中联系" + targetName + "。",
            targetName,
            copyText
        );
    }

    private DeliveryResult deliver(
        WeeklyReportProperties.Feedback feedback,
        FeedbackRecipientResolver.Recipient recipient,
        FeedbackRequestVO requestVO,
        String ticketNo,
        Instant now,
        AuthenticatedUser user,
        String copyText,
        String targetName
    ) {
        if (!feedback.isEnabled()) {
            return new DeliveryResult(false, "反馈通知未启用");
        }

        if (recipient.userIds().isEmpty()) {
            return new DeliveryResult(false, "未配置" + targetName + "的钉钉 userId");
        }

        try {
            workNoticeClient.sendMarkdown(
                "周报系统反馈",
                markdownText(ticketNo, now, user, requestVO, copyText, targetName),
                recipient.userIds()
            );
            return new DeliveryResult(true, "钉钉消息发送成功");
        } catch (WorkNoticeException ex) {
            return new DeliveryResult(false, ex.getMessage());
        }
    }

    private void writeLog(
        String ticketNo,
        Instant now,
        AuthenticatedUser user,
        FeedbackRequestVO requestVO,
        HttpServletRequest request,
        String targetName,
        DeliveryResult delivery,
        String copyText
    ) {
        try {
            Path logDir = pathConfig.projectRoot().resolve("logs").normalize();
            Files.createDirectories(logDir);
            Path logFile = logDir.resolve("feedback-" + LOG_MONTH.format(now) + ".jsonl");
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("ticketNo", ticketNo);
            record.put("createdAt", now.toString());
            record.put("targetName", targetName);
            record.put("delivered", delivery.delivered());
            record.put("deliveryMessage", delivery.message());
            record.put("userId", user.getId());
            record.put("username", user.getUsername());
            record.put("realName", user.getRealName());
            record.put("category", clean(requestVO.getCategory()));
            record.put("urgency", clean(requestVO.getUrgency()));
            record.put("title", feedbackTitle(requestVO));
            record.put("detail", clean(requestVO.getDetail()));
            record.put("expectation", clean(requestVO.getExpectation()));
            record.put("week", clean(requestVO.getWeek()));
            record.put("view", clean(requestVO.getView()));
            record.put("pageUrl", clean(requestVO.getPageUrl()));
            record.put("ip", clientIp(request));
            record.put("userAgent", clean(requestVO.getUserAgent()));
            record.put("copyText", copyText);
            String line = objectMapper.writeValueAsString(record) + System.lineSeparator();
            synchronized (logLock) {
                Files.writeString(logFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException ignored) {
            // Feedback delivery should not be blocked by local audit-log failures.
        }
    }

    private String plainText(
        String ticketNo,
        Instant now,
        AuthenticatedUser user,
        FeedbackRequestVO requestVO,
        HttpServletRequest request,
        String targetName
    ) {
        return """
            【周报系统反馈】
            编号：%s
            接收人：%s
            提交时间：%s
            提交人：%s（%s）
            类型：%s
            紧急程度：%s
            当前周次：%s
            当前页面：%s
            页面视图：%s

            标题：
            %s

            详细描述：
            %s

            复现步骤 / 期望效果：
            %s

            浏览器信息：
            %s
            IP：%s
            """.formatted(
            ticketNo,
            targetName,
            DISPLAY_TIME.format(now),
            firstText(user.getRealName(), user.getUsername()),
            user.getUsername(),
            categoryLabel(requestVO.getCategory()),
            urgencyLabel(requestVO.getUrgency()),
            firstText(clean(requestVO.getWeek()), "未选择"),
            firstText(clean(requestVO.getPageUrl()), "未提供"),
            firstText(clean(requestVO.getView()), "未提供"),
            feedbackTitle(requestVO),
            clean(requestVO.getDetail()),
            firstText(clean(requestVO.getExpectation()), "未填写"),
            firstText(clean(requestVO.getUserAgent()), "未提供"),
            clientIp(request)
        ).strip();
    }

    private String markdownText(
        String ticketNo,
        Instant now,
        AuthenticatedUser user,
        FeedbackRequestVO requestVO,
        String copyText,
        String targetName
    ) {
        String markdown = """
            ### 周报系统反馈
            - **编号**：%s
            - **接收人**：%s
            - **提交时间**：%s
            - **提交人**：%s（%s）
            - **类型**：%s
            - **紧急程度**：%s
            - **周次/页面**：%s / %s

            **标题**

            %s

            **详细描述**

            %s

            **复现步骤 / 期望效果**

            %s
            """.formatted(
            ticketNo,
            targetName,
            DISPLAY_TIME.format(now),
            firstText(user.getRealName(), user.getUsername()),
            user.getUsername(),
            categoryLabel(requestVO.getCategory()),
            urgencyLabel(requestVO.getUrgency()),
            firstText(clean(requestVO.getWeek()), "未选择"),
            firstText(clean(requestVO.getPageUrl()), "未提供"),
            feedbackTitle(requestVO),
            clean(requestVO.getDetail()),
            firstText(clean(requestVO.getExpectation()), "未填写")
        ).strip();

        if (markdown.length() <= 5600) {
            return markdown;
        }
        return markdown.substring(0, 5400) + "\n\n内容较长，原始文本请查看服务器 feedback 日志或复制文本：\n\n" + copyText.substring(0, Math.min(copyText.length(), 400));
    }

    private AuthenticatedUser currentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BizException("请先登录");
        }
        return user;
    }

    private String ticketNo() {
        return "FB-" + TICKET_TIME.format(Instant.now()) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private String categoryLabel(String value) {
        return switch (String.valueOf(value).toUpperCase(Locale.ROOT)) {
            case "BUG" -> "Bug / 显示异常";
            case "SUGGESTION" -> "功能建议";
            case "DATA" -> "数据问题";
            case "PERMISSION" -> "权限问题";
            default -> "其他";
        };
    }

    private String feedbackTitle(FeedbackRequestVO requestVO) {
        String title = clean(requestVO.getTitle());
        if (hasText(title)) {
            return title;
        }
        String detail = clean(requestVO.getDetail()).replace('\n', ' ');
        if (detail.length() > 42) {
            detail = detail.substring(0, 42) + "...";
        }
        return categoryLabel(requestVO.getCategory()) + "：" + firstText(detail, "周报系统反馈");
    }

    private String urgencyLabel(String value) {
        return switch (String.valueOf(value).toUpperCase(Locale.ROOT)) {
            case "HIGH" -> "较急";
            case "URGENT" -> "紧急";
            default -> "普通";
        };
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim().replace("\r\n", "\n").replace('\r', '\n');
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record DeliveryResult(boolean delivered, String message) {
    }
}
