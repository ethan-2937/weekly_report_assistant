package com.yzzhang.weeklyreport.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.common.ExportUnavailableException;
import com.yzzhang.weeklyreport.mapper.WeekFileMapper;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.service.ReportPermissionService;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OriginalReportExportServiceImplTest {
    private static final String WEEK = "2026-W29";

    @TempDir
    Path tempDir;

    private WeekFileMapper weekFileMapper;
    private ReportPermissionService permissionService;
    private ObjectMapper objectMapper;
    private OriginalReportExportServiceImpl service;
    private Path allReports;
    private Path contacts;

    @BeforeEach
    void setUp() throws Exception {
        weekFileMapper = mock(WeekFileMapper.class);
        permissionService = mock(ReportPermissionService.class);
        objectMapper = new ObjectMapper();
        allReports = tempDir.resolve("output/2026-W29/raw/all_reports.json");
        contacts = tempDir.resolve("output/contacts/users.json");
        Files.createDirectories(allReports.getParent());
        Files.createDirectories(contacts.getParent());
        when(weekFileMapper.allReportsPath(WEEK)).thenReturn(allReports);
        when(weekFileMapper.contactsUsersPath()).thenReturn(contacts);
        when(permissionService.currentPermission()).thenReturn(fullPermission());
        when(permissionService.filterRows(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new OriginalReportExportServiceImpl(weekFileMapper, permissionService, objectMapper);
    }

    @Test
    void exportsReferenceSheetsWithoutUnsupportedColumnsAndPreservesDuplicates() throws Exception {
        String exactContent = "=SUM(1,1)\n第二行内容  ";
        writeReports(List.of(
            report("new-001", "test-user-001", "示例员工甲", "虚构研发部", outcomesFields(exactContent)),
            report("new-002", "test-user-001", "示例员工甲", "虚构研发部", outcomesFields("第二次提交")),
            legacyReport("legacy-001", "test-user-002", "示例员工乙", "虚构产品部")
        ));
        objectMapper.writeValue(contacts.toFile(), List.of(Map.of(
            "userid", "test-user-001",
            "job_number", "EMP-001"
        )));

        Path exported = service.exportXlsx(WEEK);

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(exported))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            Sheet outcomes = workbook.getSheet(OriginalReportExportServiceImpl.OUTCOMES_TEMPLATE);
            Sheet legacy = workbook.getSheet(OriginalReportExportServiceImpl.LEGACY_TEMPLATE);
            assertThat(outcomes.getLastRowNum()).isEqualTo(2);
            assertThat(legacy.getLastRowNum()).isEqualTo(1);
            assertThat(headerValues(outcomes)).containsExactly(
                "", "User ID", "工号", "填报人", "部门", "填报时间", "最后一次修改时间",
                "本周完成成果（写清楚本周期内已交付/已闭环的事项，逐条列出。）",
                "工时投入分析（按模块拆解本周时间占比）", "AI应用及效果", "个人分享（欢迎大家填写）",
                "技术/产品/销售同学必填。您归属于哪条产品线",
                "技术/产品/销售同学必填。您本周服务的客户名称是?",
                "技术/产品/销售同学必填。您本周服务的项目名称是?",
                "技术/产品同学必填。本周投入工时合计(天)",
                "技术/产品同学必填。本周投入工时(天)明细分布：",
                "技术/产品/销售同学必填。本周您产生的差旅费用是?",
                "技术/产品/销售同学必填。本周您产生的招待费用是?",
                "图片", "下周计划（含交付时间）", "备注：", "\n图片地址"
            );
            assertThat(headerValues(outcomes)).doesNotContain(
                "地址", "评论信息", "评论数", "点赞数", "已读人数", "未读人数", "已读率"
            );
            assertThat(outcomes.getRow(1).getCell(2).getStringCellValue()).isEqualTo("EMP-001");
            assertThat(outcomes.getRow(1).getCell(7).getCellType()).isEqualTo(CellType.STRING);
            assertThat(outcomes.getRow(1).getCell(7).getStringCellValue()).isEqualTo(exactContent);
            assertThat(outcomes.getRow(1).getCell(1).getStringCellValue()).isEqualTo("test-user-001");
            assertThat(outcomes.getRow(2).getCell(1).getStringCellValue()).isEqualTo("test-user-001");
            assertThat(outcomes.getRow(0).getHeightInPoints()).isEqualTo(62f);
            assertThat(outcomes.getRow(0).getCell(1).getCellStyle().getFontIndex())
                .isEqualTo(outcomes.getRow(0).getCell(2).getCellStyle().getFontIndex());
        }
    }

    @Test
    void exportsOnlyRowsApprovedByTheExistingPermissionService() throws Exception {
        writeReports(List.of(
            report("new-001", "test-user-001", "示例员工甲", "虚构研发部", outcomesFields("可见正文")),
            report("new-002", "test-user-002", "示例员工乙", "虚构销售部", outcomesFields("隐藏正文"))
        ));
        when(permissionService.currentPermission()).thenReturn(
            new ReportPermissionService.ReportPermission(false, List.of("部门:虚构研发部"))
        );
        when(permissionService.filterRows(any(), any())).thenAnswer(invocation -> {
            List<SubmissionStatusPO> rows = invocation.getArgument(0);
            return rows.stream().filter(row -> "虚构研发部".equals(row.getDept())).toList();
        });

        Path exported = service.exportXlsx(WEEK);

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(exported))) {
            String text = workbookText(workbook);
            assertThat(text).contains("示例员工甲", "可见正文")
                .doesNotContain("示例员工乙", "隐藏正文", "new-001", "new-002");
        }
    }

    @Test
    void rejectsMissingOrIncompleteFullSnapshotsInsteadOfReturningPartialData() throws Exception {
        assertThatThrownBy(() -> service.exportXlsx(WEEK)).isInstanceOf(ExportUnavailableException.class);

        writeReports(List.of(report("new-001", "", "示例员工甲", "虚构研发部", outcomesFields("正文"))));
        assertThatThrownBy(() -> service.exportXlsx(WEEK)).isInstanceOf(ExportUnavailableException.class);
    }

    @Test
    void w28CanExportTheExistingPrimarySnapshotWithoutCompletenessDetection() throws Exception {
        Path w28Primary = tempDir.resolve("output/2026-W28/raw/reports.json");
        Files.createDirectories(w28Primary.getParent());
        objectMapper.writeValue(w28Primary.toFile(), List.of(
            report("new-001", "test-user-001", "示例员工甲", "虚构研发部", outcomesFields("W28已有正文"))
        ));
        when(weekFileMapper.allReportsPath("2026-W28")).thenReturn(
            tempDir.resolve("output/2026-W28/raw/all_reports.json")
        );
        when(weekFileMapper.rawReportsPath("2026-W28")).thenReturn(w28Primary);

        Path exported = service.exportXlsx("2026-W28");

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(exported))) {
            assertThat(workbook.getSheet(OriginalReportExportServiceImpl.OUTCOMES_TEMPLATE).getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheet(OriginalReportExportServiceImpl.LEGACY_TEMPLATE).getLastRowNum()).isZero();
            assertThat(workbookText(workbook)).contains("示例员工甲", "W28已有正文");
        }
    }

    @Test
    void checksPermissionBeforeReadingThePrivateSnapshot() {
        when(permissionService.currentPermission()).thenThrow(new AccessDeniedException("没有周报范围"));

        assertThatThrownBy(() -> service.exportXlsx(WEEK))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("没有周报范围");
        verify(weekFileMapper, never()).allReportsPath(WEEK);
    }

    private void writeReports(List<Map<String, Object>> reports) throws Exception {
        objectMapper.writeValue(allReports.toFile(), reports);
    }

    private Map<String, Object> report(
        String reportId,
        String userId,
        String name,
        String department,
        List<Map<String, String>> fields
    ) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("report_id", reportId);
        report.put("template_name", OriginalReportExportServiceImpl.OUTCOMES_TEMPLATE);
        report.put("creator_id", userId);
        report.put("creator_name", name);
        report.put("dept_name", department);
        report.put("create_time", 1_752_470_400_000L);
        report.put("modified_time", 1_752_474_000_000L);
        report.put("contents", fields);
        report.put("images", List.of());
        report.put("remark", "");
        return report;
    }

    private Map<String, Object> legacyReport(String reportId, String userId, String name, String department) {
        Map<String, Object> report = report(
            reportId,
            userId,
            name,
            department,
            List.of(
                field("本周进展", "虚构旧模板进展"),
                field("下周计划", "虚构旧模板计划"),
                field("遇到的问题或想法", "虚构想法")
            )
        );
        report.put("template_name", OriginalReportExportServiceImpl.LEGACY_TEMPLATE);
        return report;
    }

    private List<Map<String, String>> outcomesFields(String content) {
        return List.of(
            field("本周完成成果（写清楚本周期内已交付/已闭环的事项，逐条列出。）", content),
            field("工时投入分析（按模块拆解本周时间占比）", "虚构工时分布"),
            field("AI应用及效果", "虚构AI应用"),
            field("下周计划（含交付时间）", "2026-07-24交付虚构文档")
        );
    }

    private Map<String, String> field(String key, String value) {
        return Map.of("key", key, "value", value);
    }

    private List<String> headerValues(Sheet sheet) {
        Row row = sheet.getRow(0);
        List<String> values = new ArrayList<>();
        for (int index = 0; index < row.getLastCellNum(); index++) {
            values.add(row.getCell(index).getStringCellValue());
        }
        return values;
    }

    private String workbookText(Workbook workbook) {
        StringBuilder text = new StringBuilder();
        workbook.forEach(sheet -> sheet.forEach(row -> row.forEach(cell -> text.append(cell).append('\n'))));
        return text.toString();
    }

    private ReportPermissionService.ReportPermission fullPermission() {
        return new ReportPermissionService.ReportPermission(true, List.of());
    }
}
