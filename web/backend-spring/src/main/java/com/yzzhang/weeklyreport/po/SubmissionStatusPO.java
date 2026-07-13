package com.yzzhang.weeklyreport.po;

import java.util.List;

public class SubmissionStatusPO {
    private String status;
    private String name;
    private String userid;
    private String dept;
    private String leaderCandidate;
    private String title;
    private String reportDept;
    private String submitTime;
    private String reportId;
    private String templateName;
    private Integer templateComplianceRate;
    private String templateComplianceStatus;
    private List<String> templateComplianceMissingFields = List.of();
    private List<String> templateCompliancePresentFields = List.of();
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
