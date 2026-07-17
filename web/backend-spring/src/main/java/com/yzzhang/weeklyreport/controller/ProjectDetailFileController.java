package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.service.ProjectDetailService;
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
public class ProjectDetailFileController {
    private final ProjectDetailService projectDetailService;

    public ProjectDetailFileController(ProjectDetailService projectDetailService) {
        this.projectDetailService = projectDetailService;
    }

    @GetMapping("/{week}/project-details/download")
    public ResponseEntity<Resource> download(@PathVariable String week) {
        Path xlsx = projectDetailService.exportXlsx(week);
        String filename = "项目明细表_" + week + ".xlsx";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString()
            )
            .body(new FileSystemResource(xlsx));
    }
}
