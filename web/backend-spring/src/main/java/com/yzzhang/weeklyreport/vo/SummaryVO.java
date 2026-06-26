package com.yzzhang.weeklyreport.vo;

public class SummaryVO extends WeekOverviewVO {
    private String submissionSummary;
    private String managerReport;

    public String getSubmissionSummary() { return submissionSummary; }
    public void setSubmissionSummary(String submissionSummary) { this.submissionSummary = submissionSummary; }
    public String getManagerReport() { return managerReport; }
    public void setManagerReport(String managerReport) { this.managerReport = managerReport; }
}
