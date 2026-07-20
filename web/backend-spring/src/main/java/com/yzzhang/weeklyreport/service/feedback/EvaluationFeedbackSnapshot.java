package com.yzzhang.weeklyreport.service.feedback;

import java.util.List;

public record EvaluationFeedbackSnapshot(
    String weekLabel,
    List<EmployeeFeedback> employees
) {
    public EvaluationFeedbackSnapshot {
        employees = employees == null ? List.of() : List.copyOf(employees);
    }

    public record EmployeeFeedback(
        String userId,
        String name,
        String praise,
        String improvement,
        String thanks
    ) {}
}
