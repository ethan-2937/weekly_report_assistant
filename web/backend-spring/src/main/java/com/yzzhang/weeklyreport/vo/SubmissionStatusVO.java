package com.yzzhang.weeklyreport.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubmissionStatusVO {
    @JsonProperty("\u63d0\u4ea4\u72b6\u6001")
    private String status;
    @JsonProperty("\u59d3\u540d")
    private String name;
    private String userid;
    @JsonProperty("\u90e8\u95e8")
    private String dept;
    @JsonProperty("\u662f\u5426\u8d1f\u8d23\u4eba\u5019\u9009")
    private String leaderCandidate;
    @JsonProperty("\u804c\u52a1")
    private String title;
    @JsonProperty("\u5468\u62a5\u90e8\u95e8")
    private String reportDept;
    @JsonProperty("\u63d0\u4ea4\u65f6\u95f4")
    private String submitTime;
    @JsonProperty("report_id")
    private String reportId;
    @JsonProperty("\u6a21\u677f")
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
