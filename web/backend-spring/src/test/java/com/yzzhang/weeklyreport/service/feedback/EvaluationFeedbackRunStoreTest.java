package com.yzzhang.weeklyreport.service.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationFeedbackRunStoreTest {
    private static final String WEEK = "2026-W29";

    @TempDir
    Path tempDir;

    @Test
    void readsLegacyStateAndWritesOnlyANonSensitiveDigest() throws Exception {
        WeeklyReportProperties properties = new WeeklyReportProperties();
        properties.setProjectRoot(tempDir.toString());
        EvaluationFeedbackRunStore store = new EvaluationFeedbackRunStore(
            new ProjectPathConfig(properties),
            new ObjectMapper()
        );
        Path statePath = tempDir.resolve("output/2026-W29/notifications/monday-evaluation-feedback.json");
        Files.createDirectories(statePath.getParent());
        Files.writeString(statePath, """
            {
              "weekLabel": "2026-W29",
              "phase": "COMPLETE",
              "eligibleCount": 1,
              "sentCount": 1,
              "updatedAt": "2026-07-20T04:00:00Z"
            }
            """, StandardCharsets.UTF_8);

        assertThat(store.load(WEEK)).get().satisfies(state ->
            assertThat(state.feedbackDigest()).isNull()
        );

        store.save(store.state(WEEK, "COMPLETE", 1, 1, "fictional-feedback-digest"));
        String stored = Files.readString(statePath, StandardCharsets.UTF_8);
        assertThat(stored)
            .contains("fictional-feedback-digest")
            .doesNotContain("示例员工", "虚构评价正文", "test-user-001");
    }
}
