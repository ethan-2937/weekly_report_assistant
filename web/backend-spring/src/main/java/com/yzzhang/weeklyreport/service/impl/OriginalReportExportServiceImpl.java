package com.yzzhang.weeklyreport.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.common.ExportUnavailableException;
import com.yzzhang.weeklyreport.mapper.WeekFileMapper;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.service.OriginalReportExportService;
import com.yzzhang.weeklyreport.service.ReportPermissionService;
import com.yzzhang.weeklyreport.util.WeekLabelUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OriginalReportExportServiceImpl implements OriginalReportExportService {
    static final String OUTCOMES_TEMPLATE = "优智科技周报（Weekly Outcomes ）";
    static final String LEGACY_TEMPLATE = "周报";
    private static final long MAX_SOURCE_BYTES = 50L * 1024 * 1024;
    private static final int MAX_REPORTS = 5_000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm")
        .withZone(ZoneId.of("Asia/Shanghai"));

    private final WeekFileMapper weekFileMapper;
    private final ReportPermissionService reportPermissionService;
    private final ObjectMapper objectMapper;

    public OriginalReportExportServiceImpl(
        WeekFileMapper weekFileMapper,
        ReportPermissionService reportPermissionService,
        ObjectMapper objectMapper
    ) {
        this.weekFileMapper = weekFileMapper;
        this.reportPermissionService = reportPermissionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Path exportXlsx(String week) {
        if (!WeekLabelUtils.isValid(week)) {
            throw new IllegalArgumentException("invalid week");
        }
        ReportPermissionService.ReportPermission permission = reportPermissionService.currentPermission();
        Path source = weekFileMapper.allReportsPath(week);
        if (!Files.isRegularFile(source)) {
            throw new ExportUnavailableException();
        }
        List<OriginalReportRow> allRows;
        try {
            allRows = readRows(source, readEmployeeNumbers());
        } catch (RuntimeException e) {
            throw new ExportUnavailableException();
        }
        List<OriginalReportRow> visibleRows = filterRows(allRows, permission);
        Path exported = null;
        try {
            exported = Files.createTempFile("original_reports_" + week + "_", ".xlsx");
            int written = OriginalReportXlsxExporter.write(exported, visibleRows);
            if (written != visibleRows.size()) {
                throw new IOException("export row count mismatch");
            }
            exported.toFile().deleteOnExit();
            return exported;
        } catch (IOException | RuntimeException e) {
            deleteQuietly(exported);
            throw new ExportUnavailableException();
        }
    }

    private List<OriginalReportRow> filterRows(
        List<OriginalReportRow> rows,
        ReportPermissionService.ReportPermission permission
    ) {
        List<SubmissionStatusPO> candidates = rows.stream().map(this::permissionRow).toList();
        Set<SubmissionStatusPO> visible = Collections.newSetFromMap(new IdentityHashMap<>());
        visible.addAll(reportPermissionService.filterRows(candidates, permission));
        List<OriginalReportRow> result = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            if (visible.contains(candidates.get(index))) {
                result.add(rows.get(index));
            }
        }
        return List.copyOf(result);
    }

    private SubmissionStatusPO permissionRow(OriginalReportRow row) {
        SubmissionStatusPO permissionRow = new SubmissionStatusPO();
        permissionRow.setUserid(row.userId());
        permissionRow.setName(row.name());
        permissionRow.setDept(row.department());
        permissionRow.setReportDept(row.department());
        permissionRow.setReportId(row.reportId());
        return permissionRow;
    }

    private List<OriginalReportRow> readRows(Path source, Map<String, String> employeeNumbers) {
        JsonNode root = readBoundedJson(source, MAX_SOURCE_BYTES);
        if (!root.isArray() || root.size() > MAX_REPORTS) {
            throw new IllegalStateException("original report snapshot is invalid");
        }
        List<OriginalReportRow> rows = new ArrayList<>();
        for (JsonNode report : root) {
            String reportId = text(report, "report_id");
            String template = text(report, "template_name");
            String userId = text(report, "creator_id");
            String name = text(report, "creator_name");
            String department = text(report, "dept_name");
            String createdAt = time(report.get("create_time"));
            String modifiedAt = time(report.get("modified_time"));
            if (!isSupportedTemplate(template)
                || !hasText(reportId) || !hasText(userId) || !hasText(name) || !hasText(department)
                || !hasText(createdAt) || !hasText(modifiedAt)) {
                throw new IllegalStateException("original report snapshot is incomplete");
            }
            rows.add(new OriginalReportRow(
                reportId,
                template,
                userId,
                employeeNumbers.getOrDefault(userId, ""),
                name,
                department,
                createdAt,
                modifiedAt,
                contents(report.get("contents")),
                imageUrls(report.get("images")),
                rawText(report, "remark")
            ));
        }
        return List.copyOf(rows);
    }

    private Map<String, String> readEmployeeNumbers() {
        Path contacts = weekFileMapper.contactsUsersPath();
        if (!Files.isRegularFile(contacts)) {
            return Map.of();
        }
        JsonNode root;
        try {
            root = readBoundedJson(contacts, 20L * 1024 * 1024);
        } catch (RuntimeException ignored) {
            return Map.of();
        }
        if (!root.isArray()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode user : root) {
            String userId = text(user, "userid");
            String employeeNumber = text(user, "job_number");
            if (hasText(userId) && hasText(employeeNumber)) {
                result.putIfAbsent(userId, employeeNumber);
            }
        }
        return Map.copyOf(result);
    }

    private JsonNode readBoundedJson(Path path, long maxBytes) {
        try {
            long size = Files.size(path);
            if (size <= 0 || size > maxBytes) {
                throw new IllegalStateException("source file size is invalid");
            }
            return objectMapper.readTree(path.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("source file could not be read");
        }
    }

    private Map<String, String> contents(JsonNode contents) {
        if (contents == null || !contents.isArray()) {
            return Map.of();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (JsonNode item : contents) {
            String key = text(item, "key");
            String value = rawText(item, "value");
            if (hasText(key)) {
                fields.merge(key, value, (left, right) -> left + "\n" + right);
            }
        }
        return Map.copyOf(fields);
    }

    private String imageUrls(JsonNode images) {
        if (images == null || !images.isArray()) {
            return "";
        }
        List<String> urls = new ArrayList<>();
        for (JsonNode image : images) {
            if (image.isTextual()) {
                addHttpsUrl(urls, image.asText());
                continue;
            }
            for (String key : List.of("url", "download_url", "image_url")) {
                addHttpsUrl(urls, text(image, key));
            }
        }
        return String.join("\n", urls.stream().distinct().toList());
    }

    private void addHttpsUrl(List<String> urls, String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("https://")) {
            urls.add(normalized);
        }
    }

    private String time(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.canConvertToLong()) {
            try {
                return TIME_FORMAT.format(Instant.ofEpochMilli(value.asLong()));
            } catch (RuntimeException ignored) {
                return "";
            }
        }
        return value.asText("").trim();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private String rawText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private boolean isSupportedTemplate(String value) {
        return OUTCOMES_TEMPLATE.equals(value) || LEGACY_TEMPLATE.equals(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Do not include temporary paths in the public error response.
        }
    }
}
