package com.yzzhang.weeklyreport.vo;

public class JobRecordVO {
    private String id;
    private String weekMode;
    private String weekLabel;
    private String status;
    private String startedAt;
    private String finishedAt;
    private String stdout;
    private String errorMessage;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getWeekMode() { return weekMode; }
    public void setWeekMode(String weekMode) { this.weekMode = weekMode; }
    public String getWeekLabel() { return weekLabel; }
    public void setWeekLabel(String weekLabel) { this.weekLabel = weekLabel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }
    public String getStdout() { return stdout; }
    public void setStdout(String stdout) { this.stdout = stdout; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
