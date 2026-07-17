package com.yzzhang.weeklyreport.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yzzhang.weeklyreport.mapper.WeekFileMapper;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemplateComplianceServiceImplTest {
    private static final String WEEK = "2026-W29";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void usesElevenFieldsForProductAndTechnicalTitles() throws Exception {
        Path reports = writeReports(Map.of(
            "report-product", projectFields(false),
            "report-technical", projectFields(true)
        ));
        SubmissionStatusPO product = row("report-product", "产品经理");
        SubmissionStatusPO technical = row("report-technical", "Java开发工程师");

        service(reports).enrich(WEEK, List.of(product, technical));

        assertThat(product.getTemplateComplianceRate()).isEqualTo(82);
        assertThat(product.getTemplateComplianceMissingFields()).containsExactly(
            "本周投入工时合计（天）", "本周投入工时（天）明细分布"
        );
        assertThat(product.getTemplateComplianceDetail()).contains("技术/产品岗位").contains("11项");
        assertThat(technical.getTemplateComplianceRate()).isEqualTo(100);
        assertThat(technical.getTemplateComplianceDetail()).contains("技术/产品岗位").contains("11项");
    }

    @Test
    void usesNineFieldsForSalesAndTreatsZeroExpensesAsFilled() throws Exception {
        Path reports = writeReports(Map.of("report-sales", projectFields(false)));
        SubmissionStatusPO sales = row("report-sales", "销售经理");

        service(reports).enrich(WEEK, List.of(sales));

        assertThat(sales.getTemplateComplianceRate()).isEqualTo(100);
        assertThat(sales.getTemplateComplianceMissingFields()).isEmpty();
        assertThat(sales.getTemplateComplianceDetail()).contains("销售岗位").contains("9项");
    }

    @Test
    void keepsFourUniversalFieldsForOtherTitles() throws Exception {
        Path reports = writeReports(Map.of("report-general", universalFields()));
        SubmissionStatusPO general = row("report-general", "行政专员");

        service(reports).enrich(WEEK, List.of(general));

        assertThat(general.getTemplateComplianceRate()).isEqualTo(100);
        assertThat(general.getTemplateCompliancePresentFields()).hasSize(4);
        assertThat(general.getTemplateComplianceDetail()).contains("通用岗位").contains("4项");
    }

    @Test
    void reportsBlankConditionalFieldInSalesPercentage() throws Exception {
        Map<String, String> fields = projectFields(false);
        fields.put("技术/产品/销售同学必填。您本周服务的客户名称是？", "  ");
        Path reports = writeReports(Map.of("report-sales-missing", fields));
        SubmissionStatusPO sales = row("report-sales-missing", "商务经理");

        service(reports).enrich(WEEK, List.of(sales));

        assertThat(sales.getTemplateComplianceRate()).isEqualTo(89);
        assertThat(sales.getTemplateComplianceMissingFields()).containsExactly("客户名称");
        assertThat(sales.getTemplateComplianceStatus()).isEqualTo("需补充");
    }

    private TemplateComplianceServiceImpl service(Path reports) {
        WeekFileMapper fileMapper = mock(WeekFileMapper.class);
        when(fileMapper.rawReportsPath(WEEK)).thenReturn(reports);
        return new TemplateComplianceServiceImpl(fileMapper, objectMapper);
    }

    private Path writeReports(Map<String, Map<String, String>> valuesByReport) throws Exception {
        ArrayNode reports = objectMapper.createArrayNode();
        valuesByReport.forEach((reportId, fields) -> {
            ObjectNode report = reports.addObject();
            report.put("report_id", reportId);
            report.put("creator_id", "test-" + reportId);
            report.put("create_time", 100);
            ArrayNode contents = report.putArray("contents");
            fields.forEach((key, value) -> {
                ObjectNode field = contents.addObject();
                field.put("key", key);
                field.put("value", value);
            });
        });
        Path path = tempDir.resolve("reports.json");
        objectMapper.writeValue(path.toFile(), reports);
        return path;
    }

    private SubmissionStatusPO row(String reportId, String title) {
        SubmissionStatusPO row = new SubmissionStatusPO();
        row.setStatus("已提交");
        row.setReportId(reportId);
        row.setTitle(title);
        return row;
    }

    private Map<String, String> projectFields(boolean includeWorkdays) {
        Map<String, String> fields = universalFields();
        fields.put("技术/产品/销售同学必填。您归属于哪条产品线", "虚构产品线");
        fields.put("技术/产品/销售同学必填。您本周服务的客户名称是？", "虚构客户");
        fields.put("技术/产品/销售同学必填。您本周服务的项目名称是？", "虚构项目");
        if (includeWorkdays) {
            fields.put("技术/产品同学必填。本周投入工时合计（天）", "3.5");
            fields.put("技术/产品同学必填。本周投入工时（天）明细分布", "虚构项目3.5天");
        }
        fields.put("技术/产品/销售同学必填。本周您产生的差旅费用是？", "0");
        fields.put("技术/产品/销售同学必填。本周您产生的招待费用是？", "0");
        return fields;
    }

    private Map<String, String> universalFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("本周完成成果", "完成虚构交付物");
        fields.put("工时投入分析", "虚构任务100%");
        fields.put("AI应用及效果", "虚构工具用于测试并节省时间");
        fields.put("下周计划（含交付时间）", "下周三完成虚构文档");
        return fields;
    }
}
