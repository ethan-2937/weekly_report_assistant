package com.yzzhang.weeklyreport.mapper;

import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectDetailMapperTest {
    @TempDir
    Path tempDir;

    @Test
    void readsQuotedProjectDetailCsvWithHiddenPermissionFields() throws Exception {
        Path csv = tempDir.resolve("output/2026-W29/exports/project_details.csv");
        Files.createDirectories(csv.getParent());
        Files.writeString(csv, """
            \ufeff姓名,userid,部门,序号,产品线,客户名称,项目名称,本周投入工时（天）,本周差旅费用,本周招待费用
            测试员工甲,test-user-001,虚构研发部,1,虚构产品线,"虚构客户甲,虚构客户乙","虚构项目甲,虚构项目乙",3.5,120,0
            """, StandardCharsets.UTF_8);
        WeeklyReportProperties properties = new WeeklyReportProperties();
        properties.setProjectRoot(tempDir.toString());
        ProjectDetailMapper mapper = new ProjectDetailMapper(new ProjectPathConfig(properties));

        var rows = mapper.selectByWeek("2026-W29");

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getUserid()).isEqualTo("test-user-001");
        assertThat(rows.getFirst().getCustomerName()).isEqualTo("虚构客户甲,虚构客户乙");
        assertThat(rows.getFirst().getProjectName()).isEqualTo("虚构项目甲,虚构项目乙");
    }

    @Test
    void missingLegacyFileReturnsEmptyRows() {
        WeeklyReportProperties properties = new WeeklyReportProperties();
        properties.setProjectRoot(tempDir.toString());
        ProjectDetailMapper mapper = new ProjectDetailMapper(new ProjectPathConfig(properties));

        assertThat(mapper.selectByWeek("2026-W28")).isEmpty();
    }
}
