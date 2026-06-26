package com.yzzhang.weeklyreport.service;

import com.yzzhang.weeklyreport.vo.JobRecordVO;

import java.util.List;

public interface JobService {
    JobRecordVO runWeeklyJob(String weekMode);
    JobRecordVO latestJob();
    List<JobRecordVO> listJobs();
}
