package com.yzzhang.weeklyreport.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.common.BizException;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import com.yzzhang.weeklyreport.mapper.SysUserMapper;
import com.yzzhang.weeklyreport.po.SysUserPO;
import com.yzzhang.weeklyreport.security.AuthenticatedUser;
import com.yzzhang.weeklyreport.service.FeedbackService;
import com.yzzhang.weeklyreport.vo.FeedbackRequestVO;
import com.yzzhang.weeklyreport.vo.FeedbackResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Object logLock = new Object();

    public FeedbackServiceImpl(
        WeeklyReportProperties properties,
        ProjectPathConfig pathConfig,
        SysUserMapper sysUserMapper,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.pathConfig = pathConfig;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public FeedbackResponseVO submit(FeedbackRequestVO requestVO, HttpServletRequest request) {
        AuthenticatedUser user = currentAuthenticatedUser();
        WeeklyReportProperties.Feedback feedback = properties.getFeedback();
        Map<String, String> envFile = readEnvFile(feedback);
        String targetName = resolveRecipientName(feedback, envFile);
        String ticketNo = ticketNo();
        Instant now = Instant.now();
        String copyText = plainText(ticketNo, now, user, requestVO, request, targetName);

        DeliveryResult delivery = deliver(feedback, envFile, requestVO, ticketNo, now, user, copyText, targetName);
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
        Map<String, String> envFile,
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

        String userIds = resolveDingTalkUserIds(feedback, envFile, targetName);
        if (!hasText(userIds)) {
            return new DeliveryResult(false, "未配置" + targetName + "的钉钉 userId");
        }

        String appKey = resolveConfig(
            envFile,
            feedback.getDingtalkAppKey(),
            "WEEKLY_FEEDBACK_DINGTALK_APP_KEY",
            "DINGTALK_APP_KEY"
        );
        String appSecret = resolveConfig(
            envFile,
            feedback.getDingtalkAppSecret(),
            "WEEKLY_FEEDBACK_DINGTALK_APP_SECRET",
            "DINGTALK_APP_SECRET"
        );
        String agentId = resolveConfig(
            envFile,
            feedback.getDingtalkAgentId(),
            "WEEKLY_FEEDBACK_DINGTALK_AGENT_ID",
            "DINGTALK_AGENT_ID"
        );
        if (!hasText(appKey) || !hasText(appSecret) || !hasText(agentId)) {
            return new DeliveryResult(false, "缺少钉钉应用 AppKey、AppSecret 或 AgentId 配置");
        }

        Long parsedAgentId;
        try {
            parsedAgentId = Long.parseLong(agentId.trim());
        } catch (NumberFormatException ex) {
            return new DeliveryResult(false, "钉钉 AgentId 不是有效数字");
        }

        try {
            String accessToken = fetchAccessToken(feedback, appKey, appSecret);
            sendWorkNotice(feedback, accessToken, parsedAgentId, userIds, markdownText(ticketNo, now, user, requestVO, copyText, targetName));
            return new DeliveryResult(true, "钉钉消息发送成功");
        } catch (RuntimeException ex) {
            return new DeliveryResult(false, safeFailureMessage(ex));
        }
    }

    private String fetchAccessToken(WeeklyReportProperties.Feedback feedback, String appKey, String appSecret) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("appKey", appKey);
        payload.put("appSecret", appSecret);
        ResponseEntity<Map> response = restTemplate.postForEntity(feedback.getAccessTokenUrl(), payload, Map.class);
        String accessToken = stringValueDeep(response.getBody(), List.of("accessToken", "access_token"));
        if (!hasText(accessToken)) {
            throw new BizException("未获取到钉钉 accessToken");
        }
        return accessToken;
    }

    private void sendWorkNotice(
        WeeklyReportProperties.Feedback feedback,
        String accessToken,
        Long agentId,
        String userIds,
        String markdown
    ) {
        URI uri = UriComponentsBuilder.fromUriString(feedback.getAsyncSendUrl())
            .queryParam("access_token", accessToken)
            .build(true)
            .toUri();

        Map<String, Object> markdownBody = new HashMap<>();
        markdownBody.put("title", "周报系统反馈");
        markdownBody.put("text", markdown);

        Map<String, Object> msg = new HashMap<>();
        msg.put("msgtype", "markdown");
        msg.put("markdown", markdownBody);

        Map<String, Object> payload = new HashMap<>();
        payload.put("agent_id", agentId);
        payload.put("userid_list", userIds);
        payload.put("to_all_user", false);
        payload.put("msg", msg);

        ResponseEntity<Map> response = restTemplate.postForEntity(uri, payload, Map.class);
        Map body = response.getBody();
        Object errcode = body == null ? null : body.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            throw new BizException("钉钉消息发送失败：" + firstText(String.valueOf(body.get("errmsg")), String.valueOf(body)));
        }
    }

    private String resolveDingTalkUserIds(
        WeeklyReportProperties.Feedback feedback,
        Map<String, String> envFile,
        String targetName
    ) {
        String configured = resolveConfig(
            envFile,
            feedback.getDingtalkUserIds(),
            "WEEKLY_FEEDBACK_DINGTALK_USER_IDS",
            "FEEDBACK_DINGTALK_USER_IDS"
        );
        if (hasText(configured)) {
            return normalizeUserIds(configured);
        }

        String fromLocalUser = userIdFromLocalUser(targetName);
        if (hasText(fromLocalUser)) {
            return fromLocalUser;
        }

        String fromContacts = userIdFromContacts(targetName);
        if (hasText(fromContacts)) {
            return fromContacts;
        }
        return "";
    }

    private String resolveRecipientName(WeeklyReportProperties.Feedback feedback, Map<String, String> envFile) {
        return firstText(
            System.getenv("WEEKLY_FEEDBACK_RECIPIENT_NAME"),
            System.getenv("FEEDBACK_DINGTALK_RECIPIENT_NAME"),
            envFile.get("WEEKLY_FEEDBACK_RECIPIENT_NAME"),
            envFile.get("FEEDBACK_DINGTALK_RECIPIENT_NAME"),
            feedback.getRecipientName(),
            "张艺政"
        );
    }

    private String userIdFromLocalUser(String targetName) {
        try {
            List<String> dingUserIds = sysUserMapper.findActiveByRealName(targetName).stream()
                .map(SysUserPO::getDingUserId)
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
            return dingUserIds.size() == 1 ? dingUserIds.get(0) : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private String userIdFromContacts(String targetName) {
        Path contacts = pathConfig.outputRoot().resolve("contacts").resolve("users.json");
        if (!Files.exists(contacts)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(contacts.toFile());
            if (!root.isArray()) {
                return "";
            }
            List<String> matches = new ArrayList<>();
            for (JsonNode node : root) {
                if (targetName.equals(text(node, "name")) && hasText(text(node, "userid"))) {
                    matches.add(text(node, "userid").trim());
                }
            }
            return matches.stream().distinct().count() == 1 ? matches.get(0) : "";
        } catch (IOException ignored) {
            return "";
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

    private Map<String, String> readEnvFile(WeeklyReportProperties.Feedback feedback) {
        Path envPath = envPath(feedback);
        if (!Files.exists(envPath)) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        try {
            for (String rawLine : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                String line = rawLine.strip();
                if (line.startsWith("\ufeff")) {
                    line = line.substring(1).strip();
                }
                if (line.isBlank() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                values.put(parts[0].trim(), trimQuotes(parts[1].trim()));
            }
        } catch (IOException ignored) {
            return Map.of();
        }
        return values;
    }

    private Path envPath(WeeklyReportProperties.Feedback feedback) {
        if (hasText(feedback.getEnvPath())) {
            Path configured = Path.of(feedback.getEnvPath());
            if (configured.isAbsolute()) {
                return configured.normalize();
            }
            return pathConfig.projectRoot().resolve(configured).normalize();
        }
        return pathConfig.projectRoot().resolve("config").resolve(".env").normalize();
    }

    private String resolveConfig(Map<String, String> envFile, String propertyValue, String weeklyKey, String legacyKey) {
        return firstText(
            propertyValue,
            System.getenv(weeklyKey),
            System.getenv(legacyKey),
            envFile.get(weeklyKey),
            envFile.get(legacyKey)
        );
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

    private String normalizeUserIds(String value) {
        return String.join(
            ",",
            List.of(value.split("[,，;；\\s]+")).stream()
                .map(String::trim)
                .filter(this::hasText)
                .distinct()
                .toList()
        );
    }

    @SuppressWarnings("unchecked")
    private String stringValueDeep(Map map, List<String> keys) {
        if (map == null) {
            return "";
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && hasText(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        for (Object value : map.values()) {
            if (value instanceof Map nested) {
                String nestedValue = stringValueDeep(nested, keys);
                if (hasText(nestedValue)) {
                    return nestedValue;
                }
            }
        }
        return "";
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

    private String safeFailureMessage(RuntimeException ex) {
        String message;
        if (ex instanceof RestClientResponseException responseException) {
            message = "钉钉接口返回 HTTP " + responseException.getStatusCode().value();
        } else {
            message = ex.getMessage();
        }
        if (!hasText(message)) {
            message = ex.getClass().getSimpleName();
        }
        return message
            .replaceAll("access_token=[^&\\s]+", "access_token=***")
            .replaceAll("appSecret[=:][^,，\\s]+", "appSecret=***");
    }

    private String clean(String value) {
        return value == null ? "" : value.trim().replace("\r\n", "\n").replace('\r', '\n');
    }

    private String trimQuotes(String value) {
        String result = value.trim();
        if ((result.startsWith("\"") && result.endsWith("\"")) || (result.startsWith("'") && result.endsWith("'"))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText();
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
