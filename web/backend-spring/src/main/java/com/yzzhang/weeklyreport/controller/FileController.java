package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.service.WeeklyReportService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final WeeklyReportService weeklyReportService;

    public FileController(WeeklyReportService weeklyReportService) {
        this.weeklyReportService = weeklyReportService;
    }

    @GetMapping("/{week}/submission-status/download")
    public ResponseEntity<Resource> downloadSubmissionStatus(@PathVariable String week) {
        Path csv = weeklyReportService.getSubmissionStatusCsv(week);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"submission_status_" + week + ".csv\"")
            .body(new FileSystemResource(csv));
    }
}
