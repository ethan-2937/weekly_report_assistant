package com.yzzhang.weeklyreport.service.notification;

import com.yzzhang.weeklyreport.config.FeedbackPersonalMessageProperties;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class DingTalkNotificationCredentialResolver {
    private final WeeklyReportProperties properties;
    private final ProjectPathConfig pathConfig;
    private final FeedbackPersonalMessageProperties personalMessageProperties;
    private final Function<String, String> environment;

    public DingTalkNotificationCredentialResolver(
        WeeklyReportProperties properties,
        ProjectPathConfig pathConfig,
        FeedbackPersonalMessageProperties personalMessageProperties
    ) {
        this(properties, pathConfig, personalMessageProperties, System::getenv);
    }

    DingTalkNotificationCredentialResolver(
        WeeklyReportProperties properties,
        ProjectPathConfig pathConfig,
        FeedbackPersonalMessageProperties personalMessageProperties,
        Function<String, String> environment
    ) {
        this.properties = properties;
        this.pathConfig = pathConfig;
        this.personalMessageProperties = personalMessageProperties;
        this.environment = environment;
    }

    WorkNoticeCredentials workNotice() {
        ResolvedApplication application = application();
        String agentId = resolve(application.envFile(), properties.getFeedback().getDingtalkAgentId(),
            "WEEKLY_FEEDBACK_DINGTALK_AGENT_ID", "DINGTALK_AGENT_ID");
        if (!hasText(agentId)) {
            throw new WorkNoticeException("缺少钉钉应用 AgentId 配置");
        }
        try {
            return new WorkNoticeCredentials(
                application.appKey(),
                application.appSecret(),
                Long.parseLong(agentId.trim()),
                properties.getFeedback().getAccessTokenUrl(),
                properties.getFeedback().getAsyncSendUrl()
            );
        } catch (NumberFormatException ex) {
            throw new WorkNoticeException("钉钉 AgentId 不是有效数字");
        }
    }

    RobotOtoCredentials robotOto() {
        ResolvedApplication application = application();
        String robotCode = firstText(
            personalMessageProperties.getRobotCode(),
            environment.apply("WEEKLY_FEEDBACK_DINGTALK_ROBOT_CODE"),
            application.envFile().get("WEEKLY_FEEDBACK_DINGTALK_ROBOT_CODE"),
            application.appKey()
        );
        if (!hasText(robotCode)) {
            throw new WorkNoticeException("缺少钉钉机器人 RobotCode 配置");
        }
        return new RobotOtoCredentials(
            application.appKey(),
            application.appSecret(),
            robotCode,
            properties.getFeedback().getAccessTokenUrl(),
            personalMessageProperties.getRobotSendUrl()
        );
    }

    private ResolvedApplication application() {
        WeeklyReportProperties.Feedback feedback = properties.getFeedback();
        Map<String, String> envFile = readEnvFile(feedback);
        String appKey = resolve(envFile, feedback.getDingtalkAppKey(),
            "WEEKLY_FEEDBACK_DINGTALK_APP_KEY", "DINGTALK_APP_KEY");
        String appSecret = resolve(envFile, feedback.getDingtalkAppSecret(),
            "WEEKLY_FEEDBACK_DINGTALK_APP_SECRET", "DINGTALK_APP_SECRET");
        if (!hasText(appKey) || !hasText(appSecret)) {
            throw new WorkNoticeException("缺少钉钉应用 AppKey 或 AppSecret 配置");
        }
        return new ResolvedApplication(appKey, appSecret, envFile);
    }

    private Map<String, String> readEnvFile(WeeklyReportProperties.Feedback feedback) {
        Path path = envPath(feedback);
        if (!Files.exists(path)) return Map.of();
        Map<String, String> values = new HashMap<>();
        try {
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String line = rawLine.strip().replaceFirst("^\\uFEFF", "");
                if (line.isBlank() || line.startsWith("#") || !line.contains("=")) continue;
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
            return configured.isAbsolute() ? configured.normalize() : pathConfig.projectRoot().resolve(configured).normalize();
        }
        return pathConfig.projectRoot().resolve("config").resolve(".env").normalize();
    }

    private String resolve(Map<String, String> envFile, String configured, String weeklyKey, String legacyKey) {
        return firstText(configured, environment.apply(weeklyKey), environment.apply(legacyKey),
            envFile.get(weeklyKey), envFile.get(legacyKey));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) return value.trim();
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

    record WorkNoticeCredentials(
        String appKey,
        String appSecret,
        long agentId,
        String accessTokenUrl,
        String sendUrl
    ) {}

    record RobotOtoCredentials(
        String appKey,
        String appSecret,
        String robotCode,
        String accessTokenUrl,
        String sendUrl
    ) {}

    private record ResolvedApplication(String appKey, String appSecret, Map<String, String> envFile) {}
}
