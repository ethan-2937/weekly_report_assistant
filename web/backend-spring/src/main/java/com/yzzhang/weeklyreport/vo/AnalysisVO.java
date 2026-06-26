package com.yzzhang.weeklyreport.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AnalysisVO {
    private String week;
    private String source;
    private String content;
    private boolean managerReport;

    public String getWeek() { return week; }
    public void setWeek(String week) { this.week = week; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    @JsonProperty("isManagerReport")
    public boolean isManagerReport() { return managerReport; }
    public void setManagerReport(boolean managerReport) { this.managerReport = managerReport; }
}
