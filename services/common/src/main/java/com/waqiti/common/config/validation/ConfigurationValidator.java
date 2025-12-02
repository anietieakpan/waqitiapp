package com.waqiti.common.config.validation;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * CRITICAL PRODUCTION SERVICE: Configuration Validation Framework
 *
 * <p>Validates all required configuration properties at application startup.
 * Fails fast if critical configuration is missing or invalid in production.
 *
 * <p><b>Production Safety Features:</b>
 * <ul>
 *   <li>Fail-fast validation on startup (prevents runtime errors)</li>
 *   <li>Environment-specific validation (strict in production)</li>
 *   <li>URL format validation (prevents localhost in production)</li>
 *   <li>API key format validation (detects placeholder values)</li>
 *   <li>HTTPS enforcement for sensitive endpoints</li>
 *   <li>Comprehensive error reporting</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>
 * &#64;Component
 * public class MyServiceConfig extends ConfigurationValidator {
 *     &#64;Value("${my.api.key}")
 *     private String apiKey;
 *
 *     &#64;PostConstruct
 *     public void validate() {
 *         super.validateConfiguration();
 *         requireNonEmpty("my.api.key", apiKey, "API key is required");
 *         requireHttps("my.api.url", apiUrl, "API must use HTTPS in production");
 *     }
 * }
 * </pre>
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-23
 */
@Slf4j
@Component
public abstract class ConfigurationValidator {

    protected final Environment environment;
    protected final List<String> validationErrors = new ArrayList<>();

    private static final Pattern API_KEY_PLACEHOLDER = Pattern.compile("(?i)(your|test|example|placeholder|changeme|todo|fixme)");
    private static final Pattern URL_LOCALHOST = Pattern.compile("(?i)(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0)");

    protected ConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    /**
     * Check if running in production environment
     */
    protected boolean isProduction() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("production".equalsIgnoreCase(profile) || "prod".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if running in staging environment
     */
    protected boolean isStaging() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("staging".equalsIgnoreCase(profile) || "stage".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Require a configuration property to be non-null and non-empty
     *
     * @param propertyName Property name for error messages
     * @param value Actual value
     * @param errorMessage Error message if validation fails
     */
    protected void requireNonEmpty(String propertyName, String value, String errorMessage) {
        if (value == null || value.trim().isEmpty()) {
            validationErrors.add(String.format("%s: %s (property: %s)",
                propertyName, errorMessage, propertyName));
            log.error("CONFIGURATION ERROR: {}", errorMessage);
        }
    }

    /**
     * Require a configuration property only in production/staging
     *
     * @param propertyName Property name
     * @param value Actual value
     * @param errorMessage Error message
     */
    protected void requireInProduction(String propertyName, String value, String errorMessage) {
        if (isProduction() || isStaging()) {
            requireNonEmpty(propertyName, value, errorMessage);
        } else {
            log.debug("Property {} not required in {} environment",
                propertyName, String.join(",", environment.getActiveProfiles()));
        }
    }

    /**
     * Require URL to use HTTPS in production
     *
     * @param propertyName Property name
     * @param url URL value
     * @param errorMessage Error message
     */
    protected void requireHttps(String propertyName, String url, String errorMessage) {
        if (url == null || url.trim().isEmpty()) {
            validationErrors.add(String.format("%s: URL is required (%s)", propertyName, errorMessage));
            return;
        }

        if (isProduction() && !url.toLowerCase().startsWith("https://")) {
            validationErrors.add(String.format("%s: Must use HTTPS in production (got: %s). %s",
                propertyName, url, errorMessage));
            log.error("SECURITY ERROR: Non-HTTPS URL in production: {} = {}", propertyName, url);
        }
    }

    /**
     * Validate URL is not localhost in production
     *
     * @param propertyName Property name
     * @param url URL value
     * @param errorMessage Error message
     */
    protected void requireNotLocalhost(String propertyName, String url, String errorMessage) {
        if (url == null || url.trim().isEmpty()) {
            return; // Skip if empty (will be caught by requireNonEmpty if needed)
        }

        if ((isProduction() || isStaging()) && URL_LOCALHOST.matcher(url).find()) {
            validationErrors.add(String.format("%s: Localhost URLs not allowed in production (got: %s). %s",
                propertyName, url, errorMessage));
            log.error("CONFIGURATION ERROR: Localhost URL in production: {} = {}", propertyName, url);
        }
    }

    /**
     * Validate API key is not a placeholder value
     *
     * DESIGN NOTE: This method is 'protected' to support the Template Method Pattern.
     * Subclasses in notification-service and payment-service use this method to
     * validate external provider API keys (Stripe, PayPal, Twilio, etc.).
     *
     * @param propertyName Property name
     * @param apiKey API key value
     * @param errorMessage Error message
     */
    protected void requireValidApiKey(String propertyName, String apiKey, String errorMessage) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            if (isProduction()) {
                validationErrors.add(String.format("%s: API key is required in production. %s",
                    propertyName, errorMessage));
            }
            return;
        }

        if (API_KEY_PLACEHOLDER.matcher(apiKey).find()) {
            validationErrors.add(String.format("%s: API key appears to be a placeholder (contains '%s'). %s",
                propertyName, apiKey, errorMessage));
            log.error("CONFIGURATION ERROR: Placeholder API key detected: {}", propertyName);
        }

        if (apiKey.length() < 10) {
            validationErrors.add(String.format("%s: API key too short (minimum 10 characters). %s",
                propertyName, errorMessage));
            log.warn("CONFIGURATION WARNING: Suspiciously short API key: {} (length: {})",
                propertyName, apiKey.length());
        }
    }

