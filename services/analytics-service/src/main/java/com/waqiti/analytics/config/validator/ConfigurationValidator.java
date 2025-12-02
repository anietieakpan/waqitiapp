package com.waqiti.analytics.config.validator;

import com.waqiti.analytics.config.properties.AnalyticsProperties;
import com.waqiti.analytics.config.properties.InfluxDBProperties;
import com.waqiti.analytics.config.properties.SparkProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration Validator
 *
 * <p>Production-grade configuration validator that performs comprehensive
 * validation of all configuration properties at application startup and
 * provides detailed error reporting for misconfigured properties.
 *
 * <p>Features:
 * <ul>
 *   <li>Automatic validation on application startup</li>
 *   <li>Detailed constraint violation reporting</li>
 *   <li>Cross-property validation (e.g., executor memory >= driver memory)</li>
 *   <li>Environment-aware validation messages</li>
 *   <li>Fail-fast behavior for critical configuration errors</li>
 * </ul>
 *
 * @author Waqiti Analytics Team
 * @since 1.0.0
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigurationValidator {

    private final Validator validator;
    private final AnalyticsProperties analyticsProperties;
    private final InfluxDBProperties influxDBProperties;
    private final SparkProperties sparkProperties;

    /**
     * Validates all configuration properties when application is ready
     *
     * @param event application ready event
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateConfigurationOnStartup(ApplicationReadyEvent event) {
        log.info("========================================");
        log.info("Starting Configuration Validation");
        log.info("========================================");

        boolean hasErrors = false;

        // Validate Analytics Properties
        hasErrors |= validateAndReport("Analytics", analyticsProperties);

        // Validate InfluxDB Properties
        hasErrors |= validateAndReport("InfluxDB", influxDBProperties);

        // Validate Spark Properties
        hasErrors |= validateAndReport("Spark", sparkProperties);

        // Cross-property validations
        hasErrors |= validateCrossPropertyConstraints();

        if (hasErrors) {
            log.error("========================================");
            log.error("Configuration Validation FAILED");
            log.error("Please fix the configuration errors above");
            log.error("========================================");
            throw new IllegalStateException("Configuration validation failed. Check logs for details.");
        }

        log.info("========================================");
        log.info("Configuration Validation PASSED");
        log.info("All configuration properties are valid");
        log.info("========================================");
    }

    /**
     * Validates a configuration object and reports violations
     *
     * @param name configuration name for logging
     * @param config configuration object to validate
     * @param <T> configuration type
     * @return true if there are validation errors
     */
    private <T> boolean validateAndReport(String name, T config) {
        Set<ConstraintViolation<T>> violations = validator.validate(config);

        if (!violations.isEmpty()) {
            log.error("----------------------------------------");
            log.error("{} Configuration Errors:", name);
            log.error("----------------------------------------");

            violations.forEach(violation -> {
                String propertyPath = violation.getPropertyPath().toString();
                String message = violation.getMessage();
                Object invalidValue = violation.getInvalidValue();

                log.error("  Property: {}", propertyPath);
                log.error("  Error: {}", message);
                log.error("  Invalid Value: {}", invalidValue);
                log.error("");
            });

            return true;
        }

        log.info("{} Configuration: ✓ Valid", name);
        return false;
    }

    /**
     * Validates cross-property constraints that span multiple configuration classes
     *
     * @return true if there are validation errors
     */
    private boolean validateCrossPropertyConstraints() {
        boolean hasErrors = false;

        // Validate Spark memory configuration
        if (!sparkProperties.isMemoryConfigurationValid()) {
            log.error("----------------------------------------");
            log.error("Spark Cross-Property Validation Error:");
            log.error("----------------------------------------");
            log.error("  Executor memory ({}) must be >= driver memory ({})",
                    sparkProperties.getExecutor().getMemory(),
                    sparkProperties.getDriver().getMemory());
            log.error("");
            hasErrors = true;
        }

        // Validate InfluxDB configuration if real-time analytics is enabled
        if (analyticsProperties.getRealTime().isEnabled() && !influxDBProperties.isConfigured()) {
            log.warn("----------------------------------------");
            log.warn("InfluxDB Configuration Warning:");
            log.warn("----------------------------------------");
            log.warn("  Real-time analytics is enabled but InfluxDB is not fully configured");
            log.warn("  Missing: {}", getMissingInfluxDBProperties());
            log.warn("");
        }

        // Validate retention policies are logical
        int rawDataDays = analyticsProperties.getRetention().getRawDataDays();
        int aggregatedDataDays = analyticsProperties.getRetention().getAggregatedDataDays();

        if (rawDataDays > aggregatedDataDays) {
            log.warn("----------------------------------------");
            log.warn("Data Retention Configuration Warning:");
            log.warn("----------------------------------------");
            log.warn("  Raw data retention ({} days) > aggregated data retention ({} days)",
                    rawDataDays, aggregatedDataDays);
            log.warn("  This may lead to data gaps when raw data expires before aggregated data");
            log.warn("");
        }

        return hasErrors;
    }

    /**
     * Gets a list of missing InfluxDB properties
     *
     * @return comma-separated list of missing properties
     */
    private String getMissingInfluxDBProperties() {
        return java.util.stream.Stream.of(
                influxDBProperties.getUrl() == null || influxDBProperties.getUrl().isEmpty() ? "url" : null,
                influxDBProperties.getToken() == null || influxDBProperties.getToken().isEmpty() ? "token" : null,
                influxDBProperties.getOrg() == null || influxDBProperties.getOrg().isEmpty() ? "org" : null,
                influxDBProperties.getBucket() == null || influxDBProperties.getBucket().isEmpty() ? "bucket" : null
        )
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.joining(", "));
    }

    /**
     * Validates configuration programmatically (useful for tests)
     *
     * @param config configuration object to validate
     * @param <T> configuration type
     * @return set of constraint violations (empty if valid)
     */
    public <T> Set<ConstraintViolation<T>> validate(T config) {
        return validator.validate(config);
    }

    /**
     * Checks if a configuration object is valid
     *
     * @param config configuration object to check
     * @param <T> configuration type
     * @return true if configuration is valid
     */
    public <T> boolean isValid(T config) {
        return validator.validate(config).isEmpty();
    }
}
