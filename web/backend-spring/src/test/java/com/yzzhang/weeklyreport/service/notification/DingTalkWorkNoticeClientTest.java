package com.yzzhang.weeklyreport.service.notification;

import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DingTalkWorkNoticeClientTest {
    private static final String TOKEN_URL = "https://example.invalid/token";
    private static final String SEND_URL = "https://example.invalid/send";
    private static final String ACCESS_TOKEN = "fictional-access-token";

    @TempDir
    Path tempDir;

    private MockRestServiceServer server;
    private DingTalkWorkNoticeClient client;

    @BeforeEach
    void setUp() {
        WeeklyReportProperties properties = new WeeklyReportProperties();
        properties.setProjectRoot(tempDir.toString());
        WeeklyReportProperties.Feedback feedback = properties.getFeedback();
        feedback.setDingtalkAppKey("fictional-app-key");
        feedback.setDingtalkAppSecret("fictional-app-secret");
        feedback.setDingtalkAgentId("12345");
        feedback.setAccessTokenUrl(TOKEN_URL);
        feedback.setAsyncSendUrl(SEND_URL);
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new DingTalkWorkNoticeClient(properties, new ProjectPathConfig(properties), restTemplate);
    }

    @Test
    void sendsPrivateMarkdownNoticeToDistinctUserids() {
        server.expect(once(), requestTo(TOKEN_URL))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"accessToken\":\"" + ACCESS_TOKEN + "\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(SEND_URL + "?access_token=" + ACCESS_TOKEN))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""
                {
                  "agent_id": 12345,
                  "userid_list": "test-user-001,test-user-002",
                  "to_all_user": false,
                  "msg": {"msgtype": "markdown"}
                }
                """, false))
            .andRespond(withSuccess("{\"errcode\":0}", MediaType.APPLICATION_JSON));

        client.sendMarkdown(
            "虚构通知",
            "不包含敏感内容",
            List.of("test-user-001", "test-user-002", "test-user-001")
        );

        server.verify();
    }

    @Test
    void responseFailuresDoNotExposeResponseBodyOrCredentials() {
        String sensitiveBody = "fictional-access-token fictional-app-secret";
        server.expect(once(), requestTo(TOKEN_URL))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(sensitiveBody));

        assertThatThrownBy(() -> client.sendMarkdown("虚构通知", "正文", List.of("test-user-001")))
            .isInstanceOf(WorkNoticeException.class)
            .hasMessageContaining("HTTP 500")
            .hasMessageNotContaining("fictional-access-token")
            .hasMessageNotContaining("fictional-app-secret");
    }

    @Test
    void emptyDingTalkResponseIsNotTreatedAsDelivered() {
        server.expect(once(), requestTo(TOKEN_URL))
            .andRespond(withSuccess("{\"accessToken\":\"" + ACCESS_TOKEN + "\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(SEND_URL + "?access_token=" + ACCESS_TOKEN))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.sendMarkdown("虚构通知", "正文", List.of("test-user-001")))
            .isInstanceOf(WorkNoticeException.class)
            .hasMessage("钉钉工作通知未被接受");
    }

    @Test
    void invalidRecipientsAreReportedWithoutEchoingTheirUserids() {
        server.expect(once(), requestTo(TOKEN_URL))
            .andRespond(withSuccess("{\"accessToken\":\"" + ACCESS_TOKEN + "\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(SEND_URL + "?access_token=" + ACCESS_TOKEN))
            .andRespond(withSuccess(
                "{\"errcode\":0,\"invalid_user_id_list\":\"test-user-sensitive\"}",
                MediaType.APPLICATION_JSON
            ));

        assertThatThrownBy(() -> client.sendMarkdown("虚构通知", "正文", List.of("test-user-sensitive")))
            .isInstanceOf(WorkNoticeException.class)
            .hasMessage("钉钉工作通知包含无效接收人")
            .hasMessageNotContaining("test-user-sensitive");
    }
}
