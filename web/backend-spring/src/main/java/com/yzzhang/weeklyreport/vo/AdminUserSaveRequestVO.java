package com.yzzhang.weeklyreport.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class AdminUserSaveRequestVO {
    @NotBlank(message = "请输入用户名")
    @Size(max = 64, message = "用户名不能超过64个字符")
    private String username;

    @Size(max = 128, message = "密码不能超过128个字符")
    private String password;

    @Size(max = 64, message = "姓名不能超过64个字符")
    private String realName;

    @Size(max = 32, message = "手机号不能超过32个字符")
    private String mobile;

    @Size(max = 128, message = "邮箱不能超过128个字符")
    private String email;

    @Size(max = 128, message = "钉钉 userId 不能超过128个字符")
    private String dingUserId;

    @Size(max = 128, message = "钉钉 unionId 不能超过128个字符")
    private String dingUnionId;

    private Integer status = 1;
    private List<String> roles = new ArrayList<>();
    private List<String> deptScopes = new ArrayList<>();

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDingUserId() {
        return dingUserId;
    }

    public void setDingUserId(String dingUserId) {
        this.dingUserId = dingUserId;
    }

    public String getDingUnionId() {
        return dingUnionId;
    }

    public void setDingUnionId(String dingUnionId) {
        this.dingUnionId = dingUnionId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles == null ? new ArrayList<>() : roles;
    }

    public List<String> getDeptScopes() {
        return deptScopes;
    }

    public void setDeptScopes(List<String> deptScopes) {
        this.deptScopes = deptScopes == null ? new ArrayList<>() : deptScopes;
    }
}
