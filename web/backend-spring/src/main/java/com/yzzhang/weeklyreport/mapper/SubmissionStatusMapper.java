package com.yzzhang.weeklyreport.mapper;

import com.yzzhang.weeklyreport.common.ResourceNotFoundException;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.util.CsvUtils;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SubmissionStatusMapper {
    private static final String HEADER_STATUS = "\u63d0\u4ea4\u72b6\u6001";
    private static final String HEADER_NAME = "\u59d3\u540d";
    private static final String HEADER_DEPT = "\u90e8\u95e8";
    private static final String HEADER_LEADER_CANDIDATE = "\u662f\u5426\u8d1f\u8d23\u4eba\u5019\u9009";
    private static final String HEADER_TITLE = "\u804c\u52a1";
    private static final String HEADER_REPORT_DEPT = "\u5468\u62a5\u90e8\u95e8";
    private static final String HEADER_SUBMIT_TIME = "\u63d0\u4ea4\u65f6\u95f4";
    private static final String HEADER_TEMPLATE = "\u6a21\u677f";

    private final ProjectPathConfig pathConfig;

    public SubmissionStatusMapper(ProjectPathConfig pathConfig) {
        this.pathConfig = pathConfig;
    }

    public List<SubmissionStatusPO> selectByWeek(String week) {
        Path csv = csvPath(week);
        if (!Files.exists(csv)) {
            throw new ResourceNotFoundException("submission_status.csv not found for " + week);
        }
        try {
            List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return List.of();
            }
            List<String> headers = CsvUtils.parseLine(CsvUtils.stripBom(lines.get(0)));
            List<SubmissionStatusPO> result = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) {
                    continue;
                }
                List<String> values = CsvUtils.parseLine(lines.get(i));
                Map<String, String> row = new HashMap<>();
                for (int j = 0; j < headers.size(); j++) {
                    row.put(headers.get(j), j < values.size() ? values.get(j) : "");
                }
                result.add(toPO(row));
            }
            return result;
        } catch (IOException e) {
            throw new ResourceNotFoundException("failed to read submission_status.csv: " + e.getMessage());
        }
    }

    public Path csvPath(String week) {
        return pathConfig.outputRoot().resolve(week).resolve("exports").resolve("submission_status.csv").normalize();
    }

    private SubmissionStatusPO toPO(Map<String, String> row) {
        SubmissionStatusPO po = new SubmissionStatusPO();
        po.setStatus(row.getOrDefault(HEADER_STATUS, ""));
        po.setName(row.getOrDefault(HEADER_NAME, ""));
        po.setUserid(row.getOrDefault("userid", ""));
        po.setDept(row.getOrDefault(HEADER_DEPT, ""));
        po.setLeaderCandidate(row.getOrDefault(HEADER_LEADER_CANDIDATE, ""));
        po.setTitle(row.getOrDefault(HEADER_TITLE, ""));
        po.setReportDept(row.getOrDefault(HEADER_REPORT_DEPT, ""));
        po.setSubmitTime(row.getOrDefault(HEADER_SUBMIT_TIME, ""));
        po.setReportId(row.getOrDefault("report_id", ""));
        po.setTemplateName(row.getOrDefault(HEADER_TEMPLATE, ""));
        return po;
    }
}
