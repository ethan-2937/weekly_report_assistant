package com.yzzhang.weeklyreport.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ProductionCredentialValidator {
    private final WeeklyReportProperties properties;

    public ProductionCredentialValidator(WeeklyReportProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void validate() {
        WeeklyReportProperties.Auth auth = properties.getAuth();
        if (auth.isDevelopmentMode()) {
            return;
        }
        requireProductionValue(
            auth.getJwtSecret(),
            WeeklyReportProperties.Auth.DEVELOPMENT_JWT_SECRET,
            "WEEKLY_JWT_SECRET"
        );
        requireProductionValue(
            auth.getBootstrapAdminPassword(),
            WeeklyReportProperties.Auth.DEVELOPMENT_ADMIN_PASSWORD,
            "WEEKLY_BOOTSTRAP_ADMIN_PASSWORD"
        );
    }

    private void requireProductionValue(String value, String developmentDefault, String environmentVariable) {
        if (value == null || value.isBlank() || developmentDefault.equals(value)) {
            throw new IllegalStateException(
                "Production mode requires a non-default " + environmentVariable
                    + ". For local development only, set WEEKLY_AUTH_DEVELOPMENT_MODE=true."
            );
        }
    }
}
