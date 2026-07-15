package com.yzzhang.weeklyreport.vo;

import java.util.List;

public class WeeklyReportDetailVO {
    private String week;
    private String name;
    private String department;
    private String title;
    private String status;
    private String submittedAt;
    private boolean available;
    private String message;
    private List<WeeklyReportFieldVO> fields = List.of();

    public String getWeek() { return week; }
    public void setWeek(String week) { this.week = week; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(String submittedAt) { this.submittedAt = submittedAt; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<WeeklyReportFieldVO> getFields() { return fields; }
    public void setFields(List<WeeklyReportFieldVO> fields) {
        this.fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
