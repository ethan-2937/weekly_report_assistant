package com.yzzhang.weeklyreport.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionCredentialValidatorTest {
    private static final String SAFE_JWT_SECRET = "fictional-jwt-secret-with-more-than-32-bytes";
    private static final String SAFE_ADMIN_PASSWORD = "fictional-admin-password-2026";

    @Test
    void productionModeRejectsTheDevelopmentJwtSecretWithoutEchoingIt() {
        WeeklyReportProperties properties = productionProperties();
        properties.getAuth().setJwtSecret(WeeklyReportProperties.Auth.DEVELOPMENT_JWT_SECRET);

        assertThatThrownBy(() -> new ProductionCredentialValidator(properties).validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("WEEKLY_JWT_SECRET")
            .hasMessageNotContaining(WeeklyReportProperties.Auth.DEVELOPMENT_JWT_SECRET)
            .hasMessageNotContaining(SAFE_ADMIN_PASSWORD);
    }

    @Test
    void productionModeRejectsTheDevelopmentAdminPasswordWithoutEchoingIt() {
        WeeklyReportProperties properties = productionProperties();
        properties.getAuth().setBootstrapAdminPassword(WeeklyReportProperties.Auth.DEVELOPMENT_ADMIN_PASSWORD);

        assertThatThrownBy(() -> new ProductionCredentialValidator(properties).validate())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("WEEKLY_BOOTSTRAP_ADMIN_PASSWORD")
            .hasMessageNotContaining(WeeklyReportProperties.Auth.DEVELOPMENT_ADMIN_PASSWORD)
            .hasMessageNotContaining(SAFE_JWT_SECRET);
    }

    @Test
    void productionModeAcceptsExplicitNonDefaultCredentials() {
        assertThatCode(() -> new ProductionCredentialValidator(productionProperties()).validate())
            .doesNotThrowAnyException();
    }

    @Test
    void explicitDevelopmentModeAllowsLocalDefaults() {
        WeeklyReportProperties properties = new WeeklyReportProperties();
        properties.getAuth().setDevelopmentMode(true);

        assertThatCode(() -> new ProductionCredentialValidator(properties).validate())
            .doesNotThrowAnyException();
    }

    private WeeklyReportProperties productionProperties() {
        WeeklyReportProperties properties = new WeeklyReportProperties();
        properties.getAuth().setDevelopmentMode(false);
        properties.getAuth().setJwtSecret(SAFE_JWT_SECRET);
        properties.getAuth().setBootstrapAdminPassword(SAFE_ADMIN_PASSWORD);
        return properties;
    }
}
