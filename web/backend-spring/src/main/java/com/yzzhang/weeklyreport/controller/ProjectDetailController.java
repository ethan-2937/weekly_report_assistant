package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.service.ProjectDetailService;
import com.yzzhang.weeklyreport.vo.ProjectDetailVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/weeks")
public class ProjectDetailController {
    private final ProjectDetailService projectDetailService;

    public ProjectDetailController(ProjectDetailService projectDetailService) {
        this.projectDetailService = projectDetailService;
    }

    @GetMapping("/{week}/project-details")
    public List<ProjectDetailVO> list(@PathVariable String week) {
        return projectDetailService.list(week);
    }
}
