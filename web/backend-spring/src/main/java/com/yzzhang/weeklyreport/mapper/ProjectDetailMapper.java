package com.yzzhang.weeklyreport.mapper;

import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.po.ProjectDetailPO;
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
public class ProjectDetailMapper {
    private final ProjectPathConfig pathConfig;

    public ProjectDetailMapper(ProjectPathConfig pathConfig) {
        this.pathConfig = pathConfig;
    }

    public List<ProjectDetailPO> selectByWeek(String week) {
        Path path = pathConfig.outputRoot().resolve(week).resolve("exports").resolve("project_details.csv").normalize();
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return List.of();
            }
            List<String> headers = CsvUtils.parseLine(CsvUtils.stripBom(lines.get(0)));
            List<ProjectDetailPO> rows = new ArrayList<>();
            for (int index = 1; index < lines.size(); index++) {
                if (lines.get(index).isBlank()) {
                    continue;
                }
                List<String> values = CsvUtils.parseLine(lines.get(index));
                Map<String, String> row = new HashMap<>();
                for (int column = 0; column < headers.size(); column++) {
                    row.put(headers.get(column), column < values.size() ? values.get(column) : "");
                }
                rows.add(toPO(row));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("project detail data unavailable");
        }
    }

    private ProjectDetailPO toPO(Map<String, String> row) {
        ProjectDetailPO po = new ProjectDetailPO();
        po.setName(row.getOrDefault("姓名", ""));
        po.setUserid(row.getOrDefault("userid", ""));
        po.setDepartment(row.getOrDefault("部门", ""));
        po.setProductLine(row.getOrDefault("产品线", ""));
        po.setCustomerName(row.getOrDefault("客户名称", ""));
        po.setProjectName(row.getOrDefault("项目名称", ""));
        po.setInvestedDays(row.getOrDefault("本周投入工时（天）", ""));
        po.setTravelExpense(row.getOrDefault("本周差旅费用", ""));
        po.setHospitalityExpense(row.getOrDefault("本周招待费用", ""));
        return po;
    }
}
