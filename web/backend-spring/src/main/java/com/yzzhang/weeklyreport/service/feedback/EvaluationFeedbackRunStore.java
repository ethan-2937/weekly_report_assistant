package com.yzzhang.weeklyreport.service.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;

@Component
public class EvaluationFeedbackRunStore {
    private static final String STATE_FILE = "monday-evaluation-feedback.json";

    private final ProjectPathConfig pathConfig;
    private final ObjectMapper objectMapper;

    public EvaluationFeedbackRunStore(ProjectPathConfig pathConfig, ObjectMapper objectMapper) {
        this.pathConfig = pathConfig;
        this.objectMapper = objectMapper;
    }

    public Optional<RunState> load(String weekLabel) {
        Path path = statePath(weekLabel);
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), RunState.class));
        } catch (IOException ex) {
            throw new EvaluationFeedbackException("评价通知状态读取失败", ex);
        }
    }

    public void save(RunState state) {
        Path target = statePath(state.weekLabel());
        Path temporary = target.resolveSibling(STATE_FILE + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            objectMapper.writeValue(temporary.toFile(), state);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new EvaluationFeedbackException("评价通知状态写入失败", ex);
        }
    }

    public RunState state(String weekLabel, String phase, int eligibleCount, int sentCount) {
        return new RunState(weekLabel, phase, eligibleCount, sentCount, Instant.now().toString());
    }

    private Path statePath(String weekLabel) {
        if (weekLabel == null || !weekLabel.matches("\\d{4}-W\\d{2}")) {
            throw new EvaluationFeedbackException("评价通知周次无效");
        }
        return pathConfig.outputRoot()
            .resolve(weekLabel)
            .resolve("notifications")
            .resolve(STATE_FILE)
            .normalize();
    }

    public record RunState(
        String weekLabel,
        String phase,
        int eligibleCount,
        int sentCount,
        String updatedAt
    ) {}
}
