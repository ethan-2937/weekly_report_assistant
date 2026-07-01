package com.yzzhang.weeklyreport.vo;

public class LoginResponseVO {
    private String token;
    private String tokenType = "Bearer";
    private long expiresIn;
    private CurrentUserVO user;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public CurrentUserVO getUser() {
        return user;
    }

    public void setUser(CurrentUserVO user) {
        this.user = user;
    }
}
