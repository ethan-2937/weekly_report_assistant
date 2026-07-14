package com.yzzhang.weeklyreport.service.reminder;

import java.util.List;

public record ReminderCandidateSnapshot(
    String weekLabel,
    String cutoff,
    int expectedCount,
    int submittedCount,
    List<String> missingUserIds,
    int unresolvedCount
) {
    public ReminderCandidateSnapshot {
        missingUserIds = missingUserIds == null ? List.of() : List.copyOf(missingUserIds);
    }
}
