package com.yzzhang.weeklyreport.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.mapper.WeekFileMapper;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.service.TemplateComplianceService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TemplateComplianceServiceImpl implements TemplateComplianceService {
    private static final String STATUS_SUBMITTED = "已提交";
    private static final String STATUS_MISSING = "未提交";
    private static final String UNKNOWN = "无法判断";
    private static final List<RequiredField> REQUIRED_FIELDS = List.of(
        new RequiredField("本周完成成果", List.of("本周完成成果", "本周成果")),
        new RequiredField("工时投入分析", List.of("工时投入分析", "工时占比", "时间投入分析", "时间分配")),
        new RequiredField("AI应用及效果", List.of("AI应用及效果", "AI应用", "AI使用")),
        new RequiredField("下周计划（含交付时间）", List.of("下周计划含交付时间", "下周计划"))
    );
    private static final List<String> OLD_TEMPLATE_MARKERS = List.of(
        "主要工作内容",
        "遇到的挑战与解决方案",
        "创新与改进",
        "下周计划与目标"
    );

    private final WeekFileMapper weekFileMapper;
    private final ObjectMapper objectMapper;

    public TemplateComplianceServiceImpl(WeekFileMapper weekFileMapper, ObjectMapper objectMapper) {
        this.weekFileMapper = weekFileMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<SubmissionStatusPO> enrich(String week, List<SubmissionStatusPO> rows) {
        ReportIndex index = readReports(week);
        for (SubmissionStatusPO row : rows) {
            applyCompliance(row, index.find(row));
        }
        return rows;
    }

    private void applyCompliance(SubmissionStatusPO row, ReportSnapshot report) {
        if (STATUS_MISSING.equals(row.getStatus())) {
            row.setTemplateComplianceRate(null);
            row.setTemplateComplianceStatus(STATUS_MISSING);
            row.setTemplateComplianceMissingFields(List.of());
            row.setTemplateCompliancePresentFields(List.of());
            row.setTemplateComplianceDetail("未提交周报，不参与模板填写正确率统计。");
            return;
        }
        if (!STATUS_SUBMITTED.equals(row.getStatus())) {
            row.setTemplateComplianceRate(null);
            row.setTemplateComplianceStatus(UNKNOWN);
            row.setTemplateComplianceMissingFields(List.of());
            row.setTemplateCompliancePresentFields(List.of());
            row.setTemplateComplianceDetail("提交状态不明确，无法进行模板检查。");
            return;
        }
        if (report == null) {
            row.setTemplateComplianceRate(null);
            row.setTemplateComplianceStatus(UNKNOWN);
            row.setTemplateComplianceMissingFields(REQUIRED_FIELDS.stream().map(RequiredField::label).toList());
            row.setTemplateCompliancePresentFields(List.of());
            row.setTemplateComplianceDetail("未匹配到钉钉原始周报内容，请检查 raw/reports.json 或 report_id。");
            return;
        }

        List<String> present = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (RequiredField field : REQUIRED_FIELDS) {
            FieldValue value = report.find(field);
            if (value != null && hasUsefulText(value.value())) {
                present.add(field.label());
            } else {
                missing.add(field.label());
            }
        }

        int rate = Math.round((present.size() * 100f) / REQUIRED_FIELDS.size());
        row.setTemplateComplianceRate(rate);
        row.setTemplateCompliancePresentFields(present);
        row.setTemplateComplianceMissingFields(missing);
        row.setTemplateComplianceStatus(status(rate, report.usesOldTemplate()));
        row.setTemplateComplianceDetail(detail(rate, missing, report.usesOldTemplate()));
    }

    private ReportIndex readReports(String week) {
        Path path = weekFileMapper.rawReportsPath(week);
        if (!Files.exists(path)) {
            return ReportIndex.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(path.toFile());
            if (!root.isArray()) {
                return ReportIndex.empty();
            }
            ReportIndex index = new ReportIndex();
            for (JsonNode item : root) {
                ReportSnapshot report = ReportSnapshot.from(item);
                index.add(report);
            }
            return index;
        } catch (IOException e) {
            return ReportIndex.empty();
        }
    }

    private String status(int rate, boolean usesOldTemplate) {
        if (rate == 100) {
            return "符合模板";
        }
        if (rate >= 75) {
            return "需补充";
        }
        if (rate > 0) {
            return "不完整";
        }
        return usesOldTemplate ? "疑似旧模板" : "不合规";
    }

    private String detail(int rate, List<String> missing, boolean usesOldTemplate) {
        if (rate == 100) {
            return "四项必填模板字段均已填写。";
        }
        StringBuilder builder = new StringBuilder();
        if (usesOldTemplate) {
            builder.append("检测到旧版周报字段，请提醒按当前钉钉模板填写。");
        }
        if (!missing.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append("缺失或未填写：").append(String.join("、", missing)).append("。");
        }
        return builder.toString();
    }

    private boolean hasUsefulText(String value) {
        if (value == null) {
            return false;
        }
        String text = value
            .replaceAll("<[^>]*>", "")
            .replace("&nbsp;", " ")
            .replace("\u00a0", " ")
            .trim();
        return !text.isBlank()
            && !"[]".equals(text)
            && !"{}".equals(text)
            && !"null".equalsIgnoreCase(text);
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
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\s　（）()【】\\[\\]，,。；;：:、/\\\\\\-]", "")
            .toLowerCase(Locale.ROOT);
    }

    private record RequiredField(String label, List<String> aliases) {
    }

    private record FieldValue(String key, String value) {
    }

    private static class ReportSnapshot {
        private final String reportId;
        private final String creatorId;
        private final long createTime;
        private final List<FieldValue> fields;

        private ReportSnapshot(String reportId, String creatorId, long createTime, List<FieldValue> fields) {
            this.reportId = reportId;
            this.creatorId = creatorId;
            this.createTime = createTime;
            this.fields = fields;
        }

        static ReportSnapshot from(JsonNode item) {
            List<FieldValue> fields = new ArrayList<>();
            JsonNode contents = item.get("contents");
            if (contents != null && contents.isArray()) {
                for (JsonNode content : contents) {
                    fields.add(new FieldValue(text(content, "key"), text(content, "value")));
                }
            }
            return new ReportSnapshot(
                text(item, "report_id"),
                text(item, "creator_id"),
                Math.max(number(item, "create_time"), number(item, "modified_time")),
                fields
            );
        }

        FieldValue find(RequiredField requiredField) {
            for (FieldValue field : fields) {
                if (isOldTemplateField(field.key())) {
                    continue;
                }
                String normalizedKey = normalize(field.key());
                for (String alias : requiredField.aliases()) {
                    if (normalizedKey.contains(normalize(alias))) {
                        return field;
                    }
                }
            }
            return null;
        }

        boolean usesOldTemplate() {
            return fields.stream().anyMatch(field -> isOldTemplateField(field.key()));
        }

        private static boolean isOldTemplateField(String key) {
            String normalizedKey = normalize(key);
            return OLD_TEMPLATE_MARKERS.stream()
                .map(TemplateComplianceServiceImpl::normalize)
                .anyMatch(normalizedKey::contains);
        }
    }

    private static class ReportIndex {
        private final Map<String, ReportSnapshot> byReportId = new LinkedHashMap<>();
        private final Map<String, ReportSnapshot> byCreatorId = new LinkedHashMap<>();

        static ReportIndex empty() {
            return new ReportIndex();
        }

        void add(ReportSnapshot report) {
            if (report.reportId != null && !report.reportId.isBlank()) {
                byReportId.put(report.reportId, report);
            }
            if (report.creatorId != null && !report.creatorId.isBlank()) {
                byCreatorId.merge(report.creatorId, report, this::newer);
            }
        }

        ReportSnapshot find(SubmissionStatusPO row) {
            if (row.getReportId() != null && !row.getReportId().isBlank()) {
                ReportSnapshot byId = byReportId.get(row.getReportId());
                if (byId != null) {
                    return byId;
                }
            }
            if (row.getUserid() != null && !row.getUserid().isBlank()) {
                return byCreatorId.get(row.getUserid());
            }
            return null;
        }

        private ReportSnapshot newer(ReportSnapshot left, ReportSnapshot right) {
            return right.createTime >= left.createTime ? right : left;
        }
    }
}
