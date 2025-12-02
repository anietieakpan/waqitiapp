package com.waqiti.common.config;

import com.waqiti.common.config.validation.ConfigurationValidator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * CRITICAL SECURITY: Vault Configuration Validator
 *
 * <p>Enforces HTTPS for HashiCorp Vault connections in production.
 * Prevents secrets from being transmitted over unencrypted HTTP.
 *
 * <p><b>Security Requirements:</b>
 * <ul>
 *   <li>Vault URI MUST use HTTPS in production</li>
 *   <li>Vault URI MUST NOT be localhost in production/staging</li>
 *   <li>Fail-fast MUST be enabled in production</li>
 *   <li>Token authentication MUST be configured</li>
 * </ul>
 *
 * <p><b>Production Impact:</b>
 * This validation prevents a critical security vulnerability where secrets
 * could be intercepted in transit if HTTP is used instead of HTTPS.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-23
 */
@Slf4j
@Configuration
public class VaultConfigurationValidator extends ConfigurationValidator {

    @Value("${spring.cloud.vault.uri:}")
    private String vaultUri;

    @Value("${spring.cloud.vault.token:}")
    private String vaultToken;

    @Value("${spring.cloud.vault.fail-fast:false}")
    private boolean vaultFailFast;

    @Value("${spring.cloud.vault.enabled:true}")
    private boolean vaultEnabled;

    public VaultConfigurationValidator(Environment environment) {
        super(environment);
    }

    /**
     * CRITICAL SECURITY VALIDATION
     * Validates Vault configuration to prevent security vulnerabilities
     */
    @PostConstruct
    public void validateVaultConfiguration() {
        if (!vaultEnabled) {
            log.warn("⚠️  Vault is DISABLED - secrets management not active");
            log.warn("⚠️  This is acceptable in development but CRITICAL in production");

            if (isProduction()) {
                addError("Vault MUST be enabled in production (set spring.cloud.vault.enabled=true)");
            }
            return;
        }

        log.info("====================================================================");
        log.info("         VAULT CONFIGURATION - SECURITY VALIDATION");
        log.info("====================================================================");

        // 1. Require Vault URI
        requireNonEmpty("spring.cloud.vault.uri", vaultUri,
            "Vault URI is required when Vault is enabled");

        if (vaultUri != null && !vaultUri.trim().isEmpty()) {
            // 2. CRITICAL: Enforce HTTPS in production
            if (isProduction() || isStaging()) {
                if (!vaultUri.toLowerCase().startsWith("https://")) {
                    addError(String.format(
                        "SECURITY CRITICAL: Vault URI MUST use HTTPS in production/staging. " +
                        "Got: %s. This prevents secrets from being transmitted in cleartext. " +
                        "Update to: https://<vault-server>:8200",
                        vaultUri
                    ));
                    log.error("🚨 SECURITY VIOLATION: Vault configured with HTTP in production: {}", vaultUri);
                }
            }

            // 3. Prevent localhost in production
            requireNotLocalhost("spring.cloud.vault.uri", vaultUri,
                "Vault server must be a proper hostname in production, not localhost");
        }

        // 4. Require authentication
        if (vaultToken == null || vaultToken.trim().isEmpty()) {
            if (isProduction()) {
                addError("Vault token is required in production (set spring.cloud.vault.token or use AppRole authentication)");
            } else {
                log.warn("⚠️  Vault token not configured - authentication may fail");
            }
        }

        // 5. Enforce fail-fast in production
        if (isProduction() && !vaultFailFast) {
            addError("Vault fail-fast MUST be enabled in production " +
                "(set spring.cloud.vault.fail-fast=true). " +
                "This ensures application doesn't start if secrets are unavailable.");
            log.error("🚨 CONFIGURATION ERROR: Vault fail-fast disabled in production");
        }

        // Execute validation
        super.validateConfiguration();

        if (validationErrors.isEmpty()) {
            log.info("✅ Vault configuration validation PASSED");
            log.info("✅ Vault URI: {}", maskSensitiveUrl(vaultUri));
            log.info("✅ Vault Fail-Fast: {}", vaultFailFast);
            log.info("✅ Vault Token: {}", vaultToken != null ? "CONFIGURED" : "NOT SET");
        }

        log.info("====================================================================");
    }

    /**
     * Mask sensitive URL components for logging
     */
    private String maskSensitiveUrl(String url) {
        if (url == null) return "NOT SET";

        // Mask any embedded credentials in URL
        return url.replaceAll("://[^@]+@", "://*****:*****@");
    }

    /**
     * Additional validation for Vault backend configurations
     */
    @PostConstruct
    public void validateVaultBackends() {
        if (!vaultEnabled) return;

        String kvBackend = getProperty("spring.cloud.vault.kv.backend", "secret");
        String databaseBackend = getProperty("spring.cloud.vault.database.backend", "database");

        log.debug("Vault KV Backend: {}", kvBackend);
        log.debug("Vault Database Backend: {}", databaseBackend);

        // Validate backend paths don't contain suspicious characters
        if (kvBackend != null && (kvBackend.contains("..") || kvBackend.contains("/"))) {
            log.warn("⚠️  Vault KV backend path contains suspicious characters: {}", kvBackend);
        }
    }
}
