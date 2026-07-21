package com.yzzhang.weeklyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weekly.feedback-personal-message")
public class FeedbackPersonalMessageProperties {
    private String mode = "WORK_NOTICE";
    private String robotCode;
    private String robotSendUrl = "https://api.dingtalk.com/v1.0/robot/oToMessages/batchSend";

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getRobotCode() {
        return robotCode;
    }

    public void setRobotCode(String robotCode) {
        this.robotCode = robotCode;
    }

    public String getRobotSendUrl() {
        return robotSendUrl;
    }

    public void setRobotSendUrl(String robotSendUrl) {
        this.robotSendUrl = robotSendUrl;
    }
}
