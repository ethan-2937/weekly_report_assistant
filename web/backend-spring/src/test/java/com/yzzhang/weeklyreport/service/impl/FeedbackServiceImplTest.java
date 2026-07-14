package com.yzzhang.weeklyreport.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import com.yzzhang.weeklyreport.po.SysUserPO;
import com.yzzhang.weeklyreport.security.AuthenticatedUser;
import com.yzzhang.weeklyreport.service.notification.DingTalkWorkNoticeClient;
import com.yzzhang.weeklyreport.service.notification.FeedbackRecipientResolver;
import com.yzzhang.weeklyreport.service.notification.WorkNoticeException;
import com.yzzhang.weeklyreport.vo.FeedbackRequestVO;
import com.yzzhang.weeklyreport.vo.FeedbackResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackServiceImplTest {
    @TempDir
    Path tempDir;

    private DingTalkWorkNoticeClient workNoticeClient;
    private FeedbackServiceImpl service;
    private HttpServletRequest servletRequest;

    @BeforeEach
    void setUp() {
        WeeklyReportProperties properties = new WeeklyReportProperties();
        properties.setProjectRoot(tempDir.toString());
        ProjectPathConfig pathConfig = new ProjectPathConfig(properties);
        FeedbackRecipientResolver resolver = mock(FeedbackRecipientResolver.class);
        when(resolver.resolve()).thenReturn(
            new FeedbackRecipientResolver.Recipient("示例接收人", List.of("test-admin-001"))
        );
        workNoticeClient = mock(DingTalkWorkNoticeClient.class);
        service = new FeedbackServiceImpl(
            properties,
            pathConfig,
            new ObjectMapper(),
            resolver,
            workNoticeClient
        );
        servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("192.0.2.10");
        authenticateFictionalUser();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void feedbackStillUsesTheSharedWorkNoticeChannel() {
        FeedbackResponseVO response = service.submit(request(), servletRequest);

        assertThat(response.isDelivered()).isTrue();
        assertThat(response.getTargetName()).isEqualTo("示例接收人");
        verify(workNoticeClient).sendMarkdown(
            eq("周报系统反馈"),
            anyString(),
            eq(List.of("test-admin-001"))
        );
    }

    @Test
    void notificationFailureReturnsOnlyTheSanitizedAdapterMessage() {
        doThrow(new WorkNoticeException("钉钉接口返回 HTTP 500"))
            .when(workNoticeClient)
            .sendMarkdown(eq("周报系统反馈"), anyString(), eq(List.of("test-admin-001")));

        FeedbackResponseVO response = service.submit(request(), servletRequest);

        assertThat(response.isDelivered()).isFalse();
        assertThat(response.getMessage())
            .contains("HTTP 500")
            .doesNotContain("test-admin-001");
    }

    private FeedbackRequestVO request() {
        FeedbackRequestVO request = new FeedbackRequestVO();
        request.setCategory("BUG");
        request.setTitle("虚构页面问题");
        request.setDetail("使用虚构数据复现的显示问题");
        request.setUrgency("NORMAL");
        request.setWeek("2026-W29");
        return request;
    }

    private void authenticateFictionalUser() {
        SysUserPO user = new SysUserPO();
        user.setId(1L);
        user.setUsername("test-login-001");
        user.setRealName("示例员工甲");
        user.setPasswordHash("fictional-password-hash");
        user.setStatus(1);
        AuthenticatedUser principal = new AuthenticatedUser(user, List.of("REPORT_ALL"), List.of());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
