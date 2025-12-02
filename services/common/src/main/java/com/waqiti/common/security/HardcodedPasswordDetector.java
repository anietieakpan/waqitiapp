package com.waqiti.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Hardcoded Password Detector
 *
 * <p>This component scans all Spring configuration properties at application startup
 * to detect hardcoded passwords, API keys, secrets, and other sensitive values.
 *
 * <p><b>Detection Strategy:</b>
 * <ul>
 *   <li>Scans property keys matching: password, secret, key, token, credential</li>
 *   <li>Checks if values are hardcoded (not from environment/vault)</li>
 *   <li>Excludes test properties and known safe patterns</li>
 *   <li>Fails application startup if violations found in production</li>
 * </ul>
 *
 * <p><b>Allowed Patterns:</b>
 * <ul>
 *   <li>Environment variables: ${VAR_NAME}</li>
 *   <li>Vault references: ${VAULT_*}</li>
 *   <li>Empty values: "" or null</li>
 *   <li>Test profiles: spring.profiles.active contains "test"</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * This detector runs automatically on application startup. No configuration needed.
 *
 * <p><b>Configuration:</b>
 * Disable in test profiles:
 * <pre>
 * waqiti:
 *   security:
 *     hardcoded-password-detector:
 *       enabled: false  # For test profiles only
 * </pre>
 *
 * @author Platform Security Team
 * @version 1.0.0
 * @since 2025-11-23
 */
