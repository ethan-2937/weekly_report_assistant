package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.po.SubmissionStatusPO;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class ReportContentFilter {
    private static final String STATUS_SUBMITTED = "已提交";
    private static final String STATUS_MISSING = "未提交";
    private static final String YES = "是";

    String buildSubmissionSummary(String week, List<SubmissionStatusPO> visibleRows) {
        return buildSubmissionSummary(week, visibleRows, "# 周报提交验证结果（授权范围）");
    }

    private String buildSubmissionSummary(String week, List<SubmissionStatusPO> visibleRows, String title) {
        List<SubmissionStatusPO> submitted = visibleRows.stream()
            .filter(row -> STATUS_SUBMITTED.equals(row.getStatus()))
            .toList();
        List<SubmissionStatusPO> missing = visibleRows.stream()
            .filter(row -> STATUS_MISSING.equals(row.getStatus()))
            .toList();
        StringBuilder builder = new StringBuilder();
        builder.append(title).append("\n\n");
        builder.append("- 统计周期：").append(week).append("。\n");
        builder.append("- 当前账号可见范围人数：").append(visibleRows.size()).append("。\n");
        builder.append("- 已提交人数：").append(submitted.size()).append("。\n");
        builder.append("- 未提交候选人数：").append(missing.size()).append("。\n\n");
        appendRowsTable(builder, "## 已提交名单", submitted, true);
        appendRowsTable(builder, "## 未提交候选", missing, false);
        builder.append("\n> 已按当前账号的数据权限过滤，仅展示授权范围内人员。\n");
        return builder.toString();
    }

    String filterManagerReport(String content, String week, List<SubmissionStatusPO> allRows, List<SubmissionStatusPO> visibleRows) {
        if (!hasText(content)) {
            return "";
        }
        Visibility visibility = new Visibility(allRows, visibleRows);
        StringBuilder builder = new StringBuilder();
        builder.append(firstTitle(content, week)).append("（授权范围）\n\n");
        builder.append("> 已按当前账号的数据权限过滤，仅展示授权范围内人员；完整报告请使用全权限账号查看。\n\n");
        builder.append(buildSubmissionSummary(week, visibleRows, "## 本周提交概览（授权范围）")).append("\n");

        String boss = filterNamedSection(content, visibility, "老板", "拍板", "协调事项", "需要支持", "求助");
        if (hasText(boss)) {
            builder.append("## 需老板拍板/协调事项（授权范围）\n\n").append(boss).append("\n");
        }

        String personBlocks = filterPersonBlocks(content, visibility);
        if (hasText(personBlocks)) {
            builder.append("## 授权范围内员工评价\n\n").append(personBlocks).append("\n");
        } else {
            String personTables = filterNamedSection(content, visibility, "员工五维评价", "每人工作总结", "效果评价", "虚实盘");
            if (hasText(personTables)) {
                builder.append("## 授权范围内员工评价\n\n").append(personTables).append("\n");
            }
        }

        String leaderSection = buildVisibleLeaderSection(visibleRows);
        if (hasText(leaderSection)) {
            builder.append(leaderSection).append("\n");
        }

        builder.append("## 数据权限说明\n\n");
        builder.append("- 本页只展示当前账号授权范围内的提交状态和评价内容。\n");
        builder.append("- 若需要调整可见人员或团队，请由管理员在“用户管理”中维护部门权限范围。\n");
        return builder.toString();
    }

    String filterAnalysisInput(String content, String week, List<SubmissionStatusPO> allRows, List<SubmissionStatusPO> visibleRows) {
        if (!hasText(content)) {
            return "";
        }
        Visibility visibility = new Visibility(allRows, visibleRows);
        StringBuilder builder = new StringBuilder();
        builder.append("# Weekly Report Analysis Pack（授权范围）\n\n");
        builder.append("- week_label: ").append(week).append("\n");
        builder.append("- visible_users: ").append(visibleRows.size()).append("\n");
        builder.append("- submitted_visible_users: ")
            .append(visibleRows.stream().filter(row -> STATUS_SUBMITTED.equals(row.getStatus())).count())
            .append("\n\n");

        String missingTable = filterMarkdownTableByHeader(content, "Missing Candidates", visibility);
        if (hasText(missingTable)) {
            builder.append("## Missing Candidates\n\n").append(missingTable).append("\n");
        }

        String submittedReports = filterReportBlocks(content, visibility);
        if (hasText(submittedReports)) {
            builder.append("## Submitted Reports\n\n").append(submittedReports).append("\n");
        }
        builder.append("> 已按当前账号的数据权限过滤原始分析包。\n");
        return builder.toString();
    }

    String toCsv(List<SubmissionStatusPO> rows) {
        StringBuilder builder = new StringBuilder("\ufeff提交状态,姓名,userid,部门,是否负责人候选,职务,周报部门,提交时间,report_id,模板,模板填写正确率,模板合规状态,模板缺失项,模板命中项,模板检查说明\n");
        for (SubmissionStatusPO row : rows) {
            builder.append(csv(row.getStatus())).append(',')
                .append(csv(row.getName())).append(',')
                .append(csv(row.getUserid())).append(',')
                .append(csv(row.getDept())).append(',')
                .append(csv(row.getLeaderCandidate())).append(',')
                .append(csv(row.getTitle())).append(',')
                .append(csv(row.getReportDept())).append(',')
                .append(csv(row.getSubmitTime())).append(',')
                .append(csv(row.getReportId())).append(',')
                .append(csv(row.getTemplateName())).append(',')
                .append(csv(formatRate(row))).append(',')
                .append(csv(row.getTemplateComplianceStatus())).append(',')
                .append(csv(String.join("、", safeList(row.getTemplateComplianceMissingFields())))).append(',')
                .append(csv(String.join("、", safeList(row.getTemplateCompliancePresentFields())))).append(',')
                .append(csv(row.getTemplateComplianceDetail())).append('\n');
        }
        return builder.toString();
    }

    private void appendRowsTable(StringBuilder builder, String title, List<SubmissionStatusPO> rows, boolean includeSubmitTime) {
        builder.append(title).append('\n');
        if (rows.isEmpty()) {
            builder.append("\n- 暂无。\n\n");
            return;
        }
        if (includeSubmitTime) {
            builder.append("\n| 姓名 | 部门 | 提交时间 | 模板填写正确率 | 缺失项 |\n|---|---|---|---|---|\n");
            for (SubmissionStatusPO row : rows) {
                builder.append("| ").append(cell(row.getName())).append(" | ")
                    .append(cell(row.getDept())).append(" | ")
                    .append(cell(row.getSubmitTime())).append(" | ")
                    .append(cell(formatRate(row))).append(" | ")
                    .append(cell(formatMissingFields(row))).append(" |\n");
            }
        } else {
            builder.append("\n| 姓名 | 部门 | 职务 |\n|---|---|---|\n");
            for (SubmissionStatusPO row : rows) {
                builder.append("| ").append(cell(row.getName())).append(" | ")
                    .append(cell(row.getDept())).append(" | ")
                    .append(cell(row.getTitle())).append(" |\n");
            }
        }
        builder.append('\n');
    }

    private String filterPersonBlocks(String content, Visibility visibility) {
        List<String> lines = lines(content);
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            if (line.matches("^###\\s+.+")) {
                List<String> block = new ArrayList<>();
                block.add(line);
                index += 1;
                while (index < lines.size() && !lines.get(index).matches("^###\\s+.+") && !lines.get(index).matches("^##\\s+.+")) {
                    block.add(lines.get(index));
                    index += 1;
                }
                String headingName = headingName(line);
                if (visibility.isVisibleName(headingName)) {
                    appendBlock(builder, block);
                }
                continue;
            }
            index += 1;
        }
        return builder.toString();
    }

    private String filterReportBlocks(String content, Visibility visibility) {
        List<String> lines = lines(content);
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            if (line.matches("^###\\s+Report\\s+\\d+:.+")) {
                List<String> block = new ArrayList<>();
                block.add(line);
                index += 1;
                while (index < lines.size() && !lines.get(index).matches("^###\\s+Report\\s+\\d+:.+")) {
                    block.add(lines.get(index));
                    index += 1;
                }
                if (visibility.hit(String.join("\n", block))) {
                    appendBlock(builder, block);
                }
                continue;
            }
            index += 1;
        }
        return builder.toString();
    }

    private String filterNamedSection(String content, Visibility visibility, String... keywords) {
        List<String> lines = lines(content);
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            if (line.matches("^##\\s+.+") && containsAny(line, keywords)) {
                List<String> section = new ArrayList<>();
                index += 1;
                while (index < lines.size() && !lines.get(index).matches("^##\\s+.+")) {
                    section.add(lines.get(index));
                    index += 1;
                }
                builder.append(filterLines(section, visibility)).append('\n');
                continue;
            }
            index += 1;
        }
        return builder.toString().trim();
    }

    private String filterMarkdownTableByHeader(String content, String headerKeyword, Visibility visibility) {
        List<String> lines = lines(content);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).contains(headerKeyword)) {
                continue;
            }
            int index = i + 1;
            while (index < lines.size() && !lines.get(index).trim().startsWith("|")) {
                index += 1;
            }
            List<String> table = new ArrayList<>();
            while (index < lines.size() && lines.get(index).trim().startsWith("|")) {
                table.add(lines.get(index));
                index += 1;
            }
            builder.append(filterTable(table, visibility));
        }
        return builder.toString();
    }

    private String filterLines(List<String> source, Visibility visibility) {
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < source.size()) {
            String line = source.get(index);
            if (line.trim().startsWith("|")) {
                List<String> table = new ArrayList<>();
                while (index < source.size() && source.get(index).trim().startsWith("|")) {
                    table.add(source.get(index));
                    index += 1;
                }
                builder.append(filterTable(table, visibility));
                continue;
            }
            if (visibility.hit(line)) {
                builder.append(line).append('\n');
            }
            index += 1;
        }
        return builder.toString();
    }

    private String filterTable(List<String> table, Visibility visibility) {
        if (table.size() < 2) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(table.get(0)).append('\n');
        builder.append(table.get(1)).append('\n');
        int count = 0;
        for (int i = 2; i < table.size(); i++) {
            if (visibility.hit(table.get(i))) {
                builder.append(table.get(i)).append('\n');
                count += 1;
            }
        }
        return count == 0 ? "" : builder.append('\n').toString();
    }

    private String buildVisibleLeaderSection(List<SubmissionStatusPO> visibleRows) {
        List<SubmissionStatusPO> leaders = visibleRows.stream()
            .filter(row -> YES.equals(row.getLeaderCandidate()))
            .toList();
        if (leaders.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("## 团队负责人履职检查（授权范围）\n\n");
        builder.append("| 姓名 | 部门 | 职务 | 提交状态 |\n|---|---|---|---|\n");
        for (SubmissionStatusPO row : leaders) {
            builder.append("| ").append(cell(row.getName())).append(" | ")
                .append(cell(row.getDept())).append(" | ")
                .append(cell(row.getTitle())).append(" | ")
                .append(cell(row.getStatus())).append(" |\n");
        }
        builder.append("\n> 负责人履职详细评价已按授权范围过滤；如需查看完整负责人检查，请使用全权限账号。\n");
        return builder.toString();
    }

    private String firstTitle(String content, String week) {
        return lines(content).stream()
            .filter(line -> line.matches("^#\\s+.+"))
            .findFirst()
            .orElse("# " + week + " 周报汇总与评价");
    }

    private String headingName(String heading) {
        String text = heading.replaceFirst("^#+\\s*", "").trim();
        return text.split("[｜|\\s]")[0].trim();
    }

    private boolean containsAny(String line, String... keywords) {
        for (String keyword : keywords) {
            if (line.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void appendBlock(StringBuilder builder, List<String> block) {
        for (String item : block) {
            builder.append(item).append('\n');
        }
        builder.append('\n');
    }

    private List<String> lines(String content) {
        return List.of(content.replace("\r\n", "\n").split("\n", -1));
    }

    private String csv(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String cell(String value) {
        return value == null ? "" : value.replace("|", "\\|");
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String formatRate(SubmissionStatusPO row) {
        Integer rate = row.getTemplateComplianceRate();
        return rate == null ? "-" : rate + "%";
    }

    private String formatMissingFields(SubmissionStatusPO row) {
        List<String> missing = safeList(row.getTemplateComplianceMissingFields());
        if (missing.isEmpty()) {
            return "无";
        }
        return String.join("、", missing);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static class Visibility {
        private final Set<String> visibleNames;
        private final Set<String> visibleUserIds;

        Visibility(List<SubmissionStatusPO> allRows, List<SubmissionStatusPO> visibleRows) {
            this.visibleNames = names(visibleRows);
            this.visibleUserIds = userIds(visibleRows);
        }

        boolean isVisibleName(String name) {
            return visibleNames.contains(name);
        }

        boolean hit(String line) {
            if (line == null || line.isBlank()) {
                return false;
            }
            return visibleNames.stream().anyMatch(line::contains)
                || visibleUserIds.stream().anyMatch(line::contains);
        }

        private static Set<String> names(List<SubmissionStatusPO> rows) {
            Set<String> values = new LinkedHashSet<>();
            for (SubmissionStatusPO row : rows) {
                if (row.getName() != null && !row.getName().isBlank()) {
                    values.add(row.getName().trim());
                }
            }
            return values;
        }

        private static Set<String> userIds(List<SubmissionStatusPO> rows) {
            Set<String> values = new LinkedHashSet<>();
            for (SubmissionStatusPO row : rows) {
                if (row.getUserid() != null && !row.getUserid().isBlank()) {
                    values.add(row.getUserid().trim());
                }
            }
            return values;
        }

    }
}
