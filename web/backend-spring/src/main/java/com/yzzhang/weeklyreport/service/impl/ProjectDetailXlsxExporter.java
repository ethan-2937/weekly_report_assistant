package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.vo.ProjectDetailVO;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

final class ProjectDetailXlsxExporter {
    private static final String[] HEADERS = {
        "序号", "姓名", "产品线", "客户名称", "项目名称", "本周投入工时（天）", "本周差旅费用", "本周招待费用"
    };
    private static final int[] WIDTHS = {9, 14, 16, 18, 26, 18, 17, 17};
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private ProjectDetailXlsxExporter() {
    }

    static void write(Path target, List<ProjectDetailVO> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("项目明细表");
            sheet.setDisplayGridlines(false);
            sheet.createFreezePane(0, 2);

            CellStyle titleStyle = titleStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = dataStyle(workbook, "@");
            CellStyle daysStyle = dataStyle(workbook, "0.0#");
            CellStyle moneyStyle = dataStyle(workbook, "#,##0.00");

            Row title = sheet.createRow(0);
            title.setHeightInPoints(33);
            title.createCell(0).setCellValue("项目明细表");
            title.getCell(0).setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, HEADERS.length - 1));

            Row header = sheet.createRow(1);
            header.setHeightInPoints(24.75f);
            for (int column = 0; column < HEADERS.length; column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(HEADERS[column]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(column, WIDTHS[column] * 256);
            }

            for (int index = 0; index < rows.size(); index++) {
                ProjectDetailVO detail = rows.get(index);
                Row row = sheet.createRow(index + 2);
                row.setHeightInPoints(22);
                putNumber(row, 0, BigDecimal.valueOf(detail.getSequence()), textStyle);
                putText(row, 1, detail.getName(), textStyle);
                putText(row, 2, detail.getProductLine(), textStyle);
                putText(row, 3, detail.getCustomerName(), textStyle);
                putText(row, 4, detail.getProjectName(), textStyle);
                putDecimalOrText(row, 5, detail.getInvestedDays(), daysStyle, textStyle);
                putDecimalOrText(row, 6, detail.getTravelExpense(), moneyStyle, textStyle);
                putDecimalOrText(row, 7, detail.getHospitalityExpense(), moneyStyle, textStyle);
            }
            try (OutputStream output = Files.newOutputStream(target)) {
                workbook.write(output);
            }
        }
    }

    private static CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 12);
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private static CellStyle headerStyle(Workbook workbook) {
        XSSFCellStyle style = (XSSFCellStyle) dataStyle(workbook, "@");
        Font font = workbook.createFont();
        font.setFontName("等线");
        font.setFontHeightInPoints((short) 9);
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new Color(132, 183, 254), new DefaultIndexedColorMap()));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static CellStyle dataStyle(Workbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontName("等线");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.BLACK.getIndex());
        style.setBottomBorderColor(IndexedColors.BLACK.getIndex());
        style.setLeftBorderColor(IndexedColors.BLACK.getIndex());
        style.setRightBorderColor(IndexedColors.BLACK.getIndex());
        return style;
    }

    private static void putText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void putDecimalOrText(Row row, int column, String value, CellStyle numberStyle, CellStyle textStyle) {
        String normalized = value == null ? "" : value.trim();
        if (NUMBER.matcher(normalized).matches()) {
            putNumber(row, column, new BigDecimal(normalized), numberStyle);
        } else {
            putText(row, column, normalized, textStyle);
        }
    }

    private static void putNumber(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value.doubleValue());
        cell.setCellStyle(style);
    }
}
