package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.vo.HealthVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final ProjectPathConfig pathConfig;

    public HealthController(ProjectPathConfig pathConfig) {
        this.pathConfig = pathConfig;
    }

    @GetMapping("/health")
    public HealthVO health() {
        return new HealthVO("ok", pathConfig.projectRoot().toString());
    }
}
