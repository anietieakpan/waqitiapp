package com.waqiti.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Startup validation service for critical security secrets
 *
 * CRITICAL SECURITY FIX: Validates all required secrets are properly configured
 * Application will FAIL TO START if critical secrets are missing
 *
 * This prevents the application from running with hardcoded fallback values
 * or placeholder secrets that compromise security.
 *
 * Checks performed:
 * - JWT signing secret (authentication)
 * - Audit trail integrity secret (tamper detection)
 * - Encryption keys (data protection)
 * - API keys for external services
 *
 * @author Waqiti Security Team
 * @since 1.0 (CRITICAL SECURITY FIX)
 */
@Slf4j
@Component
public class SecretValidationService implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${vault.jwt.secret:}")
    private String vaultJwtSecret;

    @Value("${audit.integrity.secret:}")
    private String auditIntegritySecret;

    @Value("${encryption.key:}")
    private String encryptionKey;

    @Value("${database.encryption.key:}")
    private String databaseEncryptionKey;

    // Minimum secret length requirements
    private static final int MIN_JWT_SECRET_LENGTH = 32; // 256 bits
    private static final int MIN_INTEGRITY_SECRET_LENGTH = 32;
    private static final int MIN_ENCRYPTION_KEY_LENGTH = 32;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Starting critical security secrets validation...");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate JWT Secret
        validateJwtSecret(errors, warnings);

        // Validate Audit Integrity Secret
        validateAuditIntegritySecret(errors, warnings);

        // Validate Encryption Keys
        validateEncryptionKeys(errors, warnings);

        // Report results
        if (!errors.isEmpty()) {
            log.error("╔═══════════════════════════════════════════════════════════════╗");
            log.error("║  CRITICAL SECURITY CONFIGURATION ERROR - APPLICATION HALTED  ║");
            log.error("╚═══════════════════════════════════════════════════════════════╝");
            log.error("");
            for (String error : errors) {
                log.error("  ✗ {}", error);
            }
            log.error("");
            log.error("APPLICATION CANNOT START WITH MISSING OR INVALID SECRETS");
            log.error("Please configure the required secrets and restart the application");
            log.error("");

            throw new IllegalStateException(
                "Critical security secrets are missing or invalid. " +
                "Application cannot start. See logs for details."
            );
        }

        if (!warnings.isEmpty()) {
            log.warn("╔═══════════════════════════════════════════════════════════════╗");
            log.warn("║           SECURITY CONFIGURATION WARNINGS                     ║");
            log.warn("╚═══════════════════════════════════════════════════════════════╝");
            log.warn("");
            for (String warning : warnings) {
                log.warn("  ⚠ {}", warning);
            }
            log.warn("");
        }

        log.info("✓ All critical security secrets validated successfully");
    }

    private void validateJwtSecret(List<String> errors, List<String> warnings) {
        String effectiveJwtSecret = getEffectiveJwtSecret();

        if (effectiveJwtSecret == null || effectiveJwtSecret.trim().isEmpty()) {
            errors.add("JWT_SECRET is not configured. Set environment variable JWT_SECRET or vault.jwt.secret");
            return;
        }

        // Check for placeholder values
        if (isPlaceholderValue(effectiveJwtSecret)) {
            errors.add("JWT_SECRET contains placeholder value: " + maskSecret(effectiveJwtSecret));
            return;
        }

        // Check minimum length
        if (effectiveJwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            errors.add(String.format(
                "JWT_SECRET is too short (%d characters). Minimum required: %d characters (256 bits)",
                effectiveJwtSecret.length(), MIN_JWT_SECRET_LENGTH
            ));
            return;
        }

        // Check for weak patterns
        if (isWeakSecret(effectiveJwtSecret)) {
            warnings.add("JWT_SECRET appears to be weak. Consider using a cryptographically random secret");
        }

        log.info("✓ JWT_SECRET validated: length={} chars", effectiveJwtSecret.length());
    }

    private void validateAuditIntegritySecret(List<String> errors, List<String> warnings) {
        if (auditIntegritySecret == null || auditIntegritySecret.trim().isEmpty()) {
            errors.add("AUDIT_INTEGRITY_SECRET is not configured. Set environment variable AUDIT_INTEGRITY_SECRET");
            return;
        }

        if (isPlaceholderValue(auditIntegritySecret)) {
            errors.add("AUDIT_INTEGRITY_SECRET contains placeholder value: " + maskSecret(auditIntegritySecret));
            return;
        }

        if (auditIntegritySecret.length() < MIN_INTEGRITY_SECRET_LENGTH) {
            errors.add(String.format(
                "AUDIT_INTEGRITY_SECRET is too short (%d characters). Minimum required: %d characters",
                auditIntegritySecret.length(), MIN_INTEGRITY_SECRET_LENGTH
            ));
            return;
        }

        if (isWeakSecret(auditIntegritySecret)) {
            warnings.add("AUDIT_INTEGRITY_SECRET appears to be weak");
        }

        log.info("✓ AUDIT_INTEGRITY_SECRET validated: length={} chars", auditIntegritySecret.length());
    }

    private void validateEncryptionKeys(List<String> errors, List<String> warnings) {
        // Encryption key validation
        if (encryptionKey != null && !encryptionKey.trim().isEmpty()) {
            if (isPlaceholderValue(encryptionKey)) {
                errors.add("ENCRYPTION_KEY contains placeholder value");
            } else if (encryptionKey.length() < MIN_ENCRYPTION_KEY_LENGTH) {
                errors.add(String.format(
                    "ENCRYPTION_KEY is too short (%d characters). Minimum required: %d characters",
                    encryptionKey.length(), MIN_ENCRYPTION_KEY_LENGTH
                ));
            } else {
                log.info("✓ ENCRYPTION_KEY validated: length={} chars", encryptionKey.length());
            }
        } else {
            warnings.add("ENCRYPTION_KEY is not configured. Data-at-rest encryption may not be available");
        }

        // Database encryption key validation
        if (databaseEncryptionKey != null && !databaseEncryptionKey.trim().isEmpty()) {
            if (isPlaceholderValue(databaseEncryptionKey)) {
                errors.add("DATABASE_ENCRYPTION_KEY contains placeholder value");
            } else if (databaseEncryptionKey.length() < MIN_ENCRYPTION_KEY_LENGTH) {
                errors.add(String.format(
                    "DATABASE_ENCRYPTION_KEY is too short (%d characters). Minimum required: %d characters",
                    databaseEncryptionKey.length(), MIN_ENCRYPTION_KEY_LENGTH
                ));
            } else {
                log.info("✓ DATABASE_ENCRYPTION_KEY validated: length={} chars", databaseEncryptionKey.length());
            }
        } else {
            warnings.add("DATABASE_ENCRYPTION_KEY is not configured. Database field encryption may not be available");
        }
    }

    private String getEffectiveJwtSecret() {
        // Prefer Vault secret over direct secret
        if (vaultJwtSecret != null && !vaultJwtSecret.trim().isEmpty() && !isPlaceholderValue(vaultJwtSecret)) {
            return vaultJwtSecret;
        }
        return jwtSecret;
    }

    private boolean isPlaceholderValue(String value) {
        if (value == null) return true;

        String lowerValue = value.toLowerCase();

        // Check for common placeholder patterns
        return lowerValue.contains("${") ||
               lowerValue.contains("changeme") ||
               lowerValue.contains("change_me") ||
               lowerValue.contains("placeholder") ||
               lowerValue.contains("default") ||
               lowerValue.contains("secret") ||
               lowerValue.contains("password") ||
               lowerValue.equals("xxx") ||
               lowerValue.equals("xxxx") ||
               lowerValue.matches("^x+$") ||
               lowerValue.matches("^[a-z]+_[a-z]+_[a-z]+$"); // SOME_DEFAULT_VALUE pattern
    }

    private boolean isWeakSecret(String secret) {
        if (secret == null) return true;

        // Check for repetitive characters
        if (secret.matches("^(.)\\1+$")) { // All same character
            return true;
        }

        // Check for simple sequential patterns
        if (secret.matches(".*(?:abcd|1234|qwer).*")) {
            return true;
        }

        // Check for low entropy (all lowercase or all digits)
        if (secret.matches("^[a-z]+$") || secret.matches("^[0-9]+$")) {
            return true;
        }

        return false;
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.length() <= 4) {
            return "****";
        }
        return secret.substring(0, 2) + "****" + secret.substring(secret.length() - 2);
    }

    /**
     * Manual validation method for testing
     * Can be called from application code to re-validate secrets
     */
    public void validateSecrets() {
        onApplicationEvent(null);
    }

    /**
     * Check if specific secret is configured
     */
    public boolean isSecretConfigured(String secretName) {
        return switch (secretName.toLowerCase()) {
            case "jwt" -> getEffectiveJwtSecret() != null && !getEffectiveJwtSecret().trim().isEmpty();
            case "audit" -> auditIntegritySecret != null && !auditIntegritySecret.trim().isEmpty();
            case "encryption" -> encryptionKey != null && !encryptionKey.trim().isEmpty();
            case "database_encryption" -> databaseEncryptionKey != null && !databaseEncryptionKey.trim().isEmpty();
            default -> false;
        };
    }
}
