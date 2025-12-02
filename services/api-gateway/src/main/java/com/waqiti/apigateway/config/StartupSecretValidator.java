package com.waqiti.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Startup Secret Validator
 *
 * Validates that all required secrets are properly configured before the gateway
 * accepts any traffic. This is a CRITICAL security component that prevents the
 * gateway from running in an insecure state due to missing credentials.
 *
 * Security Principles:
 * - Fail-fast: Crash at startup if any required secret is missing
 * - Defense in depth: Validates even if Spring property validation fails
 * - Clear diagnostics: Reports exactly which secrets are missing
 * - Production safety: Gateway never runs without all required secrets
 *
 * Required Secrets:
 * - KEYCLOAK_CLIENT_SECRET: OAuth2 client secret for Keycloak authentication
 * - REDIS_PASSWORD: Password for Redis connection (rate limiting + caching)
 *
 * Optional but Recommended:
 * - VAULT_TOKEN/VAULT_ROLE_ID: For Vault secret management
 * - SSL_KEYSTORE_PASSWORD: For HTTPS certificate
 *
 * @author Waqiti Platform Security Team
 * @version 1.0.0
 * @since 2025-11-15
 */
@Component
@Slf4j
public class StartupSecretValidator {

    @Value("${keycloak.credentials.secret:}")
    private String keycloakClientSecret;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.cloud.vault.token:}")
    private String vaultToken;

    @Value("${spring.cloud.vault.app-role.role-id:}")
    private String vaultRoleId;

    @Value("${server.ssl.key-store-password:}")
    private String sslKeystorePassword;

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    /**
     * Validates all required secrets after application context is fully initialized.
     *
     * This runs AFTER all beans are created but BEFORE the application starts
     * accepting traffic. If any required secret is missing, this method throws
     * IllegalStateException which crashes the gateway.
     *
     * @throws IllegalStateException if any required secret is missing
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateRequiredSecrets() {
        log.info("=".repeat(80));
        log.info("STARTUP SECURITY VALIDATION - Checking required secrets");
        log.info("Active Profile: {}", activeProfile);
        log.info("=".repeat(80));

        List<String> missingSecrets = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // CRITICAL: Keycloak client secret (required for authentication)
        if (isBlank(keycloakClientSecret)) {
            missingSecrets.add("KEYCLOAK_CLIENT_SECRET (keycloak.credentials.secret)");
        } else {
            log.info("✓ Keycloak client secret: CONFIGURED");
        }

        // CRITICAL: Redis password (required for rate limiting)
        if (isBlank(redisPassword)) {
            missingSecrets.add("REDIS_PASSWORD (spring.data.redis.password)");
        } else {
            log.info("✓ Redis password: CONFIGURED");
        }

        // RECOMMENDED: Vault authentication (warn if missing in production)
        boolean hasVaultAuth = !isBlank(vaultToken) || !isBlank(vaultRoleId);
        if (!hasVaultAuth) {
            if (isProductionProfile()) {
                warnings.add("Vault authentication not configured - secrets rotation disabled");
            }
            log.warn("⚠ Vault authentication: NOT CONFIGURED (using environment variables)");
        } else {
            log.info("✓ Vault authentication: CONFIGURED");
        }

        // RECOMMENDED: SSL keystore password (warn if missing with SSL enabled)
        if (isBlank(sslKeystorePassword)) {
            warnings.add("SSL keystore password not configured - HTTPS may fail");
            log.warn("⚠ SSL keystore password: NOT CONFIGURED");
        } else {
            log.info("✓ SSL keystore password: CONFIGURED");
        }

        // Log warnings
        if (!warnings.isEmpty()) {
            log.warn("=".repeat(80));
            log.warn("SECURITY WARNINGS ({} issues):", warnings.size());
            warnings.forEach(warning -> log.warn("  - {}", warning));
            log.warn("=".repeat(80));
        }

        // FAIL FAST if any critical secret is missing
        if (!missingSecrets.isEmpty()) {
            String errorMessage = String.format(
                    "FATAL SECURITY ERROR: %d required secret(s) are MISSING!%n%n" +
                    "Missing secrets:%n%s%n%n" +
                    "The API Gateway CANNOT start without these secrets.%n" +
                    "This is intentional - better to fail at startup than run insecurely.%n%n" +
                    "To fix:%n" +
                    "1. Set the required environment variables%n" +
                    "2. Configure Vault with proper secrets%n" +
                    "3. Verify Kubernetes secrets are mounted%n%n" +
                    "For local development, see: docs/DEVELOPMENT.md%n" +
                    "For production deployment, see: docs/DEPLOYMENT.md",
                    missingSecrets.size(),
                    String.join("\n", missingSecrets.stream().map(s -> "  - " + s).toList())
            );

            log.error("=".repeat(80));
            log.error(errorMessage);
            log.error("=".repeat(80));

            // CRASH THE GATEWAY
            throw new IllegalStateException("Required secrets are missing. Gateway cannot start securely.");
        }

        // SUCCESS
        log.info("=".repeat(80));
        log.info("✓ SECURITY VALIDATION PASSED - All required secrets are configured");
        log.info("✓ Gateway is secure and ready to accept traffic");
        log.info("=".repeat(80));
    }

    /**
     * Checks if a string is blank (null, empty, or only whitespace).
     *
     * @param value the string to check
     * @return true if blank, false otherwise
     */
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Checks if the application is running in production profile.
     *
     * @return true if production profile is active
     */
    private boolean isProductionProfile() {
        return activeProfile != null && activeProfile.contains("production");
    }
}
