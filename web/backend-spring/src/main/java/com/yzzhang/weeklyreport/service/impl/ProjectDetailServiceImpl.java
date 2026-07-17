package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.mapper.ProjectDetailMapper;
import com.yzzhang.weeklyreport.mapper.SubmissionStatusMapper;
import com.yzzhang.weeklyreport.po.ProjectDetailPO;
import com.yzzhang.weeklyreport.po.SubmissionStatusPO;
import com.yzzhang.weeklyreport.service.ProjectDetailService;
import com.yzzhang.weeklyreport.service.ReportPermissionService;
import com.yzzhang.weeklyreport.util.WeekLabelUtils;
import com.yzzhang.weeklyreport.vo.ProjectDetailVO;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectDetailServiceImpl implements ProjectDetailService {
    private final ProjectDetailMapper projectDetailMapper;
    private final SubmissionStatusMapper submissionStatusMapper;
    private final ReportPermissionService reportPermissionService;

    public ProjectDetailServiceImpl(
        ProjectDetailMapper projectDetailMapper,
        SubmissionStatusMapper submissionStatusMapper,
        ReportPermissionService reportPermissionService
    ) {
        this.projectDetailMapper = projectDetailMapper;
        this.submissionStatusMapper = submissionStatusMapper;
        this.reportPermissionService = reportPermissionService;
    }

    @Override
    public List<ProjectDetailVO> list(String week) {
        assertWeek(week);
        ReportPermissionService.ReportPermission permission = reportPermissionService.currentPermission();
        List<SubmissionStatusPO> visibleRows = reportPermissionService.filterRows(
            submissionStatusMapper.selectByWeek(week),
            permission
        );
        Set<String> visibleUserids = new HashSet<>();
        for (SubmissionStatusPO row : visibleRows) {
            if (row.getUserid() != null && !row.getUserid().isBlank()) {
                visibleUserids.add(row.getUserid());
            }
        }
        List<ProjectDetailPO> visibleDetails = projectDetailMapper.selectByWeek(week).stream()
            .filter(row -> visibleUserids.contains(row.getUserid()))
            .toList();
        return java.util.stream.IntStream.range(0, visibleDetails.size())
            .mapToObj(index -> toVO(visibleDetails.get(index), index + 1))
            .toList();
    }

    @Override
    public Path exportXlsx(String week) {
        List<ProjectDetailVO> rows = list(week);
        Path exported = null;
        try {
            exported = Files.createTempFile("project_details_" + week + "_", ".xlsx");
            ProjectDetailXlsxExporter.write(exported, rows);
            exported.toFile().deleteOnExit();
            return exported;
        } catch (IOException e) {
            if (exported != null) {
                try {
                    Files.deleteIfExists(exported);
                } catch (IOException ignored) {
                    // Keep the public error independent from the temporary path.
                }
            }
            throw new IllegalStateException("project detail export generation failed");
        }
    }

    private ProjectDetailVO toVO(ProjectDetailPO po, int sequence) {
        ProjectDetailVO vo = new ProjectDetailVO();
        vo.setSequence(sequence);
        vo.setProductLine(po.getProductLine());
        vo.setCustomerName(po.getCustomerName());
        vo.setProjectName(po.getProjectName());
        vo.setInvestedDays(po.getInvestedDays());
        vo.setTravelExpense(po.getTravelExpense());
        vo.setHospitalityExpense(po.getHospitalityExpense());
        return vo;
    }

    private void assertWeek(String week) {
        if (!WeekLabelUtils.isValid(week)) {
            throw new IllegalArgumentException("invalid week");
        }
    }
}
