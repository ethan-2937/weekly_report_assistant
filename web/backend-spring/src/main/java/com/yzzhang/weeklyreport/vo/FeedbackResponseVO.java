package com.yzzhang.weeklyreport.vo;

public class FeedbackResponseVO {
    private String ticketNo;
    private boolean delivered;
    private String message;
    private String targetName;
    private String copyText;

    public FeedbackResponseVO() {
    }

    public FeedbackResponseVO(String ticketNo, boolean delivered, String message, String targetName, String copyText) {
        this.ticketNo = ticketNo;
        this.delivered = delivered;
        this.message = message;
        this.targetName = targetName;
        this.copyText = copyText;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getCopyText() {
        return copyText;
    }

    public void setCopyText(String copyText) {
        this.copyText = copyText;
    }
}
