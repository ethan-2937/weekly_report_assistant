package com.yzzhang.weeklyreport.vo;

import java.util.ArrayList;
import java.util.List;

public class CurrentUserVO {
    private Long id;
    private String username;
    private String realName;
    private String dingUserId;
    private List<String> roles = new ArrayList<>();
    private List<String> deptScopes = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getDingUserId() {
        return dingUserId;
    }

    public void setDingUserId(String dingUserId) {
        this.dingUserId = dingUserId;
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
