package com.yzzhang.weeklyreport.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.common.ResourceNotFoundException;
import com.yzzhang.weeklyreport.mapper.SubmissionStatusMapper;
import com.yzzhang.weeklyreport.mapper.WeekFileMapper;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.service.ReportPermissionService;
import com.yzzhang.weeklyreport.service.WeeklyReportSourceService;
import com.yzzhang.weeklyreport.util.WeekLabelUtils;
import com.yzzhang.weeklyreport.vo.WeeklyReportDetailVO;
import com.yzzhang.weeklyreport.vo.WeeklyReportFieldVO;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class WeeklyReportSourceServiceImpl implements WeeklyReportSourceService {
    static final int MAX_PREVIEW_CHARACTERS = 100_000;
    private static final String STATUS_SUBMITTED = "已提交";
    private static final String MESSAGE_MISSING = "该成员本周未提交，没有可查看的周报原文。";
    private static final String MESSAGE_UNAVAILABLE = "周报原文暂时不可用，请联系开发人员。";
    private static final String MESSAGE_TOO_LARGE = "周报内容过大，暂不支持在线预览，请联系开发人员。";
    private static final Pattern CREDENTIAL_VALUE = Pattern.compile(
        "(?i)(access[_-]?token|appsecret|client[_-]?secret|password|密码)(\\s*[:=：]\\s*)[^\\s,;，；]+"
    );
    private static final Pattern BEARER_VALUE = Pattern.compile("(?i)Bearer\\s+[^\\s,;，；]+");
    private static final Pattern JWT_VALUE = Pattern.compile(
        "\\beyJ[A-Za-z0-9_-]*\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"
    );

    private final SubmissionStatusMapper submissionStatusMapper;
    private final WeekFileMapper weekFileMapper;
    private final ReportPermissionService reportPermissionService;
    private final ObjectMapper objectMapper;

    public WeeklyReportSourceServiceImpl(
        SubmissionStatusMapper submissionStatusMapper,
        WeekFileMapper weekFileMapper,
        ReportPermissionService reportPermissionService,
        ObjectMapper objectMapper
    ) {
        this.submissionStatusMapper = submissionStatusMapper;
        this.weekFileMapper = weekFileMapper;
        this.reportPermissionService = reportPermissionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public WeeklyReportDetailVO getReport(String week, String userId) {
        assertRequest(week, userId);
        ReportPermissionService.ReportPermission permission = reportPermissionService.currentPermission();
        List<SubmissionStatusPO> visibleRows = reportPermissionService.filterRows(
            submissionStatusMapper.selectByWeek(week),
            permission
        );
        SubmissionStatusPO target = visibleRows.stream()
            .filter(row -> userId.equals(row.getUserid()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("report not found"));

        WeeklyReportDetailVO detail = baseDetail(week, target);
        if (!STATUS_SUBMITTED.equals(target.getStatus())) {
            detail.setMessage(MESSAGE_MISSING);
            return detail;
        }

        JsonNode report = readReport(weekFileMapper.rawReportsPath(week), target);
        if (report == null) {
            detail.setMessage(MESSAGE_UNAVAILABLE);
            return detail;
        }

        ExtractedFields extracted = extractFields(report);
        if (extracted.tooLarge()) {
            detail.setMessage(MESSAGE_TOO_LARGE);
            return detail;
        }
        if (extracted.fields().isEmpty()) {
            detail.setMessage(MESSAGE_UNAVAILABLE);
            return detail;
        }
        detail.setAvailable(true);
        detail.setFields(extracted.fields());
        return detail;
    }

    private WeeklyReportDetailVO baseDetail(String week, SubmissionStatusPO target) {
        WeeklyReportDetailVO detail = new WeeklyReportDetailVO();
        detail.setWeek(week);
        detail.setName(target.getName());
        detail.setDepartment(target.getDept());
        detail.setTitle(target.getTitle());
        detail.setStatus(target.getStatus());
        detail.setSubmittedAt(target.getSubmitTime());
        return detail;
    }

    private JsonNode readReport(Path path, SubmissionStatusPO target) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            return findReport(root, target);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private JsonNode findReport(JsonNode root, SubmissionStatusPO target) {
        if (root == null || !root.isArray()) {
            return null;
        }
        JsonNode creatorMatch = null;
        long latestTime = Long.MIN_VALUE;
        for (JsonNode item : root) {
            if (hasText(target.getReportId()) && target.getReportId().equals(text(item, "report_id"))) {
                return item;
            }
            if (target.getUserid().equals(text(item, "creator_id"))) {
                long itemTime = Math.max(number(item, "create_time"), number(item, "modified_time"));
                if (creatorMatch == null || itemTime >= latestTime) {
                    creatorMatch = item;
                    latestTime = itemTime;
                }
            }
        }
        return creatorMatch;
    }

    private ExtractedFields extractFields(JsonNode report) {
        JsonNode contents = report.get("contents");
        if (contents == null || !contents.isArray()) {
            return new ExtractedFields(List.of(), false);
        }
        List<WeeklyReportFieldVO> fields = new ArrayList<>();
        int characterCount = 0;
        for (JsonNode content : contents) {
            String label = sanitize(text(content, "key")).trim();
            String value = sanitize(text(content, "value")).trim();
            if (label.isBlank() || isAttachmentLabel(label)) {
                continue;
            }
            characterCount += label.length() + value.length();
            if (characterCount > MAX_PREVIEW_CHARACTERS) {
                return new ExtractedFields(List.of(), true);
            }
            fields.add(new WeeklyReportFieldVO(label, value));
        }
        return new ExtractedFields(List.copyOf(fields), false);
    }

    private String sanitize(String value) {
        String cleaned = value == null ? "" : value.replace("\u0000", "");
        cleaned = CREDENTIAL_VALUE.matcher(cleaned).replaceAll("$1$2[已隐藏]");
        cleaned = BEARER_VALUE.matcher(cleaned).replaceAll("Bearer [已隐藏]");
        return JWT_VALUE.matcher(cleaned).replaceAll("[JWT已隐藏]");
    }

    private void assertRequest(String week, String userId) {
        if (!WeekLabelUtils.isValid(week)) {
            throw new IllegalArgumentException("invalid week");
        }
        if (!hasText(userId) || userId.length() > 128) {
            throw new IllegalArgumentException("invalid user id");
        }
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static long number(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return 0L;
        }
        if (value.isNumber()) {
            return value.asLong();
        }
        try {
            return Long.parseLong(value.asText("0"));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isAttachmentLabel(String label) {
        String normalized = label.replaceAll("[\\s　（）()【】\\[\\]：:]", "");
        return normalized.contains("附件") || normalized.contains("图片");
    }

    private record ExtractedFields(List<WeeklyReportFieldVO> fields, boolean tooLarge) {
    }
}
