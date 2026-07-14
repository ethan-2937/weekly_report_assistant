package com.yzzhang.weeklyreport.config;

import com.yzzhang.weeklyreport.service.impl.SubmissionReminderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "weekly.submission-reminder", name = "enabled", havingValue = "true")
public class SubmissionReminderScheduler {
    private final SubmissionReminderService reminderService;

    public SubmissionReminderScheduler(SubmissionReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(
        cron = "${weekly.submission-reminder.cron:0 0 18 * * SUN}",
        zone = "${weekly.submission-reminder.zone:Asia/Shanghai}"
    )
    public void remindMissingSubmitters() {
        reminderService.runOnce();
    }
}
