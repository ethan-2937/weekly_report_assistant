package com.yzzhang.weeklyreport.service.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.service.notification.DingTalkNotificationCredentialResolver.RobotOtoCredentials;
import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient.PersonalNotice;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

@Component
public class DingTalkRobotOtoClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public DingTalkRobotOtoClient(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this(builder.setConnectTimeout(Duration.ofSeconds(10)).setReadTimeout(Duration.ofSeconds(20)).build(), objectMapper);
    }

    DingTalkRobotOtoClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    void send(RobotOtoCredentials credentials, List<PersonalNotice> notices, IntConsumer afterDelivery) {
        String accessToken = fetchAccessToken(credentials);
        int delivered = 0;
        for (PersonalNotice notice : notices) {
            sendOne(credentials, accessToken, notice);
            afterDelivery.accept(++delivered);
        }
    }

    private String fetchAccessToken(RobotOtoCredentials credentials) {
        Map<String, Object> payload = Map.of(
            "appKey", credentials.appKey(),
            "appSecret", credentials.appSecret()
        );
        ResponseEntity<Map> response = restTemplate.postForEntity(credentials.accessTokenUrl(), payload, Map.class);
        String token = value(response.getBody(), "accessToken", "access_token");
        if (!hasText(token)) throw new WorkNoticeException("未获取到钉钉 accessToken");
        return token;
    }

    private void sendOne(RobotOtoCredentials credentials, String accessToken, PersonalNotice notice) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-acs-dingtalk-access-token", accessToken);

        Map<String, Object> payload = new HashMap<>();
        payload.put("robotCode", credentials.robotCode());
        payload.put("userIds", List.of(notice.userId()));
        payload.put("msgKey", "sampleMarkdown");
        payload.put("msgParam", messageParameter(notice));
        ResponseEntity<Map> response = restTemplate.exchange(
            credentials.sendUrl(), HttpMethod.POST, new HttpEntity<>(payload, headers), Map.class
        );
        Map body = response.getBody();
        if (!hasText(value(body, "processQueryKey"))) {
            throw new WorkNoticeException("钉钉机器人单聊消息未被接受");
        }
        if (hasItems(body, "invalidStaffIdList") || hasItems(body, "flowControlledStaffIdList")) {
            throw new WorkNoticeException("钉钉机器人单聊包含无效或受限接收人");
        }
    }

    private String messageParameter(PersonalNotice notice) {
        try {
            return objectMapper.writeValueAsString(Map.of("title", notice.title(), "text", notice.markdown()));
        } catch (JsonProcessingException ex) {
            throw new WorkNoticeException("钉钉机器人单聊消息编码失败");
        }
    }

    private String value(Map body, String... keys) {
        if (body == null) return "";
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && hasText(String.valueOf(value))) return String.valueOf(value);
        }
        return "";
    }

    private boolean hasItems(Map body, String key) {
        Object value = body == null ? null : body.get(key);
        return value instanceof java.util.Collection<?> collection && !collection.isEmpty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
