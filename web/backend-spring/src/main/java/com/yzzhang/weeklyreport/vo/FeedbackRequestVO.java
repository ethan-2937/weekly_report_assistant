package com.yzzhang.weeklyreport.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FeedbackRequestVO {
    @NotBlank(message = "请选择反馈类型")
    @Size(max = 32, message = "反馈类型过长")
    private String category;

    @Size(max = 120, message = "标题不能超过120个字")
    private String title;

    @NotBlank(message = "请填写详细描述")
    @Size(max = 3000, message = "详细描述不能超过3000个字")
    private String detail;

    @Size(max = 2000, message = "期望或复现步骤不能超过2000个字")
    private String expectation;

    @Size(max = 32, message = "紧急程度过长")
    private String urgency;

    @Size(max = 512, message = "页面地址过长")
    private String pageUrl;

    @Size(max = 512, message = "浏览器信息过长")
    private String userAgent;

    @Size(max = 32, message = "周次过长")
    private String week;

    @Size(max = 64, message = "页面视图过长")
    private String view;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getExpectation() {
        return expectation;
    }

    public void setExpectation(String expectation) {
        this.expectation = expectation;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getWeek() {
        return week;
    }

    public void setWeek(String week) {
        this.week = week;
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }
}
