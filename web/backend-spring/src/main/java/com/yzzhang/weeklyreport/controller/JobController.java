package com.yzzhang.weeklyreport.controller;

import com.yzzhang.weeklyreport.service.JobService;
import com.yzzhang.weeklyreport.vo.JobRecordVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("/run")
    public ResponseEntity<JobRecordVO> run(@RequestParam(defaultValue = "previous") String week) {
        return ResponseEntity.accepted().body(jobService.runWeeklyJob(week));
    }

    @GetMapping("/latest")
    public JobRecordVO latest() {
        return jobService.latestJob();
    }

    @GetMapping
    public List<JobRecordVO> list() {
        return jobService.listJobs();
    }
}
