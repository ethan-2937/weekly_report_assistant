package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.mapper.ProjectDetailMapper;
import com.yzzhang.weeklyreport.mapper.SubmissionStatusMapper;
import com.yzzhang.weeklyreport.po.ProjectDetailPO;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.service.ReportPermissionService;
import com.yzzhang.weeklyreport.vo.ProjectDetailVO;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectDetailServiceImplTest {
    private static final String WEEK = "2026-W29";

    @Test
    void filtersByVisibleSubmissionRowsAndRenumbersPublicResult() {
        Fixture fixture = fixture();

        List<ProjectDetailVO> rows = fixture.service.list(WEEK);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getSequence()).isEqualTo(1);
        assertThat(rows.getFirst().getProjectName()).isEqualTo("虚构项目乙");
    }

    @Test
    void exportsReferenceSevenColumnWorkbookWithSafeCellTypes() throws Exception {
        Fixture fixture = fixture();

        Path xlsx = fixture.service.exportXlsx(WEEK);

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(xlsx))) {
            var sheet = workbook.getSheet("项目明细表");
            DataFormatter formatter = new DataFormatter();
            assertThat(formatter.formatCellValue(sheet.getRow(0).getCell(0))).isEqualTo("项目明细表");
            assertThat(sheet.getRow(1).getPhysicalNumberOfCells()).isEqualTo(7);
            assertThat(formatter.formatCellValue(sheet.getRow(1).getCell(3))).isEqualTo("项目名称");
            assertThat(sheet.getRow(2).getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(2).getCell(5).getCellType()).isEqualTo(CellType.STRING);
            assertThat(formatter.formatCellValue(sheet.getRow(2).getCell(5))).isEqualTo("=1+1");
        }
    }

    @Test
    void rejectsInvalidWeekBeforeReadingFiles() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.service.list("../2026-W29"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private Fixture fixture() {
        ProjectDetailMapper detailMapper = mock(ProjectDetailMapper.class);
        SubmissionStatusMapper statusMapper = mock(SubmissionStatusMapper.class);
        ReportPermissionService permissionService = mock(ReportPermissionService.class);
        ReportPermissionService.ReportPermission permission =
            new ReportPermissionService.ReportPermission(false, List.of("虚构市场部"));

        SubmissionStatusPO firstStatus = status("test-user-001", "虚构研发部");
        SubmissionStatusPO secondStatus = status("test-user-002", "虚构市场部");
        when(statusMapper.selectByWeek(WEEK)).thenReturn(List.of(firstStatus, secondStatus));
        when(permissionService.currentPermission()).thenReturn(permission);
        when(permissionService.filterRows(anyList(), eq(permission))).thenReturn(List.of(secondStatus));
        when(detailMapper.selectByWeek(WEEK)).thenReturn(List.of(
            detail("test-user-001", "虚构项目甲", "2", "100"),
            detail("test-user-002", "虚构项目乙", "3.5", "=1+1")
        ));

        return new Fixture(new ProjectDetailServiceImpl(detailMapper, statusMapper, permissionService));
    }

    private SubmissionStatusPO status(String userid, String department) {
        SubmissionStatusPO po = new SubmissionStatusPO();
        po.setUserid(userid);
        po.setDept(department);
        return po;
    }

    private ProjectDetailPO detail(String userid, String project, String days, String travel) {
        ProjectDetailPO po = new ProjectDetailPO();
        po.setUserid(userid);
        po.setProductLine("虚构产品线");
        po.setCustomerName("虚构客户");
        po.setProjectName(project);
        po.setInvestedDays(days);
        po.setTravelExpense(travel);
        po.setHospitalityExpense("0");
        return po;
    }

    private record Fixture(ProjectDetailServiceImpl service) {
    }
}
