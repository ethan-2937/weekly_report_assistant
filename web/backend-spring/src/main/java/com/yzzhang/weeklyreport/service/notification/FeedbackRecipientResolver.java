package com.yzzhang.weeklyreport.service.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import com.yzzhang.weeklyreport.mapper.SysUserMapper;
import com.yzzhang.weeklyreport.po.SysUserPO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Component
public class FeedbackRecipientResolver {
    private final WeeklyReportProperties properties;
    private final ProjectPathConfig pathConfig;
    private final SysUserMapper sysUserMapper;
    private final ObjectMapper objectMapper;
    private final Function<String, String> environment;

    @Autowired
    public FeedbackRecipientResolver(
        WeeklyReportProperties properties,
        ProjectPathConfig pathConfig,
        SysUserMapper sysUserMapper,
        ObjectMapper objectMapper
    ) {
        this(properties, pathConfig, sysUserMapper, objectMapper, System::getenv);
    }

    FeedbackRecipientResolver(
        WeeklyReportProperties properties,
        ProjectPathConfig pathConfig,
        SysUserMapper sysUserMapper,
        ObjectMapper objectMapper,
        Function<String, String> environment
    ) {
        this.properties = properties;
        this.pathConfig = pathConfig;
        this.sysUserMapper = sysUserMapper;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    public Recipient resolve() {
        WeeklyReportProperties.Feedback feedback = properties.getFeedback();
        Map<String, String> envFile = readEnvFile(feedback);
        String name = firstText(
            environment.apply("WEEKLY_FEEDBACK_RECIPIENT_NAME"),
            environment.apply("FEEDBACK_DINGTALK_RECIPIENT_NAME"),
            envFile.get("WEEKLY_FEEDBACK_RECIPIENT_NAME"),
            envFile.get("FEEDBACK_DINGTALK_RECIPIENT_NAME"),
            feedback.getRecipientName(),
            "系统管理员"
        );
        String configured = resolveConfig(
            envFile,
            feedback.getDingtalkUserIds(),
            "WEEKLY_FEEDBACK_DINGTALK_USER_IDS",
            "FEEDBACK_DINGTALK_USER_IDS"
        );
        List<String> userIds = normalizeUserIds(configured);
        if (userIds.isEmpty()) {
            userIds = userIdFromLocalUser(name);
        }
        if (userIds.isEmpty()) {
            userIds = userIdFromContacts(name);
        }
        return new Recipient(name, userIds);
    }

    private List<String> userIdFromLocalUser(String targetName) {
        try {
            List<String> matches = sysUserMapper.findActiveByRealName(targetName).stream()
                .map(SysUserPO::getDingUserId)
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
            return matches.size() == 1 ? matches : List.of();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<String> userIdFromContacts(String targetName) {
        Path contacts = pathConfig.outputRoot().resolve("contacts").resolve("users.json");
        if (!Files.exists(contacts)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(contacts.toFile());
            if (!root.isArray()) {
                return List.of();
            }
            List<String> matches = new ArrayList<>();
            for (JsonNode node : root) {
                if (targetName.equals(text(node, "name")) && hasText(text(node, "userid"))) {
                    matches.add(text(node, "userid").trim());
                }
            }
            List<String> distinct = matches.stream().distinct().toList();
            return distinct.size() == 1 ? distinct : List.of();
        } catch (IOException ignored) {
            return List.of();
        }
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

    private List<String> normalizeUserIds(String value) {
        LinkedHashSet<String> userIds = new LinkedHashSet<>();
        for (String item : hasText(value) ? value.split("[,，;；\\s]+") : new String[0]) {
            if (hasText(item)) {
                userIds.add(item.trim());
            }
        }
        return List.copyOf(userIds);
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

    private String trimQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Recipient(String name, List<String> userIds) {}
}
