package com.yzzhang.weeklyreport.config;

import com.yzzhang.weeklyreport.service.impl.EvaluationFeedbackService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "weekly.evaluation-feedback", name = "enabled", havingValue = "true")
public class EvaluationFeedbackScheduler {
    private final EvaluationFeedbackService feedbackService;

    public EvaluationFeedbackScheduler(
        EvaluationFeedbackService feedbackService,
        EvaluationFeedbackProperties properties
    ) {
        assertContact(properties.getHrContactName());
        this.feedbackService = feedbackService;
    }

    @Scheduled(
        cron = "${weekly.evaluation-feedback.cron:0 0 12 * * MON}",
        zone = "${weekly.evaluation-feedback.zone:Asia/Shanghai}"
    )
    public void sendEvaluationFeedback() {
        feedbackService.runOnce();
    }

    private void assertContact(String contactName) {
        if (contactName == null || !contactName.trim().matches("[\\p{L}\\p{N}·._ -]{1,64}")) {
            throw new IllegalStateException(
                "WEEKLY_EVALUATION_FEEDBACK_HR_CONTACT must be configured when evaluation feedback is enabled"
            );
        }
    }
}
