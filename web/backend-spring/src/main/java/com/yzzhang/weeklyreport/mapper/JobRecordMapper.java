package com.yzzhang.weeklyreport.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.po.JobRecordPO;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Repository
public class JobRecordMapper {
    private final ProjectPathConfig pathConfig;
    private final ObjectMapper objectMapper;

    public JobRecordMapper(ProjectPathConfig pathConfig, ObjectMapper objectMapper) {
        this.pathConfig = pathConfig;
        this.objectMapper = objectMapper;
    }

    public void insertLog(JobRecordPO job) {
        try {
            Path logDir = pathConfig.projectRoot().resolve("logs");
            Files.createDirectories(logDir);
            Path log = logDir.resolve("jobs.jsonl");
            String json = objectMapper.writeValueAsString(job) + System.lineSeparator();
            Files.writeString(log, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Logging must not break the user's weekly report workflow.
        }
    }
}
