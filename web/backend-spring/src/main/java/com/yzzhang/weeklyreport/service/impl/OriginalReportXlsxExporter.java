package com.yzzhang.weeklyreport.service.impl;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class OriginalReportXlsxExporter {
    private static final int MAX_CELL_CHARACTERS = 32_767;
    private static final float HEADER_HEIGHT = 62f;
    private static final float MIN_ROW_HEIGHT = 62f;
    private static final float MAX_ROW_HEIGHT = 409.5f;

    private static final String[] OUTCOMES_HEADERS = {
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
    };
    private static final double[] OUTCOMES_WIDTHS = {
        13, 31.8654, 4.3173, 5.7115, 41.5769, 24.875, 13, 255, 224.9519, 255, 13,
        31.2212, 35.375, 13, 29.7019, 33.9038, 35.375, 13, 4.3173, 175.7981, 5.7115, 7.1154
    };
    private static final String[] LEGACY_HEADERS = {
        "User ID", "工号", "填报人", "部门", "填报时间", "最后一次修改时间",
        "本周进展", "下周计划", "遇到的问题或想法", "备注：", "\n图片地址"
    };
    private static final double[] LEGACY_WIDTHS = {
        31.0192, 4.3173, 5.7115, 41.5769, 24.875, 13, 133.8558, 53.1923, 86.8269, 5.7115, 7.1154
    };

    private OriginalReportXlsxExporter() {
    }

    static int write(Path target, List<OriginalReportRow> rows) throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try {
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dataStyle = dataStyle(workbook);
            CellStyle indexStyle = indexStyle(workbook);
            List<OriginalReportRow> outcomes = rows.stream()
                .filter(row -> OriginalReportExportServiceImpl.OUTCOMES_TEMPLATE.equals(row.templateName()))
                .toList();
            List<OriginalReportRow> legacy = rows.stream()
                .filter(row -> OriginalReportExportServiceImpl.LEGACY_TEMPLATE.equals(row.templateName()))
                .toList();

            Sheet outcomesSheet = createSheet(
                workbook,
                OriginalReportExportServiceImpl.OUTCOMES_TEMPLATE,
                OUTCOMES_HEADERS,
                OUTCOMES_WIDTHS,
                headerStyle,
                true
            );
            writeOutcomes(outcomesSheet, outcomes, dataStyle, indexStyle);
            Sheet legacySheet = createSheet(
                workbook,
                OriginalReportExportServiceImpl.LEGACY_TEMPLATE,
                LEGACY_HEADERS,
                LEGACY_WIDTHS,
                headerStyle,
                false
            );
            writeLegacy(legacySheet, legacy, dataStyle);

            try (OutputStream output = Files.newOutputStream(target)) {
                workbook.write(output);
            }
            return outcomes.size() + legacy.size();
        } finally {
            workbook.close();
        }
    }

    private static Sheet createSheet(
        SXSSFWorkbook workbook,
        String name,
        String[] headers,
        double[] widths,
        CellStyle headerStyle,
        boolean plainFirstColumn
    ) {
        Sheet sheet = workbook.createSheet(name);
        sheet.setDefaultColumnWidth(9);
        Row header = sheet.createRow(0);
        header.setHeightInPoints(HEADER_HEIGHT);
        for (int index = 0; index < headers.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(headers[index]);
            if (!(plainFirstColumn && index == 0)) {
                cell.setCellStyle(headerStyle);
            }
            sheet.setColumnWidth(index, Math.min(255 * 256, (int) Math.round(widths[index] * 256)));
        }
        return sheet;
    }

    private static void writeOutcomes(
        Sheet sheet,
        List<OriginalReportRow> rows,
        CellStyle dataStyle,
        CellStyle indexStyle
    ) {
        for (int index = 0; index < rows.size(); index++) {
            OriginalReportRow source = rows.get(index);
            Map<String, String> fields = normalizedFields(source.fields());
            List<String> values = List.of(
                Integer.toString(index + 1), source.userId(), source.employeeNumber(), source.name(), source.department(),
                source.createdAt(), source.modifiedAt(),
                field(fields, "本周完成成果（写清楚本周期内已交付/已闭环的事项，逐条列出。）", "本周完成成果"),
                field(fields, "工时投入分析（按模块拆解本周时间占比）", "工时投入分析"),
                field(fields, "AI应用及效果"), field(fields, "个人分享（欢迎大家填写）", "个人分享"),
                field(fields, "技术/产品/销售同学必填。您归属于哪条产品线"),
                field(fields, "技术/产品/销售同学必填。您本周服务的客户名称是?"),
                field(fields, "技术/产品/销售同学必填。您本周服务的项目名称是?"),
                field(fields, "技术/产品同学必填。本周投入工时合计(天)"),
                field(fields, "技术/产品同学必填。本周投入工时(天)明细分布："),
                field(fields, "技术/产品/销售同学必填。本周您产生的差旅费用是?"),
                field(fields, "技术/产品/销售同学必填。本周您产生的招待费用是?"),
                field(fields, "图片"), field(fields, "下周计划（含交付时间）", "下周计划"),
                source.remark(), source.images()
            );
            writeRow(sheet, index + 1, values, OUTCOMES_WIDTHS, dataStyle, indexStyle);
        }
    }

    private static void writeLegacy(Sheet sheet, List<OriginalReportRow> rows, CellStyle dataStyle) {
        for (int index = 0; index < rows.size(); index++) {
            OriginalReportRow source = rows.get(index);
            Map<String, String> fields = normalizedFields(source.fields());
            List<String> values = List.of(
                source.userId(), source.employeeNumber(), source.name(), source.department(), source.createdAt(),
                source.modifiedAt(), field(fields, "本周进展"), field(fields, "下周计划"),
                field(fields, "遇到的问题或想法"), source.remark(), source.images()
            );
            writeRow(sheet, index + 1, values, LEGACY_WIDTHS, dataStyle, null);
        }
    }

    private static void writeRow(
        Sheet sheet,
        int rowIndex,
        List<String> values,
        double[] widths,
        CellStyle dataStyle,
        CellStyle firstColumnStyle
    ) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(estimatedHeight(values, widths));
        for (int column = 0; column < values.size(); column++) {
            Cell cell = row.createCell(column);
            cell.setCellValue(safeCell(values.get(column)));
            cell.setCellStyle(column == 0 && firstColumnStyle != null ? firstColumnStyle : dataStyle);
        }
    }

    private static Map<String, String> normalizedFields(Map<String, String> fields) {
        Map<String, String> result = new LinkedHashMap<>();
        fields.forEach((key, value) -> result.putIfAbsent(normalizeLabel(key), value));
        return result;
    }

    private static String field(Map<String, String> fields, String... aliases) {
        for (String alias : aliases) {
            String value = fields.get(normalizeLabel(alias));
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    private static String normalizeLabel(String value) {
        return value == null ? "" : value.strip()
            .replace('？', '?')
            .replace('：', ':')
            .replaceAll("\\s+", "")
            .toLowerCase(Locale.ROOT);
    }

    private static String safeCell(String value) {
        String normalized = value == null ? "" : value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        if (normalized.length() > MAX_CELL_CHARACTERS) {
            throw new IllegalStateException("original report cell exceeds the XLSX limit");
        }
        return normalized;
    }

    private static float estimatedHeight(List<String> values, double[] widths) {
        int maxLines = 1;
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index) == null ? "" : values.get(index);
            int charactersPerLine = Math.max(4, (int) Math.floor(widths[index]));
            int lines = 0;
            for (String part : value.split("\\R", -1)) {
                lines += Math.max(1, (int) Math.ceil((double) part.codePointCount(0, part.length()) / charactersPerLine));
            }
            maxLines = Math.max(maxLines, lines);
        }
        return Math.min(MAX_ROW_HEIGHT, Math.max(MIN_ROW_HEIGHT, maxLines * 17.25f));
    }

    private static CellStyle headerStyle(SXSSFWorkbook workbook) {
        CellStyle style = borderedStyle(workbook);
        Font font = workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 14);
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static CellStyle dataStyle(SXSSFWorkbook workbook) {
        CellStyle style = borderedStyle(workbook);
        Font font = workbook.createFont();
        font.setFontName("Calibri");
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.LEFT);
        return style;
    }

    private static CellStyle borderedStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle indexStyle(SXSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
}
