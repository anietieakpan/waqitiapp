package com.waqiti.common.config.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Secure Configuration Properties
 *
 * Centralized configuration for security validation rules
 * and requirements across all Waqiti services.
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-06
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "waqiti.security.config-validation")
@Validated
public class SecureConfigurationProperties {

    /**
     * Enable/disable configuration validation
     * (should only be disabled in test environments)
     */
    private boolean enabled = true;

    /**
     * Fail-fast on validation errors
     */
    private boolean failFast = true;

    /**
     * Minimum password length (characters)
     */
    @Min(12)
    private int minPasswordLength = 16;

    /**
     * Minimum secret length (bytes)
     */
    @Min(24)
    private int minSecretLength = 32;

    /**
     * Recommended secret length (bytes)
     */
    @Min(32)
    private int recommendedSecretLength = 64;

    /**
     * Require Vault in production
     */
    private boolean requireVaultInProduction = true;

    /**
     * Require database password in all environments
     */
    private boolean requireDatabasePassword = true;

    /**
     * Require Redis password for non-localhost
     */
    private boolean requireRedisPassword = true;

    /**
     * Require JWT secret
     */
    private boolean requireJwtSecret = true;

    /**
     * Require encryption master key
     */
    private boolean requireEncryptionKey = true;

    /**
     * Properties that must not have empty defaults
     */
    @NotEmpty
    private List<String> requiredProperties = new ArrayList<>(List.of(
        "spring.datasource.username",
        "spring.datasource.password"
    ));

    /**
     * Weak passwords to reject
     */
    @NotEmpty
    private List<String> weakPasswords = new ArrayList<>(List.of(
        "password", "Password1", "admin", "changeme", "secret",
        "12345678", "password123", "qwerty", "test", "demo"
    ));

    /**
     * Environment-specific validation rules
     */
    private EnvironmentValidationRules production = new EnvironmentValidationRules();
    private EnvironmentValidationRules staging = new EnvironmentValidationRules();
    private EnvironmentValidationRules development = new EnvironmentValidationRules();

    @Data
    public static class EnvironmentValidationRules {
        private boolean requireVault = false;
        private boolean requireHttps = false;
        private boolean requireStrongSecrets = true;
        private int minSecretLength = 32;
    }

    /**
     * Initialize production defaults
     */
    public SecureConfigurationProperties() {
        // Production defaults
        production.setRequireVault(true);
        production.setRequireHttps(true);
        production.setRequireStrongSecrets(true);
        production.setMinSecretLength(64);

        // Staging defaults
        staging.setRequireVault(true);
        staging.setRequireHttps(true);
        staging.setRequireStrongSecrets(true);
        staging.setMinSecretLength(32);

        // Development defaults (more lenient)
        development.setRequireVault(false);
        development.setRequireHttps(false);
        development.setRequireStrongSecrets(false);
        development.setMinSecretLength(16);
    }
}