    /**
     * Require a numeric value to be within a range
     *
     * @param propertyName Property name
     * @param value Actual value
     * @param min Minimum value (inclusive)
     * @param max Maximum value (inclusive)
     * @param errorMessage Error message
     */
    protected void requireInRange(String propertyName, Integer value, int min, int max, String errorMessage) {
        if (value == null) {
            validationErrors.add(String.format("%s: Value is required. %s", propertyName, errorMessage));
            return;
        }

        if (value < min || value > max) {
            validationErrors.add(String.format("%s: Value must be between %d and %d (got: %d). %s",
                propertyName, min, max, value, errorMessage));
        }
    }

    /**
     * Require a value to match a predicate
     *
     * @param propertyName Property name
     * @param value Value to test
     * @param predicate Validation predicate
     * @param errorMessage Error message
     */
    protected <T> void require(String propertyName, T value, Predicate<T> predicate, String errorMessage) {
        if (value == null || !predicate.test(value)) {
            validationErrors.add(String.format("%s: %s", propertyName, errorMessage));
        }
    }

    /**
     * Validate database connection URL
     *
     * @param propertyName Property name
     * @param jdbcUrl JDBC URL
     */
    protected void requireValidDatabaseUrl(String propertyName, String jdbcUrl) {
        requireNonEmpty(propertyName, jdbcUrl, "Database URL is required");

        if (jdbcUrl == null) return;

        // Check for localhost in production
        requireNotLocalhost(propertyName, jdbcUrl,
            "Database must not be localhost in production");

        // Check for SSL/TLS in production
        if (isProduction() && jdbcUrl.contains("postgresql") && !jdbcUrl.contains("sslmode=require")) {
            validationErrors.add(String.format("%s: PostgreSQL must use SSL in production (add ?sslmode=require). Got: %s",
                propertyName, jdbcUrl));
            log.warn("SECURITY WARNING: PostgreSQL without SSL in production: {}", propertyName);
        }
    }

    /**
     * Validate Redis connection URL
     *
     * @param propertyName Property name
     * @param redisUrl Redis URL
     */
    protected void requireValidRedisUrl(String propertyName, String redisUrl) {
        requireNonEmpty(propertyName, redisUrl, "Redis URL is required");

        if (redisUrl == null) return;

        // Check for localhost in production
        requireNotLocalhost(propertyName, redisUrl,
            "Redis must not be localhost in production");

        // Check for TLS in production
        if (isProduction() && !redisUrl.startsWith("rediss://")) {
            log.warn("SECURITY WARNING: Redis without TLS in production: {} = {}", propertyName, redisUrl);
        }
    }

    /**
     * Validate Kafka bootstrap servers
     *
     * @param propertyName Property name
     * @param bootstrapServers Kafka bootstrap servers
     */
    protected void requireValidKafkaServers(String propertyName, String bootstrapServers) {
        requireNonEmpty(propertyName, bootstrapServers, "Kafka bootstrap servers are required");

        if (bootstrapServers == null) return;

        // Check for localhost in production
        requireNotLocalhost(propertyName, bootstrapServers,
            "Kafka must not be localhost in production");
    }

    /**
     * Add a custom validation error
     *
     * @param errorMessage Error message
     */
    protected void addError(String errorMessage) {
        validationErrors.add(errorMessage);
        log.error("VALIDATION ERROR: {}", errorMessage);
    }

    /**
     * Execute validation and throw exception if errors found
     *
     * <p>Call this method in your @PostConstruct method after all validations.
     *
     * @throws ConfigurationValidationException if validation errors found
     */
    protected void validateConfiguration() {
        if (!validationErrors.isEmpty()) {
            String errorReport = buildErrorReport();
            log.error("CONFIGURATION VALIDATION FAILED:\n{}", errorReport);

            if (isProduction() || isStaging()) {
                throw new ConfigurationValidationException(
                    "Configuration validation failed with " + validationErrors.size() + " error(s). " +
                    "See logs for details or fix the following issues:\n" + errorReport
                );
            } else {
                log.warn("Configuration validation found issues but allowing startup in {} environment",
                    String.join(",", environment.getActiveProfiles()));
            }
        } else {
            log.info("Configuration validation passed successfully");
        }
    }

    /**
     * Build formatted error report
     */
    private String buildErrorReport() {
        StringBuilder report = new StringBuilder();
        report.append("================================================================================\n");
        report.append("                 CONFIGURATION VALIDATION ERRORS\n");
        report.append("================================================================================\n\n");

        for (int i = 0; i < validationErrors.size(); i++) {
            report.append(String.format("%d. %s\n", i + 1, validationErrors.get(i)));
        }

        report.append("\n================================================================================\n");
        report.append(String.format("Total Errors: %d\n", validationErrors.size()));
        report.append("================================================================================\n");

        return report.toString();
    }

    /**
     * Get property value from environment
     *
     * @param propertyName Property name
     * @return Property value or null if not found
     */
    protected String getProperty(String propertyName) {
        return environment.getProperty(propertyName);
    }

    /**
     * Get property value with default
     *
     * @param propertyName Property name
     * @param defaultValue Default value
     * @return Property value or default
     */
    protected String getProperty(String propertyName, String defaultValue) {
        return environment.getProperty(propertyName, defaultValue);
    }
}
