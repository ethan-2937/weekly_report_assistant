package com.yzzhang.weeklyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weekly.submission-reminder")
public class SubmissionReminderProperties {
    private boolean enabled;
    private long processTimeoutSeconds = 180;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getProcessTimeoutSeconds() {
        return processTimeoutSeconds;
    }

    public void setProcessTimeoutSeconds(long processTimeoutSeconds) {
        this.processTimeoutSeconds = processTimeoutSeconds;
    }
}
