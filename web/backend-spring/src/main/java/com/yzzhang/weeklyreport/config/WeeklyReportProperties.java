package com.yzzhang.weeklyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weekly")
public class WeeklyReportProperties {
    private String projectRoot;
    private String frontendDist;
    private String pythonBin;
    private Auth auth = new Auth();

    public String getProjectRoot() {
        return projectRoot;
    }

    public void setProjectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
    }

    public String getFrontendDist() {
        return frontendDist;
    }

    public void setFrontendDist(String frontendDist) {
        this.frontendDist = frontendDist;
    }

    public String getPythonBin() {
        return pythonBin;
    }

    public void setPythonBin(String pythonBin) {
        this.pythonBin = pythonBin;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    public static class Auth {
        private String jwtSecret = "weekly-report-local-dev-secret-change-me-2026";
        private long tokenExpireMinutes = 720;
        private String bootstrapAdminUsername = "admin";
        private String bootstrapAdminPassword = "admin123";
        private String bootstrapAdminRealName = "系统管理员";
        private DingTalk dingtalk = new DingTalk();

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public long getTokenExpireMinutes() {
            return tokenExpireMinutes;
        }

        public void setTokenExpireMinutes(long tokenExpireMinutes) {
            this.tokenExpireMinutes = tokenExpireMinutes;
        }

        public String getBootstrapAdminUsername() {
            return bootstrapAdminUsername;
        }

        public void setBootstrapAdminUsername(String bootstrapAdminUsername) {
            this.bootstrapAdminUsername = bootstrapAdminUsername;
        }

        public String getBootstrapAdminPassword() {
            return bootstrapAdminPassword;
        }

        public void setBootstrapAdminPassword(String bootstrapAdminPassword) {
            this.bootstrapAdminPassword = bootstrapAdminPassword;
        }

        public String getBootstrapAdminRealName() {
            return bootstrapAdminRealName;
        }

        public void setBootstrapAdminRealName(String bootstrapAdminRealName) {
            this.bootstrapAdminRealName = bootstrapAdminRealName;
        }

        public DingTalk getDingtalk() {
            return dingtalk;
        }

        public void setDingtalk(DingTalk dingtalk) {
            this.dingtalk = dingtalk;
        }
    }

    public static class DingTalk {
        private boolean enabled;
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private String frontendUrl = "/";
        private String authorizeUrl = "https://login.dingtalk.com/oauth2/auth";
        private String tokenUrl = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
        private String userInfoUrl = "https://api.dingtalk.com/v1.0/contact/users/me";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public String getFrontendUrl() {
            return frontendUrl;
        }

        public void setFrontendUrl(String frontendUrl) {
            this.frontendUrl = frontendUrl;
        }

        public String getAuthorizeUrl() {
            return authorizeUrl;
        }

        public void setAuthorizeUrl(String authorizeUrl) {
            this.authorizeUrl = authorizeUrl;
        }

        public String getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }

        public String getUserInfoUrl() {
            return userInfoUrl;
        }

        public void setUserInfoUrl(String userInfoUrl) {
            this.userInfoUrl = userInfoUrl;
        }
    }
}
