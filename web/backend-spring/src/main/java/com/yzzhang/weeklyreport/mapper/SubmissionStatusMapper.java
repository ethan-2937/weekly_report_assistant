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
        po.setStatus(row.getOrDefault("????", ""));
        po.setName(row.getOrDefault("??", ""));
        po.setUserid(row.getOrDefault("userid", ""));
        po.setDept(row.getOrDefault("??", ""));
        po.setLeaderCandidate(row.getOrDefault("???????", ""));
        po.setTitle(row.getOrDefault("??", ""));
        po.setReportDept(row.getOrDefault("????", ""));
        po.setSubmitTime(row.getOrDefault("????", ""));
        po.setReportId(row.getOrDefault("report_id", ""));
        po.setTemplateName(row.getOrDefault("??", ""));
        return po;
    }
}
