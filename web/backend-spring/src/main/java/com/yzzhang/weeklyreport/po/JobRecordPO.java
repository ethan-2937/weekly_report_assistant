package com.yzzhang.weeklyreport.po;

import java.time.Instant;

public class JobRecordPO {
    private String id;
    private String weekMode;
    private String weekLabel = "";
    private String status = "RUNNING";
    private Instant startedAt = Instant.now();
    private Instant finishedAt;
    private String stdout = "";
    private String errorMessage = "";

    public JobRecordPO(String id, String weekMode) {
        this.id = id;
        this.weekMode = weekMode;
    }

    public String getId() { return id; }
    public String getWeekMode() { return weekMode; }
    public String getWeekLabel() { return weekLabel; }
    public void setWeekLabel(String weekLabel) { this.weekLabel = weekLabel; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getStdout() { return stdout; }
    public void setStdout(String stdout) { this.stdout = stdout; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
