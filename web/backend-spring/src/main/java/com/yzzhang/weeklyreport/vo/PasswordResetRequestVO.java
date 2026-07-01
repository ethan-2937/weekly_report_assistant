package com.yzzhang.weeklyreport.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordResetRequestVO {
    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 128, message = "密码长度需在6到128个字符之间")
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
