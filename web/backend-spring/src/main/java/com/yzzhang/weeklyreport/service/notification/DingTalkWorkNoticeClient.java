package com.yzzhang.weeklyreport.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.config.FeedbackPersonalMessageProperties;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import com.yzzhang.weeklyreport.service.notification.DingTalkNotificationCredentialResolver.WorkNoticeCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

@Component
public class DingTalkWorkNoticeClient {
    private static final int MAX_RECIPIENTS_PER_REQUEST = 100;
    private static final String WORK_NOTICE = "WORK_NOTICE";
    private static final String ROBOT_OTO = "ROBOT_OTO";

    private final DingTalkNotificationCredentialResolver credentialResolver;
    private final DingTalkRobotOtoClient robotOtoClient;
    private final FeedbackPersonalMessageProperties personalMessageProperties;
    private final RestTemplate restTemplate;

    @Autowired
    public DingTalkWorkNoticeClient(
        DingTalkNotificationCredentialResolver credentialResolver,
        DingTalkRobotOtoClient robotOtoClient,
        FeedbackPersonalMessageProperties personalMessageProperties,
        RestTemplateBuilder restTemplateBuilder
    ) {
        this(
            credentialResolver,
            robotOtoClient,
            personalMessageProperties,
            restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(20))
                .build()
        );
    }

    DingTalkWorkNoticeClient(
        WeeklyReportProperties properties,
        ProjectPathConfig pathConfig,
        FeedbackPersonalMessageProperties personalMessageProperties,
        RestTemplate restTemplate
    ) {
        this(
            new DingTalkNotificationCredentialResolver(properties, pathConfig, personalMessageProperties, key -> null),
            new DingTalkRobotOtoClient(restTemplate, new ObjectMapper()),
            personalMessageProperties,
            restTemplate
        );
    }

    private DingTalkWorkNoticeClient(
        DingTalkNotificationCredentialResolver credentialResolver,
        DingTalkRobotOtoClient robotOtoClient,
        FeedbackPersonalMessageProperties personalMessageProperties,
        RestTemplate restTemplate
    ) {
        this.credentialResolver = credentialResolver;
        this.robotOtoClient = robotOtoClient;
        this.personalMessageProperties = personalMessageProperties;
        this.restTemplate = restTemplate;
    }

    public void sendMarkdown(String title, String markdown, List<String> recipientUserIds) {
        List<String> recipients = normalizeRecipients(recipientUserIds);
        if (recipients.isEmpty()) {
            throw new WorkNoticeException("未配置钉钉工作通知接收人");
        }

        WorkNoticeCredentials credentials = credentialResolver.workNotice();
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

        String mode = personalMessageMode();
        try {
            if (ROBOT_OTO.equals(mode)) {
                robotOtoClient.send(credentialResolver.robotOto(), safeNotices, afterDelivery);
                return;
            }
            WorkNoticeCredentials credentials = credentialResolver.workNotice();
            String accessToken = fetchAccessToken(credentials);
            int delivered = 0;
            for (PersonalNotice notice : safeNotices) {
                sendChunk(credentials, accessToken, notice.title(), notice.markdown(), List.of(notice.userId()));
                afterDelivery.accept(++delivered);
            }
        } catch (WorkNoticeException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw new WorkNoticeException("钉钉接口返回 HTTP " + ex.getStatusCode().value());
        } catch (RuntimeException ex) {
            throw new WorkNoticeException("钉钉个性化通知请求失败");
        }
    }

    private String fetchAccessToken(WorkNoticeCredentials credentials) {
        Map<String, Object> payload = Map.of(
            "appKey", credentials.appKey(),
            "appSecret", credentials.appSecret()
        );
        ResponseEntity<Map> response = restTemplate.postForEntity(credentials.accessTokenUrl(), payload, Map.class);
        String token = stringValueDeep(response.getBody(), List.of("accessToken", "access_token"));
        if (!hasText(token)) {
            throw new WorkNoticeException("未获取到钉钉 accessToken");
        }
        return token;
    }

    private void sendChunk(
        WorkNoticeCredentials credentials,
        String accessToken,
        String title,
        String markdown,
        List<String> recipients
    ) {
        URI uri = UriComponentsBuilder.fromUriString(credentials.sendUrl())
            .queryParam("access_token", accessToken)
            .build(true)
            .toUri();

        Map<String, Object> msg = Map.of(
            "msgtype", "markdown",
            "markdown", Map.of("title", title, "text", markdown)
        );
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

    private String personalMessageMode() {
        String value = personalMessageProperties.getMode();
        String mode = hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : WORK_NOTICE;
        if (!WORK_NOTICE.equals(mode) && !ROBOT_OTO.equals(mode)) {
            throw new WorkNoticeException("钉钉个性化通知通道配置无效");
        }
        return mode;
    }

    private List<String> normalizeRecipients(List<String> values) {
        LinkedHashSet<String> recipients = new LinkedHashSet<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (hasText(value)) recipients.add(value.trim());
        }
        return new ArrayList<>(recipients);
    }

    @SuppressWarnings("unchecked")
    private String stringValueDeep(Map map, List<String> keys) {
        if (map == null) return "";
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && hasText(String.valueOf(value))) return String.valueOf(value);
        }
        for (Object value : map.values()) {
            if (value instanceof Map nested) {
                String nestedValue = stringValueDeep(nested, keys);
                if (hasText(nestedValue)) return nestedValue;
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record PersonalNotice(String title, String markdown, String userId) {}
}
