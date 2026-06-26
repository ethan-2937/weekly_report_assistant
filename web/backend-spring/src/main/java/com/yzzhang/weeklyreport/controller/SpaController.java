package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class SpaController {
    private final ProjectPathConfig pathConfig;

    public SpaController(ProjectPathConfig pathConfig) {
        this.pathConfig = pathConfig;
    }

    @GetMapping("/")
    public ResponseEntity<?> index() {
        Path index = pathConfig.frontendDist().resolve("index.html");
        if (!Files.exists(index)) {
            return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body("Vue frontend is not built yet. Run: cd web/frontend && npm install && npm run build");
        }
        Resource resource = new FileSystemResource(index);
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
            .body(resource);
    }
}
