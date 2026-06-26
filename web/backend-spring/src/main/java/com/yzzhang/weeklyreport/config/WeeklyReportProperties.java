package com.yzzhang.weeklyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weekly")
public class WeeklyReportProperties {
    private String projectRoot;
    private String frontendDist;
    private String pythonBin;

    public String getProjectRoot() {
        return projectRoot;
    }

    public void setProjectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
    }

    public String getFrontendDist() {
        return frontendDist;
    }

    public void setFrontendDist(String frontendDist) {
        this.frontendDist = frontendDist;
    }

    public String getPythonBin() {
        return pythonBin;
    }

    public void setPythonBin(String pythonBin) {
        this.pythonBin = pythonBin;
    }
}
