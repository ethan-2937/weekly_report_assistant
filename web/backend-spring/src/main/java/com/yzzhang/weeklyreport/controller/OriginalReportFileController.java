package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.service.OriginalReportExportService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
public class OriginalReportFileController {
    private static final MediaType XLSX = MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final OriginalReportExportService exportService;

    public OriginalReportFileController(OriginalReportExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/{week}/original-reports/download")
    public ResponseEntity<Resource> download(@PathVariable String week) {
        Path xlsx = exportService.exportXlsx(week);
        String filename = "周报明细_" + week + ".xlsx";
        return ResponseEntity.ok()
            .contentType(XLSX)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString()
            )
            .body(new FileSystemResource(xlsx));
    }
}
