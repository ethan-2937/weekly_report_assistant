package com.yzzhang.weeklyreport.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class NotificationTestRequestVO {
    @NotBlank(message = "请选择通知测试类型")
    @Pattern(
        regexp = "SUNDAY_REMINDER|MONDAY_EVALUATION",
        message = "通知测试类型无效"
    )
    private String type;

    @NotBlank(message = "请确认通知测试接收人")
    @Size(max = 64, message = "通知测试接收人名称过长")
    private String confirmRecipientName;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getConfirmRecipientName() {
        return confirmRecipientName;
    }

    public void setConfirmRecipientName(String confirmRecipientName) {
        this.confirmRecipientName = confirmRecipientName;
    }
}
