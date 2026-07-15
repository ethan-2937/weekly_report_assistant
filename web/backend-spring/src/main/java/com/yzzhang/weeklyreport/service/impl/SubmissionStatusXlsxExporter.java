package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Creates a regular OOXML workbook without exposing identifiers or report text. */
final class SubmissionStatusXlsxExporter {
    private static final String SHEET_NAME = "\u63d0\u4ea4\u72b6\u6001";
    private static final String[] HEADERS = {
        "\u63d0\u4ea4\u72b6\u6001",
        "\u59d3\u540d",
        "\u90e8\u95e8",
        "\u662f\u5426\u8d1f\u8d23\u4eba",
        "\u804c\u52a1",
        "\u5468\u62a5\u90e8\u95e8",
        "\u63d0\u4ea4\u65f6\u95f4",
        "\u6a21\u677f",
        "\u6a21\u677f\u586b\u5199\u6b63\u786e\u7387",
        "\u6a21\u677f\u5408\u89c4\u72b6\u6001",
        "\u6a21\u677f\u7f3a\u5931\u9879",
        "\u6a21\u677f\u547d\u4e2d\u9879",
        "\u6a21\u677f\u68c0\u67e5\u8bf4\u660e"
    };

    private SubmissionStatusXlsxExporter() {
    }

    static void write(Path target, List<SubmissionStatusPO> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            CellStyle headerStyle = headerStyle(workbook);
            Row header = sheet.createRow(0);
            for (int index = 0; index < HEADERS.length; index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(HEADERS[index]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(index, columnWidth(index));
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                writeRow(sheet.createRow(rowIndex + 1), rows.get(rowIndex));
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, Math.max(0, rows.size()), 0, HEADERS.length - 1
            ));
            try (OutputStream output = Files.newOutputStream(target)) {
                workbook.write(output);
            }
        }
    }

    private static CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setAlignment(org.apache.poi.ss.usermodel.HorizontalAlignment.LEFT);
        style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static void writeRow(Row target, SubmissionStatusPO row) {
        int column = 0;
        put(target, column++, row.getStatus());
        put(target, column++, row.getName());
        put(target, column++, row.getDept());
        put(target, column++, row.getLeaderCandidate());
        put(target, column++, row.getTitle());
        put(target, column++, row.getReportDept());
        put(target, column++, row.getSubmitTime());
        put(target, column++, row.getTemplateName());
        put(target, column++, formatRate(row.getTemplateComplianceRate()));
        put(target, column++, row.getTemplateComplianceStatus());
        put(target, column++, join(row.getTemplateComplianceMissingFields()));
        put(target, column++, join(row.getTemplateCompliancePresentFields()));
        put(target, column, row.getTemplateComplianceDetail());
    }

    private static void put(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private static String formatRate(Integer rate) {
        return rate == null ? "-" : rate + "%";
    }

    private static String join(List<String> values) {
        return values == null ? "" : String.join("\u3001", values);
    }

    private static int columnWidth(int index) {
        return switch (index) {
            case 1, 2, 3, 4, 5, 7, 9 -> 22 * 256;
            case 10, 11, 12 -> 34 * 256;
            default -> 18 * 256;
        };
    }
}
