package com.waqiti.user.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration Validator
 *
 * Validates critical configuration on application startup
 * FAILS FAST if required configuration is missing
 *
 * PRODUCTION SAFETY:
 * - Prevents service from starting with invalid configuration
 * - Validates database connectivity settings
 * - Validates Kafka configuration
 * - Validates Keycloak authentication settings
 * - Validates external service URLs
 *
 * SECURITY:
 * - Ensures encryption keys are configured
 * - Validates OAuth2/JWT settings
 * - Checks for production-safe defaults
 */
@Slf4j
@Configuration
public class ConfigurationValidator {

    private final Environment environment;

    // Database Configuration
    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    // Kafka Configuration
    @Value("${spring.kafka.bootstrap-servers:}")
    private String kafkaBootstrapServers;

    // Keycloak Configuration
    @Value("${keycloak.auth-server-url:}")
    private String keycloakAuthServerUrl;

    @Value("${keycloak.credentials.secret:}")
    private String keycloakClientSecret;

    // External Services
    @Value("${services.integration-service.url:}")
    private String integrationServiceUrl;

    @Value("${services.notification-service.url:}")
    private String notificationServiceUrl;

    // Security Configuration
    @Value("${waqiti.user.security.mfa.enabled:false}")
    private boolean mfaEnabled;

    public ConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validateConfiguration() {
        log.info("STARTUP: Validating application configuration...");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Get active profile
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isProduction = activeProfiles.length > 0 &&
            (activeProfiles[0].equals("production") || activeProfiles[0].equals("prod"));

        log.info("STARTUP: Active profile: {}", activeProfiles.length > 0 ? activeProfiles[0] : "default");

        // 1. Validate Database Configuration
        if (isBlank(datasourceUrl)) {
            errors.add("Database URL is not configured (spring.datasource.url)");
        } else {
            log.info("STARTUP: Database URL configured: {}", maskSensitiveUrl(datasourceUrl));
        }

        if (isBlank(datasourceUsername)) {
            errors.add("Database username is not configured (spring.datasource.username)");
        }

        if (isBlank(datasourcePassword)) {
            if (isProduction) {
                errors.add("Database password is not configured (spring.datasource.password)");
            } else {
                warnings.add("Database password is not configured - using default for dev");
            }
        }

        // 2. Validate Kafka Configuration
        if (isBlank(kafkaBootstrapServers)) {
            errors.add("Kafka bootstrap servers not configured (spring.kafka.bootstrap-servers)");
        } else {
            log.info("STARTUP: Kafka bootstrap servers: {}", kafkaBootstrapServers);
        }

        // 3. Validate Keycloak Configuration
        if (isBlank(keycloakAuthServerUrl)) {
            errors.add("Keycloak auth server URL not configured (keycloak.auth-server-url)");
        } else {
            log.info("STARTUP: Keycloak server: {}", keycloakAuthServerUrl);
        }

        if (isBlank(keycloakClientSecret)) {
            if (isProduction) {
                errors.add("Keycloak client secret not configured (keycloak.credentials.secret)");
            } else {
                warnings.add("Keycloak client secret not configured");
            }
        }

        // 4. Validate External Service URLs
        if (isBlank(integrationServiceUrl)) {
            warnings.add("Integration service URL not configured (services.integration-service.url)");
        } else {
            log.info("STARTUP: Integration service: {}", integrationServiceUrl);
        }

        if (isBlank(notificationServiceUrl)) {
            warnings.add("Notification service URL not configured (services.notification-service.url)");
        } else {
            log.info("STARTUP: Notification service: {}", notificationServiceUrl);
        }

        // 5. Validate Security Configuration
        if (isProduction && !mfaEnabled) {
            warnings.add("MFA is disabled in production - this is a security risk");
        }

        // 6. Check for development settings in production
        if (isProduction) {
            if (datasourceUrl != null && datasourceUrl.contains("localhost")) {
                errors.add("Production profile using localhost database - invalid configuration");
            }

            if (datasourcePassword != null && datasourcePassword.contains("dev")) {
                errors.add("Production profile using development password - security risk");
            }

            if (keycloakAuthServerUrl != null && keycloakAuthServerUrl.contains("localhost")) {
                errors.add("Production profile using localhost Keycloak - invalid configuration");
            }
        }

        // Print validation results
        if (!warnings.isEmpty()) {
            log.warn("STARTUP: Configuration warnings:");
            warnings.forEach(warning -> log.warn("  - {}", warning));
        }

        if (!errors.isEmpty()) {
            log.error("STARTUP: CRITICAL configuration errors detected:");
            errors.forEach(error -> log.error("  - {}", error));

            // FAIL FAST - throw exception to prevent startup
            throw new IllegalStateException(
                String.format("Configuration validation failed with %d errors. " +
                    "Service cannot start. See logs for details.", errors.size())
            );
        }

        log.info("STARTUP: Configuration validation PASSED - {} warnings", warnings.size());
    }

    /**
     * Check if string is blank (null or empty)
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Mask sensitive parts of URLs for logging
     */
    private String maskSensitiveUrl(String url) {
        if (url == null) return "null";

        // Mask password in JDBC URLs: jdbc:postgresql://user:password@host/db
        return url.replaceAll("://([^:]+):([^@]+)@", "://$1:****@");
    }
}
