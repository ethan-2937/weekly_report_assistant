package com.yzzhang.weeklyreport.service.notification;

import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntConsumer;

@Component
public class DingTalkWorkNoticeClient {
    private static final int MAX_RECIPIENTS_PER_REQUEST = 100;

    private final WeeklyReportProperties properties;
    private final ProjectPathConfig pathConfig;
    private final RestTemplate restTemplate;
    private final Function<String, String> environment;

    @Autowired
    public DingTalkWorkNoticeClient(
        WeeklyReportProperties properties,
        ProjectPathConfig pathConfig,
        RestTemplateBuilder restTemplateBuilder
    ) {
        this(
            properties,
            pathConfig,
            restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(20))
                .build(),
            System::getenv
        );
    }

    DingTalkWorkNoticeClient(
        WeeklyReportProperties properties,
        ProjectPathConfig pathConfig,
        RestTemplate restTemplate
    ) {
        this(properties, pathConfig, restTemplate, key -> null);
    }

    private DingTalkWorkNoticeClient(
        WeeklyReportProperties properties,
        ProjectPathConfig pathConfig,
        RestTemplate restTemplate,
        Function<String, String> environment
    ) {
        this.properties = properties;
        this.pathConfig = pathConfig;
        this.restTemplate = restTemplate;
        this.environment = environment;
    }

    public void sendMarkdown(String title, String markdown, List<String> recipientUserIds) {
        List<String> recipients = normalizeRecipients(recipientUserIds);
        if (recipients.isEmpty()) {
            throw new WorkNoticeException("未配置钉钉工作通知接收人");
        }

        Credentials credentials = credentials();
        try {
            String accessToken = fetchAccessToken(credentials);
            for (int start = 0; start < recipients.size(); start += MAX_RECIPIENTS_PER_REQUEST) {
                int end = Math.min(start + MAX_RECIPIENTS_PER_REQUEST, recipients.size());
                sendChunk(credentials, accessToken, title, markdown, recipients.subList(start, end));
            }
        } catch (WorkNoticeException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new WorkNoticeException("钉钉接口返回 HTTP " + ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            throw new WorkNoticeException("钉钉工作通知请求失败");
        }
    }

    public void sendPersonalMarkdown(List<PersonalNotice> notices, IntConsumer afterDelivery) {
        List<PersonalNotice> safeNotices = notices == null ? List.of() : List.copyOf(notices);
        if (safeNotices.isEmpty() || safeNotices.size() > 300 || afterDelivery == null) {
            throw new WorkNoticeException("个性化钉钉通知批次无效");
        }
        if (safeNotices.stream().anyMatch(notice -> notice == null || !hasText(notice.userId()))) {
            throw new WorkNoticeException("个性化钉钉通知接收人无效");
        }

        Credentials credentials = credentials();
        try {
            String accessToken = fetchAccessToken(credentials);
            int delivered = 0;
            for (PersonalNotice notice : safeNotices) {
                sendChunk(credentials, accessToken, notice.title(), notice.markdown(), List.of(notice.userId()));
                delivered += 1;
                afterDelivery.accept(delivered);
            }
        } catch (WorkNoticeException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new WorkNoticeException("钉钉接口返回 HTTP " + ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            throw new WorkNoticeException("钉钉工作通知请求失败");
        }
    }

    private String fetchAccessToken(Credentials credentials) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("appKey", credentials.appKey());
        payload.put("appSecret", credentials.appSecret());
        ResponseEntity<Map> response = restTemplate.postForEntity(credentials.accessTokenUrl(), payload, Map.class);
        String token = stringValueDeep(response.getBody(), List.of("accessToken", "access_token"));
        if (!hasText(token)) {
            throw new WorkNoticeException("未获取到钉钉 accessToken");
        }
        return token;
    }

    private void sendChunk(
        Credentials credentials,
        String accessToken,
        String title,
        String markdown,
        List<String> recipients
    ) {
        URI uri = UriComponentsBuilder.fromUriString(credentials.asyncSendUrl())
            .queryParam("access_token", accessToken)
            .build(true)
            .toUri();

        Map<String, Object> markdownBody = Map.of("title", title, "text", markdown);
        Map<String, Object> msg = Map.of("msgtype", "markdown", "markdown", markdownBody);
        Map<String, Object> payload = new HashMap<>();
        payload.put("agent_id", credentials.agentId());
        payload.put("userid_list", String.join(",", recipients));
        payload.put("to_all_user", false);
        payload.put("msg", msg);

        ResponseEntity<Map> response = restTemplate.postForEntity(uri, payload, Map.class);
        Map body = response.getBody();
        Object errcode = body == null ? null : body.get("errcode");
        if (errcode == null || !"0".equals(String.valueOf(errcode))) {
            throw new WorkNoticeException("钉钉工作通知未被接受");
        }
        if (hasText(stringValueDeep(body, List.of("invalid_user_id_list", "invalidUserIdList")))) {
            throw new WorkNoticeException("钉钉工作通知包含无效接收人");
        }
    }

    private Credentials credentials() {
        WeeklyReportProperties.Feedback feedback = properties.getFeedback();
        Map<String, String> envFile = readEnvFile(feedback);
        String appKey = resolveConfig(envFile, feedback.getDingtalkAppKey(),
            "WEEKLY_FEEDBACK_DINGTALK_APP_KEY", "DINGTALK_APP_KEY");
        String appSecret = resolveConfig(envFile, feedback.getDingtalkAppSecret(),
            "WEEKLY_FEEDBACK_DINGTALK_APP_SECRET", "DINGTALK_APP_SECRET");
        String agentId = resolveConfig(envFile, feedback.getDingtalkAgentId(),
            "WEEKLY_FEEDBACK_DINGTALK_AGENT_ID", "DINGTALK_AGENT_ID");
        if (!hasText(appKey) || !hasText(appSecret) || !hasText(agentId)) {
            throw new WorkNoticeException("缺少钉钉应用 AppKey、AppSecret 或 AgentId 配置");
        }

        long parsedAgentId;
        try {
            parsedAgentId = Long.parseLong(agentId.trim());
        } catch (NumberFormatException ex) {
            throw new WorkNoticeException("钉钉 AgentId 不是有效数字");
        }
        return new Credentials(
            appKey,
            appSecret,
            parsedAgentId,
            feedback.getAccessTokenUrl(),
            feedback.getAsyncSendUrl()
        );
    }

    private Map<String, String> readEnvFile(WeeklyReportProperties.Feedback feedback) {
        Path envPath = envPath(feedback);
        if (!Files.exists(envPath)) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        try {
            for (String rawLine : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                String line = rawLine.strip().replaceFirst("^\\uFEFF", "");
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
            return configured.isAbsolute()
                ? configured.normalize()
                : pathConfig.projectRoot().resolve(configured).normalize();
        }
        return pathConfig.projectRoot().resolve("config").resolve(".env").normalize();
    }

    private String resolveConfig(Map<String, String> envFile, String propertyValue, String weeklyKey, String legacyKey) {
        return firstText(
            propertyValue,
            environment.apply(weeklyKey),
            environment.apply(legacyKey),
            envFile.get(weeklyKey),
            envFile.get(legacyKey)
        );
    }

    private List<String> normalizeRecipients(List<String> values) {
        LinkedHashSet<String> recipients = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (hasText(value)) {
                recipients.add(value.trim());
            }
        }
        return new ArrayList<>(recipients);
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

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String trimQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Credentials(
        String appKey,
        String appSecret,
        long agentId,
        String accessTokenUrl,
        String asyncSendUrl
    ) {}

    public record PersonalNotice(String title, String markdown, String userId) {}
}
