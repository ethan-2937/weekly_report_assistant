package com.yzzhang.weeklyreport.service.impl;

import com.yzzhang.weeklyreport.common.BizException;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.mapper.JobRecordMapper;
import com.yzzhang.weeklyreport.po.JobRecordPO;
import com.yzzhang.weeklyreport.service.JobService;
import com.yzzhang.weeklyreport.util.WeekLabelUtils;
import com.yzzhang.weeklyreport.vo.JobRecordVO;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class JobServiceImpl implements JobService {
    private final ProjectPathConfig pathConfig;
    private final JobRecordMapper jobRecordMapper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<JobRecordPO> jobs = new ArrayList<>();

    public JobServiceImpl(ProjectPathConfig pathConfig, JobRecordMapper jobRecordMapper) {
        this.pathConfig = pathConfig;
        this.jobRecordMapper = jobRecordMapper;
    }

    @Override
    public JobRecordVO runWeeklyJob(String weekMode) {
        if (!"previous".equals(weekMode) && !"current".equals(weekMode)) {
            throw new BizException("week must be previous or current");
        }
        if (!running.compareAndSet(false, true)) {
            throw new BizException("A weekly report job is already running.");
        }
        JobRecordPO job = new JobRecordPO(UUID.randomUUID().toString(), weekMode);
        synchronized (jobs) {
            jobs.add(job);
        }
        executor.submit(() -> runJob(job));
        return toVO(job);
    }

    @Override
    public JobRecordVO latestJob() {
        synchronized (jobs) {
            return jobs.stream().max(Comparator.comparing(JobRecordPO::getStartedAt)).map(this::toVO).orElse(new JobRecordVO());
        }
    }

    @Override
    public List<JobRecordVO> listJobs() {
        synchronized (jobs) {
            return jobs.stream()
                .sorted(Comparator.comparing(JobRecordPO::getStartedAt).reversed())
                .map(this::toVO)
                .toList();
        }
    }

    private void runJob(JobRecordPO job) {
        try {
            List<String> command = pythonCommand();
            command.add("scripts/run_weekly.py");
            command.add("--week");
            command.add(job.getWeekMode());
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(pathConfig.projectRoot().toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String stdout = readLimited(process.getInputStream(), 20000);
            int exit = process.waitFor();
            job.setStdout(stdout);
            job.setFinishedAt(Instant.now());
            if (exit == 0) {
                job.setStatus("SUCCESS");
                job.setWeekLabel(WeekLabelUtils.lastWeekLabelIn(stdout).orElse(""));
            } else {
                job.setStatus("FAILED");
                job.setErrorMessage("run_weekly.py exited with code " + exit);
            }
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage(e.getMessage() == null ? e.getClass().getName() : e.getMessage());
            job.setFinishedAt(Instant.now());
        } finally {
            running.set(false);
            jobRecordMapper.insertLog(job);
        }
    }

    private List<String> pythonCommand() {
        List<String> command = new ArrayList<>();
        String configured = pathConfig.pythonBin();
        if (pathConfig.isWindows() && "py".equals(configured)) {
            command.add("py");
            command.add("-3");
        } else {
            command.add(configured);
        }
        return command;
    }

    private String readLimited(InputStream input, int maxChars) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() < maxChars) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        }
    }

    private JobRecordVO toVO(JobRecordPO po) {
        JobRecordVO vo = new JobRecordVO();
        vo.setId(po.getId());
        vo.setWeekMode(po.getWeekMode());
        vo.setWeekLabel(po.getWeekLabel());
        vo.setStatus(po.getStatus());
        vo.setStartedAt(po.getStartedAt() == null ? "" : po.getStartedAt().toString());
        vo.setFinishedAt(po.getFinishedAt() == null ? "" : po.getFinishedAt().toString());
        vo.setStdout(Optional.ofNullable(po.getStdout()).orElse(""));
        vo.setErrorMessage(Optional.ofNullable(po.getErrorMessage()).orElse(""));
        return vo;
    }
}
