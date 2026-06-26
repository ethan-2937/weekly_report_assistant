package com.yzzhang.weeklyreport.vo;

public class HealthVO {
    private String status;
    private String projectRoot;

    public HealthVO(String status, String projectRoot) {
        this.status = status;
        this.projectRoot = projectRoot;
    }

    public String getStatus() { return status; }
    public String getProjectRoot() { return projectRoot; }
}
