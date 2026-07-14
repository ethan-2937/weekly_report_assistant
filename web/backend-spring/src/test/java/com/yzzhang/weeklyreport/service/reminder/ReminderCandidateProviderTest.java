package com.yzzhang.weeklyreport.service.reminder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yzzhang.weeklyreport.config.ProjectPathConfig;
import com.yzzhang.weeklyreport.config.SubmissionReminderProperties;
import com.yzzhang.weeklyreport.config.WeeklyReportProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReminderCandidateProviderTest {
    private final ReminderCandidateProvider provider = provider();

    @Test
    void parsesValidatedCandidateSnapshot() {
        ReminderCandidateSnapshot snapshot = provider.parse("""
            {
              "weekLabel": "2026-W29",
              "cutoff": "2026-07-19T18:00:00+08:00",
              "expectedCount": 2,
              "submittedCount": 1,
              "missingUserIds": ["test-user-002"],
              "unresolvedCount": 0
            }
            """);

        assertThat(snapshot.missingUserIds()).containsExactly("test-user-002");
    }

    @Test
    void rejectsUnresolvedRosterWithoutEchoingUserids() {
        String sensitiveUserId = "test-user-sensitive";
        String payload = """
            {
              "weekLabel": "2026-W29",
              "cutoff": "2026-07-19T18:00:00+08:00",
              "expectedCount": 2,
              "submittedCount": 0,
              "missingUserIds": ["%s"],
              "unresolvedCount": 1
            }
            """.formatted(sensitiveUserId);

        assertThatThrownBy(() -> provider.parse(payload))
            .isInstanceOf(SubmissionReminderException.class)
            .hasMessageContaining("稳定 userid")
            .hasMessageNotContaining(sensitiveUserId);
    }

    @Test
    void malformedOutputDoesNotAppearInTheError() {
        String sensitiveOutput = "not-json-fictional-sensitive-token";

        assertThatThrownBy(() -> provider.parse(sensitiveOutput))
            .isInstanceOf(SubmissionReminderException.class)
            .hasMessage("未交候选采集返回格式无效")
            .hasMessageNotContaining(sensitiveOutput);
    }

    @Test
    void rejectsAnEmptyRosterInsteadOfReportingEveryoneSubmitted() {
        String payload = """
            {
              "weekLabel": "2026-W29",
              "cutoff": "2026-07-19T18:00:00+08:00",
              "expectedCount": 0,
              "submittedCount": 0,
              "missingUserIds": [],
              "unresolvedCount": 0
            }
            """;

        assertThatThrownBy(() -> provider.parse(payload))
            .isInstanceOf(SubmissionReminderException.class)
            .hasMessage("未交候选采集返回了无效统计");
    }

    private ReminderCandidateProvider provider() {
        WeeklyReportProperties weekly = new WeeklyReportProperties();
        return new ReminderCandidateProvider(
            new ProjectPathConfig(weekly),
            new SubmissionReminderProperties(),
            new ObjectMapper()
        );
    }
}
