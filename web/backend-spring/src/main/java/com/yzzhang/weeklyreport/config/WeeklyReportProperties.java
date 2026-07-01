package com.yzzhang.weeklyreport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weekly")
public class WeeklyReportProperties {
    private String projectRoot;
    private String frontendDist;
    private String pythonBin;
    private Auth auth = new Auth();
    private Feedback feedback = new Feedback();

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

    public Feedback getFeedback() {
        return feedback;
    }

    public void setFeedback(Feedback feedback) {
        this.feedback = feedback;
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

    public static class Feedback {
        private boolean enabled = true;
        private String recipientName = "张艺政";
        private String dingtalkUserIds;
        private String dingtalkAppKey;
        private String dingtalkAppSecret;
        private String dingtalkAgentId;
        private String envPath;
        private String accessTokenUrl = "https://api.dingtalk.com/v1.0/oauth2/accessToken";
        private String asyncSendUrl = "https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRecipientName() {
            return recipientName;
        }

        public void setRecipientName(String recipientName) {
            this.recipientName = recipientName;
        }

        public String getDingtalkUserIds() {
            return dingtalkUserIds;
        }

        public void setDingtalkUserIds(String dingtalkUserIds) {
            this.dingtalkUserIds = dingtalkUserIds;
        }

        public String getDingtalkAppKey() {
            return dingtalkAppKey;
        }

        public void setDingtalkAppKey(String dingtalkAppKey) {
            this.dingtalkAppKey = dingtalkAppKey;
        }

        public String getDingtalkAppSecret() {
            return dingtalkAppSecret;
        }

        public void setDingtalkAppSecret(String dingtalkAppSecret) {
            this.dingtalkAppSecret = dingtalkAppSecret;
        }

        public String getDingtalkAgentId() {
            return dingtalkAgentId;
        }

        public void setDingtalkAgentId(String dingtalkAgentId) {
            this.dingtalkAgentId = dingtalkAgentId;
        }

        public String getEnvPath() {
            return envPath;
        }

        public void setEnvPath(String envPath) {
            this.envPath = envPath;
        }

        public String getAccessTokenUrl() {
            return accessTokenUrl;
        }

        public void setAccessTokenUrl(String accessTokenUrl) {
            this.accessTokenUrl = accessTokenUrl;
        }

        public String getAsyncSendUrl() {
            return asyncSendUrl;
        }

        public void setAsyncSendUrl(String asyncSendUrl) {
            this.asyncSendUrl = asyncSendUrl;
        }
    }
}
