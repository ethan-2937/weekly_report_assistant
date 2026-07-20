package com.yzzhang.weeklyreport.service.feedback;

import java.time.Instant;
import java.util.List;

public record EvaluationFeedbackSnapshot(
    String weekLabel,
    List<EmployeeFeedback> employees,
    String feedbackDigest,
    Instant feedbackUpdatedAt
) {
    public EvaluationFeedbackSnapshot(String weekLabel, List<EmployeeFeedback> employees) {
        this(weekLabel, employees, "", Instant.EPOCH);
    }

    public EvaluationFeedbackSnapshot {
        employees = employees == null ? List.of() : List.copyOf(employees);
        feedbackDigest = feedbackDigest == null ? "" : feedbackDigest;
        feedbackUpdatedAt = feedbackUpdatedAt == null ? Instant.EPOCH : feedbackUpdatedAt;
    }

    public record EmployeeFeedback(
        String userId,
        String name,
        String department,
        String title,
        String praise,
        String improvement,
        String thanks
    ) {
        public EmployeeFeedback(
            String userId,
            String name,
            String praise,
            String improvement,
            String thanks
        ) {
            this(userId, name, "", "", praise, improvement, thanks);
        }
    }
}