@Component
public class HardcodedPasswordDetector implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(HardcodedPasswordDetector.class);

    /**
     * Property name patterns that indicate sensitive values
     */
    private static final List<Pattern> SENSITIVE_PROPERTY_PATTERNS = List.of(
        Pattern.compile(".*password.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*secret.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*key.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*token.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*credential.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*apikey.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*api-key.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile(".*private.*", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Patterns that indicate the value is dynamically loaded (not hardcoded)
     */
    private static final List<Pattern> SAFE_VALUE_PATTERNS = List.of(
        Pattern.compile("^\\$\\{.*\\}$"),                    // ${VAR_NAME}
        Pattern.compile("^\\$\\{VAULT_.*\\}$"),             // ${VAULT_*}
        Pattern.compile("^\\$\\{vault:.*\\}$"),             // ${vault:path}
        Pattern.compile("^\\s*$"),                           // Empty string
        Pattern.compile("^null$", Pattern.CASE_INSENSITIVE), // null
        Pattern.compile("^ENC\\(.*\\)$")                     // Encrypted with Jasypt
    );

    /**
     * Property names to exclude from checking (known safe patterns)
     */
    private static final List<Pattern> EXCLUDED_PROPERTY_PATTERNS = List.of(
        Pattern.compile(".*\\.ssl\\.key-password"),  // SSL key passwords (can be empty)
        Pattern.compile(".*\\.key-password"),        // Generic key passwords
        Pattern.compile(".*\\.truststore-password"), // Truststore passwords (can be empty)
        Pattern.compile(".*\\.keystore-password"),   // Keystore passwords (can be empty)
        Pattern.compile("spring\\.security\\.user\\.password"), // Spring Security default user (test only)
        Pattern.compile("logging\\..*"),             // Logging configuration
        Pattern.compile("management\\..*password.*") // Management endpoints
    );

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment environment = event.getApplicationContext().getEnvironment();

        // Check if detector is enabled
        boolean enabled = environment.getProperty(
            "waqiti.security.hardcoded-password-detector.enabled",
            Boolean.class,
            true
        );

        if (!enabled) {
            log.info("Hardcoded password detector is disabled");
            return;
        }

        // Check if we're in a test profile
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isTestProfile = false;
        for (String profile : activeProfiles) {
            if (profile.contains("test")) {
                isTestProfile = true;
                break;
            }
        }

        log.info("╔════════════════════════════════════════════════════════════╗");
        log.info("║ HARDCODED PASSWORD DETECTION - SCANNING CONFIGURATION      ║");
        log.info("║ Profiles: {}", (Object) activeProfiles);
        log.info("╚════════════════════════════════════════════════════════════╝");

        List<HardcodedPasswordViolation> violations = scanConfiguration(environment);

        if (violations.isEmpty()) {
            log.info("✅ No hardcoded passwords detected");
            return;
        }

        // Log violations
        log.error("❌ SECURITY VIOLATION: {} hardcoded password(s) detected", violations.size());
        for (HardcodedPasswordViolation violation : violations) {
            log.error("  - Property: {}", violation.propertyName);
            log.error("    Value: {} (masked)", maskValue(violation.propertyValue));
            log.error("    Source: {}", violation.propertySource);
        }

        log.error("");
        log.error("╔════════════════════════════════════════════════════════════╗");
        log.error("║ ACTION REQUIRED: REMOVE HARDCODED PASSWORDS                ║");
        log.error("╚════════════════════════════════════════════════════════════╝");
        log.error("");
        log.error("Hardcoded passwords are a critical security vulnerability.");
        log.error("Please use one of the following secure approaches:");
        log.error("");
        log.error("1. Environment Variables:");
        log.error("   password: ${{VAULT_DB_PASSWORD:}}");
        log.error("");
        log.error("2. HashiCorp Vault:");
        log.error("   password: ${{vault:secret/database/password}}");
        log.error("");
        log.error("3. Spring Cloud Config:");
        log.error("   Load from config server with encryption");
        log.error("");

        // In production or staging, fail the application startup
        boolean isProduction = false;
        for (String profile : activeProfiles) {
            if (profile.contains("prod") || profile.contains("staging")) {
                isProduction = true;
                break;
            }
        }

        if (isProduction) {
            throw new HardcodedPasswordException(
                "Application startup failed: " + violations.size() +
                " hardcoded password(s) detected. See logs for details."
            );
        } else if (!isTestProfile) {
            // In dev, just log a warning
            log.warn("⚠️  Hardcoded passwords detected in development environment");
            log.warn("⚠️  These MUST be fixed before deploying to production");
        }
    }

    /**
     * Scans all configuration properties for hardcoded passwords
     */
    private List<HardcodedPasswordViolation> scanConfiguration(Environment environment) {
        List<HardcodedPasswordViolation> violations = new ArrayList<>();

        if (!(environment instanceof ConfigurableEnvironment)) {
            return violations;
        }

        ConfigurableEnvironment configurableEnvironment = (ConfigurableEnvironment) environment;

        for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource) {
                EnumerablePropertySource<?> enumerablePropertySource =
                    (EnumerablePropertySource<?>) propertySource;

                for (String propertyName : enumerablePropertySource.getPropertyNames()) {
                    // Check if this property might contain sensitive data
                    if (isSensitiveProperty(propertyName)) {
                        Object value = environment.getProperty(propertyName);

                        if (value != null && isHardcodedValue(value.toString())) {
                            violations.add(new HardcodedPasswordViolation(
                                propertyName,
                                value.toString(),
                                propertySource.getName()
                            ));
                        }
                    }
                }
            }
        }

        return violations;
    }

    /**
     * Checks if a property name indicates it might contain sensitive data
     */
    private boolean isSensitiveProperty(String propertyName) {
        // Check if excluded
        for (Pattern excludedPattern : EXCLUDED_PROPERTY_PATTERNS) {
            if (excludedPattern.matcher(propertyName).matches()) {
                return false;
            }
        }

        // Check if sensitive
        for (Pattern sensitivePattern : SENSITIVE_PROPERTY_PATTERNS) {
            if (sensitivePattern.matcher(propertyName).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a value appears to be hardcoded (not from environment/vault)
     */
    private boolean isHardcodedValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        // Check if value matches a safe pattern
        for (Pattern safePattern : SAFE_VALUE_PATTERNS) {
            if (safePattern.matcher(value).matches()) {
                return false;
            }
        }

        // If we get here, it's likely a hardcoded value
        return true;
    }

    /**
     * Masks a sensitive value for logging (shows first and last 2 characters only)
     */
    private String maskValue(String value) {
        if (value == null || value.length() <= 4) {
            return "***";
        }

        return value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    /**
     * Represents a hardcoded password violation
     */
    private static class HardcodedPasswordViolation {
        final String propertyName;
        final String propertyValue;
        final String propertySource;

        HardcodedPasswordViolation(String propertyName, String propertyValue, String propertySource) {
            this.propertyName = propertyName;
            this.propertyValue = propertyValue;
            this.propertySource = propertySource;
        }
    }

    /**
     * Exception thrown when hardcoded passwords are detected in production
     */
    public static class HardcodedPasswordException extends RuntimeException {
        public HardcodedPasswordException(String message) {
            super(message);
        }
    }
}
