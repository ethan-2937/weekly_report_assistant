package com.yzzhang.weeklyreport.vo;

import java.util.List;

public record EvaluationFeedbackPreviewVO(
    String week,
    String phase,
    int eligibleCount,
    int sentCount,
    String updatedAt,
    boolean exactMatch,
    String verificationMode,
    String warning,
    List<NotificationVO> notifications
) {
    public EvaluationFeedbackPreviewVO {
        warning = warning == null ? "" : warning;
        verificationMode = verificationMode == null ? "" : verificationMode;
        notifications = notifications == null ? List.of() : List.copyOf(notifications);
    }

    public record NotificationVO(
        String name,
        String department,
        String title,
        String markdown
    ) {}
}
