package com.yzzhang.weeklyreport.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

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
    @JsonProperty("\u6a21\u677f\u586b\u5199\u6b63\u786e\u7387")
    private Integer templateComplianceRate;
    @JsonProperty("\u6a21\u677f\u5408\u89c4\u72b6\u6001")
    private String templateComplianceStatus;
    @JsonProperty("\u6a21\u677f\u7f3a\u5931\u9879")
    private List<String> templateComplianceMissingFields = List.of();
    @JsonProperty("\u6a21\u677f\u547d\u4e2d\u9879")
    private List<String> templateCompliancePresentFields = List.of();
    @JsonProperty("\u6a21\u677f\u68c0\u67e5\u8bf4\u660e")
    private String templateComplianceDetail;

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
    public Integer getTemplateComplianceRate() { return templateComplianceRate; }
    public void setTemplateComplianceRate(Integer templateComplianceRate) { this.templateComplianceRate = templateComplianceRate; }
    public String getTemplateComplianceStatus() { return templateComplianceStatus; }
    public void setTemplateComplianceStatus(String templateComplianceStatus) { this.templateComplianceStatus = templateComplianceStatus; }
    public List<String> getTemplateComplianceMissingFields() { return templateComplianceMissingFields; }
    public void setTemplateComplianceMissingFields(List<String> templateComplianceMissingFields) { this.templateComplianceMissingFields = templateComplianceMissingFields == null ? List.of() : templateComplianceMissingFields; }
    public List<String> getTemplateCompliancePresentFields() { return templateCompliancePresentFields; }
    public void setTemplateCompliancePresentFields(List<String> templateCompliancePresentFields) { this.templateCompliancePresentFields = templateCompliancePresentFields == null ? List.of() : templateCompliancePresentFields; }
    public String getTemplateComplianceDetail() { return templateComplianceDetail; }
    public void setTemplateComplianceDetail(String templateComplianceDetail) { this.templateComplianceDetail = templateComplianceDetail; }
}
