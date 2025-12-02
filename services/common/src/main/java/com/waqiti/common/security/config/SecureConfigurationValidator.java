package com.waqiti.common.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Production-Ready Secure Configuration Validator
 *
 * Validates that all sensitive configurations are properly secured with Vault
 * and no hardcoded credentials exist in production environments.
 *
 * Security Requirements:
 * - PCI DSS Requirement 8.2.1: No hardcoded credentials
 * - SOC 2 CC6.1: Logical and physical access controls
 * - NIST SP 800-53 IA-5: Authenticator management
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-16
 */
@Component
@Slf4j
public class SecureConfigurationValidator implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment environment;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Value("${spring.cloud.vault.enabled:false}")
    private boolean vaultEnabled;

    // Patterns for detecting insecure configurations
    private static final Pattern HARDCODED_PASSWORD_PATTERN = Pattern.compile(
        "password.*:.*(?!\\$\\{)(?!.*vault)(?!.*${)[a-zA-Z0-9_-]+",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HARDCODED_SECRET_PATTERN = Pattern.compile(
        "(secret|api[_-]?key|token).*:.*(?!\\$\\{)(?!.*vault)[a-zA-Z0-9_-]{8,}",
        Pattern.CASE_INSENSITIVE
    );

    // Production profiles
    private static final List<String> PRODUCTION_PROFILES = Arrays.asList(
        "production", "prod", "staging", "stage", "live"
    );

    public SecureConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("🔐 Starting Secure Configuration Validation...");

        List<String> violations = new ArrayList<>();

        // 1. Check if running in production mode
        boolean isProduction = isProductionEnvironment();

        if (isProduction) {
            log.warn("⚠️ Production environment detected: {}", activeProfile);

            // 2. Validate Vault is enabled
            if (!vaultEnabled) {
                violations.add("CRITICAL: Vault is disabled in production environment");
            }

            // 3. Check database credentials
            violations.addAll(validateDatabaseConfig());

            // 4. Check Redis credentials
            violations.addAll(validateRedisConfig());

            // 5. Check Kafka credentials
            violations.addAll(validateKafkaConfig());

            // 6. Check encryption keys
            violations.addAll(validateEncryptionConfig());

            // 7. Check API keys and tokens
            violations.addAll(validateApiKeys());
        }

        // Report violations
        if (!violations.isEmpty()) {
            log.error("🚨 SECURITY CONFIGURATION VIOLATIONS DETECTED:");
            violations.forEach(v -> log.error("   ❌ {}", v));

            if (isProduction) {
                throw new SecurityConfigurationException(
                    "Production security validation failed: " + violations.size() + " violations detected"
                );
            } else {
                log.warn("⚠️ Security violations detected in non-production environment. Fix before deployment.");
            }
        } else {
            log.info("✅ Secure configuration validation passed");
        }
    }

    private boolean isProductionEnvironment() {
        return PRODUCTION_PROFILES.stream()
            .anyMatch(profile -> activeProfile.toLowerCase().contains(profile));
    }

    private List<String> validateDatabaseConfig() {
        List<String> violations = new ArrayList<>();

        String dbUsername = environment.getProperty("spring.datasource.username");
        String dbPassword = environment.getProperty("spring.datasource.password");
        String vaultDbEnabled = environment.getProperty("spring.cloud.vault.database.enabled");

        if (dbPassword != null && !dbPassword.isEmpty()) {
            if (isHardcodedValue(dbPassword)) {
                violations.add("Database password appears to be hardcoded. Use Vault: ${vault.database.password}");
            }
        } else if (!"true".equals(vaultDbEnabled)) {
            violations.add("Database password is empty and Vault database integration is disabled");
        }

        if (dbUsername != null && isHardcodedValue(dbUsername) && !dbUsername.contains("vault")) {
            violations.add("Database username should use Vault dynamic credentials");
        }

        return violations;
    }

    private List<String> validateRedisConfig() {
        List<String> violations = new ArrayList<>();

        String redisPassword = environment.getProperty("spring.data.redis.password");

        if (redisPassword != null && !redisPassword.isEmpty() && isHardcodedValue(redisPassword)) {
            violations.add("Redis password appears to be hardcoded. Use Vault: ${vault.redis.password}");
        }

        return violations;
    }

    private List<String> validateKafkaConfig() {
        List<String> violations = new ArrayList<>();

        String truststorePassword = environment.getProperty("spring.kafka.ssl.trust-store-password");
        String keystorePassword = environment.getProperty("spring.kafka.ssl.key-store-password");

        if (truststorePassword != null && !truststorePassword.isEmpty() && isHardcodedValue(truststorePassword)) {
            violations.add("Kafka truststore password appears to be hardcoded. Use Vault: ${vault.kafka.ssl.truststore-password}");
        }

        if (keystorePassword != null && !keystorePassword.isEmpty() && isHardcodedValue(keystorePassword)) {
            violations.add("Kafka keystore password appears to be hardcoded. Use Vault: ${vault.kafka.ssl.keystore-password}");
        }

        return violations;
    }

    private List<String> validateEncryptionConfig() {
        List<String> violations = new ArrayList<>();

        String encryptionKey = environment.getProperty("waqiti.security.encryption.key");
        String jwtSecret = environment.getProperty("waqiti.security.jwt.secret");

        if (encryptionKey != null && !encryptionKey.isEmpty() && isHardcodedValue(encryptionKey)) {
            violations.add("Encryption key appears to be hardcoded. Use Vault: ${vault.encryption.key}");
        }

        if (jwtSecret != null && !jwtSecret.isEmpty() && isHardcodedValue(jwtSecret)) {
            violations.add("JWT secret appears to be hardcoded. Use Vault: ${vault.jwt.secret}");
        }

        return violations;
    }

    private List<String> validateApiKeys() {
        List<String> violations = new ArrayList<>();

        // Check common API key patterns
        String[] apiKeyProperties = {
            "stripe.api.key",
            "twilio.api.key",
            "sendgrid.api.key",
            "aws.access.key",
            "aws.secret.key"
        };

        for (String prop : apiKeyProperties) {
            String value = environment.getProperty(prop);
            if (value != null && !value.isEmpty() && isHardcodedValue(value)) {
                violations.add(prop + " appears to be hardcoded. Use Vault: ${vault." + prop + "}");
            }
        }

        return violations;
    }

    /**
     * Check if a value appears to be hardcoded (not a placeholder or Vault reference)
     */
    private boolean isHardcodedValue(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        // Check if it's a Spring property placeholder
        if (value.startsWith("${") && value.endsWith("}")) {
            return false;
        }

        // Check if it references Vault
        if (value.contains("vault.")) {
            return false;
        }

        // Check if it's an environment variable reference
        if (value.startsWith("$") || value.contains("env.")) {
            return false;
        }

        // If it's a plain string with actual characters, it's likely hardcoded
        return value.length() > 3 && !value.equals("null") && !value.equals("none");
    }

    /**
     * Custom exception for security configuration failures
     */
    public static class SecurityConfigurationException extends RuntimeException {
        public SecurityConfigurationException(String message) {
            super(message);
        }
    }
}
