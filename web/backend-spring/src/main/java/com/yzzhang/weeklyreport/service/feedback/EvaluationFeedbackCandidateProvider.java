package com.yzzhang.weeklyreport.service.feedback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.mapper.SubmissionStatusMapper;
import com.yzzhang.weeklyreport.mapper.WeekFileMapper;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.service.TemplateComplianceService;
import com.yzzhang.weeklyreport.service.feedback.EvaluationFeedbackSnapshot.EmployeeFeedback;
import com.yzzhang.weeklyreport.util.WeekLabelUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class EvaluationFeedbackCandidateProvider {
    private static final int MAX_ARTIFACT_BYTES = 512 * 1024;
    private static final String LEGACY_THANKS =
        "感谢您认真完成本周工作记录。团队因您的每一份投入而更加完整，也更有力量。";
    private static final Pattern SECRET = Pattern.compile(
        "(?i)(access[_ -]?token\\s*[:=]\\s*\\S+|(?:appsecret|client[_ -]?secret|password|密码)\\s*[:=：]\\s*\\S+|bearer\\s+\\S+|eyJ[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{10,})"
    );
    private static final Pattern URL = Pattern.compile("(?i)https?://");
    private static final Pattern INTERNAL = Pattern.compile(
        "(?i)(\\b(?:userid|unionid|fileid|spaceid)\\b|[A-Z]:\\\\|/app/|output/)"
    );

    private final SubmissionStatusMapper submissionStatusMapper;
    private final WeekFileMapper weekFileMapper;
    private final ObjectMapper objectMapper;
    private final TemplateComplianceService templateComplianceService;

    public EvaluationFeedbackCandidateProvider(
        SubmissionStatusMapper submissionStatusMapper,
        WeekFileMapper weekFileMapper,
        ObjectMapper objectMapper,
        TemplateComplianceService templateComplianceService
    ) {
        this.submissionStatusMapper = submissionStatusMapper;
        this.weekFileMapper = weekFileMapper;
        this.objectMapper = objectMapper;
        this.templateComplianceService = templateComplianceService;
    }

    public EvaluationFeedbackSnapshot collect(String weekLabel) {
        if (!WeekLabelUtils.isValid(weekLabel)) {
            throw new EvaluationFeedbackException("评价反馈周次无效");
        }
        List<SubmissionStatusPO> rows = templateComplianceService.enrich(
            weekLabel,
            submissionStatusMapper.selectByWeek(weekLabel)
        );
        List<SubmissionStatusPO> submitted = rows.stream()
            .filter(row -> "已提交".equals(row.getStatus()))
            .toList();
        validateSubmittedRows(submitted);

        Path weekRoot = weekFileMapper.weekDir(weekLabel);
        JsonNode state = readJson(weekRoot.resolve("automation").resolve("evaluation_state.json"));
        Path artifactPath = weekRoot.resolve("automation").resolve("employee_feedback.json");
        JsonNode artifact = readJson(artifactPath);
        int artifactVersion = validateArtifactEnvelope(
            weekLabel,
            state,
            artifact,
            weekFileMapper.managerReportPath(weekLabel)
        );

        Map<String, JsonNode> feedbackByUserId = feedbackByUserId(artifact.get("feedback"));
        Set<String> expectedIds = submitted.stream().map(SubmissionStatusPO::getUserid).collect(java.util.stream.Collectors.toSet());
        if (!feedbackByUserId.keySet().equals(expectedIds)) {
            throw new EvaluationFeedbackException("评价反馈人员范围不一致");
        }

        List<String> allNames = rows.stream().map(SubmissionStatusPO::getName).filter(this::hasText).toList();
        List<String> allUserIds = rows.stream().map(SubmissionStatusPO::getUserid).filter(this::hasText).toList();
        List<EmployeeFeedback> candidates = new ArrayList<>();
        for (SubmissionStatusPO row : submitted) {
            JsonNode item = feedbackByUserId.get(row.getUserid());
            String praise = text(item, "praise").trim();
            String improvement = text(item, "improvement").trim();
            String thanks = artifactVersion == 2 ? text(item, "thanks").trim() : LEGACY_THANKS;
            validateProse(praise, improvement, thanks, allNames, allUserIds);
            candidates.add(new EmployeeFeedback(
                row.getUserid(),
                row.getName(),
                row.getDept(),
                row.getTitle(),
                row.getTemplateComplianceRate(),
                praise,
                improvement,
                thanks
            ));
        }
        return new EvaluationFeedbackSnapshot(
            weekLabel,
            candidates,
            fileDigest(artifactPath),
            lastModified(artifactPath)
        );
    }

    private void validateSubmittedRows(List<SubmissionStatusPO> submitted) {
        Set<String> seen = new HashSet<>();
        for (SubmissionStatusPO row : submitted) {
            if (!hasText(row.getUserid()) || !hasText(row.getName()) || !seen.add(row.getUserid())) {
                throw new EvaluationFeedbackException("已提交人员缺少稳定唯一身份");
            }
            Integer rate = row.getTemplateComplianceRate();
            if (rate == null || rate < 0 || rate > 100) {
                throw new EvaluationFeedbackException("已提交人员模板符合度不可用");
            }
        }
    }

    private JsonNode readJson(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_ARTIFACT_BYTES) {
                throw new EvaluationFeedbackException("正式评价反馈未就绪");
            }
            return objectMapper.readTree(path.toFile());
        } catch (IOException ex) {
            throw new EvaluationFeedbackException("正式评价反馈不可读", ex);
        }
    }

    private int validateArtifactEnvelope(String week, JsonNode state, JsonNode artifact, Path reportPath) {
        String reportDigest = text(state, "reportDigest");
        String inputDigest = text(state, "inputDigest");
        int artifactVersion = artifact.path("version").asInt(0);
        if (!"SUCCESS".equals(text(state, "status")) || !week.equals(text(state, "weekLabel"))
            || !hasText(reportDigest) || !hasText(inputDigest)
            || (artifactVersion != 1 && artifactVersion != 2)
            || !week.equals(text(artifact, "weekLabel"))
            || !reportDigest.equals(text(artifact, "reportDigest"))
            || !inputDigest.equals(text(artifact, "inputDigest"))
            || !reportDigest.equals(fileDigest(reportPath))) {
            throw new EvaluationFeedbackException("正式评价反馈校验失败");
        }
        return artifactVersion;
    }

    private Map<String, JsonNode> feedbackByUserId(JsonNode feedback) {
        if (feedback == null || !feedback.isArray() || feedback.size() > 300) {
            throw new EvaluationFeedbackException("评价反馈格式无效");
        }
        Map<String, JsonNode> indexed = new HashMap<>();
        for (JsonNode item : feedback) {
            String userId = text(item, "userid").trim();
            if (!hasText(userId) || userId.length() > 128 || indexed.putIfAbsent(userId, item) != null) {
                throw new EvaluationFeedbackException("评价反馈身份无效");
            }
        }
        return indexed;
    }

    private void validateProse(
        String praise,
        String improvement,
        String thanks,
        List<String> names,
        List<String> userIds
    ) {
        if (praise.length() < 4 || praise.length() > 400
            || improvement.length() < 4 || improvement.length() > 700
            || thanks.length() < 24 || thanks.length() > 220
            || !thanks.startsWith("感谢您") || !thanks.contains("团队因您")) {
            throw new EvaluationFeedbackException("评价反馈内容长度无效");
        }
        String prose = praise + "\n" + improvement + "\n" + thanks;
        if (SECRET.matcher(prose).find() || URL.matcher(prose).find() || INTERNAL.matcher(prose).find()
            || names.stream().anyMatch(prose::contains)
            || userIds.stream().filter(id -> id.length() >= 6).anyMatch(prose::contains)) {
            throw new EvaluationFeedbackException("评价反馈包含不允许的内容");
        }
    }

    private String fileDigest(Path path) {
        if (!Files.isRegularFile(path)) {
            return "";
        }
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new EvaluationFeedbackException("正式评价摘要校验失败", ex);
        }
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ex) {
            throw new EvaluationFeedbackException("正式评价更新时间读取失败", ex);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
