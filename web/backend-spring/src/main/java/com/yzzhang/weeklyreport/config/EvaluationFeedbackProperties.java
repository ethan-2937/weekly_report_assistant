package com.yzzhang.weeklyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weekly.evaluation-feedback")
public class EvaluationFeedbackProperties {
    private boolean enabled;
    private String hrContactName = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHrContactName() {
        return hrContactName;
    }

    public void setHrContactName(String hrContactName) {
        this.hrContactName = hrContactName;
    }
}
