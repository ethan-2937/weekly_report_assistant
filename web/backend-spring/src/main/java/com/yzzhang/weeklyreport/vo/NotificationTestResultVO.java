package com.yzzhang.weeklyreport.vo;

public record NotificationTestResultVO(
    String type,
    boolean delivered,
    String targetName,
    String message
) {}
