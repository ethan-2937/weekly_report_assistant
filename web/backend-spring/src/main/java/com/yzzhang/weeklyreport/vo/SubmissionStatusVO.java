package com.yzzhang.weeklyreport.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubmissionStatusVO {
    @JsonProperty("????")
    private String status;
    @JsonProperty("??")
    private String name;
    private String userid;
    @JsonProperty("??")
    private String dept;
    @JsonProperty("???????")
    private String leaderCandidate;
    @JsonProperty("??")
    private String title;
    @JsonProperty("????")
    private String reportDept;
    @JsonProperty("????")
    private String submitTime;
    @JsonProperty("report_id")
    private String reportId;
    @JsonProperty("??")
    private String templateName;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUserid() { return userid; }
    public void setUserid(String userid) { this.userid = userid; }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }
    public String getLeaderCandidate() { return leaderCandidate; }
    public void setLeaderCandidate(String leaderCandidate) { this.leaderCandidate = leaderCandidate; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getReportDept() { return reportDept; }
    public void setReportDept(String reportDept) { this.reportDept = reportDept; }
    public String getSubmitTime() { return submitTime; }
    public void setSubmitTime(String submitTime) { this.submitTime = submitTime; }
    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
}
