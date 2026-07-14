package com.yzzhang.weeklyreport.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import com.yzzhang.weeklyreport.mapper.SysUserMapper;
import com.yzzhang.weeklyreport.po.SysUserPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedbackRecipientResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void configuredRecipientUseridsAreNormalizedWithoutContactAccess() {
        WeeklyReportProperties properties = properties();
        properties.getFeedback().setRecipientName("示例管理员");
        properties.getFeedback().setDingtalkUserIds("test-admin-001; test-admin-002; test-admin-001");
        SysUserMapper mapper = mock(SysUserMapper.class);
        FeedbackRecipientResolver resolver = resolver(properties, mapper);

        FeedbackRecipientResolver.Recipient recipient = resolver.resolve();

        assertThat(recipient.name()).isEqualTo("示例管理员");
        assertThat(recipient.userIds()).containsExactly("test-admin-001", "test-admin-002");
    }

    @Test
    void oneActiveLocalUserIsAStableFallback() {
        WeeklyReportProperties properties = properties();
        properties.getFeedback().setRecipientName("示例管理员");
        SysUserPO user = new SysUserPO();
        user.setDingUserId("test-admin-001");
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.findActiveByRealName("示例管理员")).thenReturn(List.of(user));
        FeedbackRecipientResolver resolver = resolver(properties, mapper);

        FeedbackRecipientResolver.Recipient recipient = resolver.resolve();

        assertThat(recipient.userIds()).containsExactly("test-admin-001");
    }

    private WeeklyReportProperties properties() {
        WeeklyReportProperties properties = new WeeklyReportProperties();
        properties.setProjectRoot(tempDir.toString());
        return properties;
    }

    private FeedbackRecipientResolver resolver(WeeklyReportProperties properties, SysUserMapper mapper) {
        return new FeedbackRecipientResolver(
            properties,
            new ProjectPathConfig(properties),
            mapper,
            new ObjectMapper(),
            key -> null
        );
    }
}
