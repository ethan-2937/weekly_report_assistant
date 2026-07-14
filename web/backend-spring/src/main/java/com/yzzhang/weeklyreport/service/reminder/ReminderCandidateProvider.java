package com.yzzhang.weeklyreport.service.reminder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.SubmissionReminderProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ReminderCandidateProvider {
    private static final int MAX_PROCESS_OUTPUT_BYTES = 64 * 1024;

    private final ProjectPathConfig pathConfig;
    private final SubmissionReminderProperties properties;
    private final ObjectMapper objectMapper;

    public ReminderCandidateProvider(
        ProjectPathConfig pathConfig,
        SubmissionReminderProperties properties,
        ObjectMapper objectMapper
    ) {
        this.pathConfig = pathConfig;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ReminderCandidateSnapshot collect() {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command());
            builder.directory(pathConfig.projectRoot().toFile());
            process = builder.start();
            long timeout = Math.max(1, properties.getProcessTimeoutSeconds());
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new SubmissionReminderException("未交候选采集超时");
            }
            String stdout = readLimited(process.getInputStream());
            if (process.exitValue() != 0) {
                throw new SubmissionReminderException("未交候选采集失败（退出码 " + process.exitValue() + "）");
            }
            return parse(stdout);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SubmissionReminderException("未交候选采集被中断", ex);
        } catch (IOException ex) {
            throw new SubmissionReminderException("无法启动未交候选采集进程", ex);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    ReminderCandidateSnapshot parse(String stdout) {
        try {
            ReminderCandidateSnapshot snapshot = objectMapper.readValue(stdout.strip(), ReminderCandidateSnapshot.class);
            validate(snapshot);
            return snapshot;
        } catch (JsonProcessingException ex) {
            throw new SubmissionReminderException("未交候选采集返回格式无效");
        }
    }

    private void validate(ReminderCandidateSnapshot snapshot) {
        if (snapshot.weekLabel() == null || !snapshot.weekLabel().matches("\\d{4}-W\\d{2}")) {
            throw new SubmissionReminderException("未交候选采集缺少有效周次");
        }
        if (snapshot.expectedCount() <= 0 || snapshot.submittedCount() < 0 || snapshot.unresolvedCount() < 0) {
            throw new SubmissionReminderException("未交候选采集返回了无效统计");
        }
        if (snapshot.unresolvedCount() > 0) {
            throw new SubmissionReminderException("应交人员存在缺少稳定 userid 的记录");
        }
        if (snapshot.submittedCount() + snapshot.missingUserIds().size() != snapshot.expectedCount()
            || snapshot.missingUserIds().stream().distinct().count() != snapshot.missingUserIds().size()) {
            throw new SubmissionReminderException("未交候选采集统计不一致");
        }
        if (snapshot.missingUserIds().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new SubmissionReminderException("未交候选采集包含空 userid");
        }
    }

    private List<String> command() {
        List<String> command = new ArrayList<>();
        String configured = pathConfig.pythonBin();
        if (pathConfig.isWindows() && "py".equals(configured)) {
            command.add("py");
            command.add("-3");
        } else {
            command.add(configured);
        }
        command.add("scripts/submission_reminder.py");
        command.add("--json");
        return command;
    }

    private String readLimited(java.io.InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_PROCESS_OUTPUT_BYTES + 1);
        if (bytes.length > MAX_PROCESS_OUTPUT_BYTES) {
            throw new SubmissionReminderException("未交候选采集输出超过安全限制");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
