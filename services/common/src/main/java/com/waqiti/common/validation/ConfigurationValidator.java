package com.waqiti.common.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Comprehensive configuration validator for all services
 * Validates critical configuration properties at startup
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigurationValidator {
    
    private final Environment environment;
    private final List<ConfigurationValidationRule> validationRules = new ArrayList<>();
    
    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        log.info("Starting configuration validation...");
        
        initializeValidationRules();
        ConfigurationValidationResult result = performValidation();
        
        if (result.hasErrors()) {
            log.error("Configuration validation failed with {} errors:", result.getErrors().size());
            result.getErrors().forEach(error -> log.error("  - {}", error));
            throw new ConfigurationValidationException("Configuration validation failed", result.getErrors());
        }
        
        if (result.hasWarnings()) {
            log.warn("Configuration validation completed with {} warnings:", result.getWarnings().size());
            result.getWarnings().forEach(warning -> log.warn("  - {}", warning));
        }
        
        log.info("Configuration validation completed successfully");
    }
    
    private void initializeValidationRules() {
        addDatabaseValidationRules();
        addRedisValidationRules();
        addSecurityValidationRules();
        addEncryptionValidationRules();
        addExternalServiceValidationRules();
        addMonitoringValidationRules();
        addPerformanceValidationRules();
        addBusinessValidationRules();
    }
    
    private void addDatabaseValidationRules() {
        // Database connection validation
        validationRules.add(ConfigurationValidationRule.builder()
            .property("spring.datasource.url")
            .required(true)
            .validator(this::validateDatabaseUrl)
            .errorMessage("Database URL is invalid or not accessible")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("spring.datasource.username")
            .required(true)
            .validator(value -> !value.trim().isEmpty())
            .errorMessage("Database username cannot be empty")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("spring.datasource.password")
            .required(true)
            .validator(value -> !value.trim().isEmpty())
            .errorMessage("Database password cannot be empty")
            .build());
            
        // Connection pool validation
        validationRules.add(ConfigurationValidationRule.builder()
            .property("spring.datasource.hikari.maximum-pool-size")
            .required(false)
            .defaultValue("20")
            .validator(value -> isValidInteger(value, 1, 100))
            .errorMessage("Database pool size must be between 1 and 100")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("spring.datasource.hikari.minimum-idle")
            .required(false)
            .defaultValue("5")
            .validator(value -> isValidInteger(value, 1, 50))
            .errorMessage("Database minimum idle connections must be between 1 and 50")
            .build());
    }
    
    private void addRedisValidationRules() {
        validationRules.add(ConfigurationValidationRule.builder()
            .property("spring.redis.host")
            .required(true)
            .validator(this::validateHostname)
            .errorMessage("Redis host is invalid")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("spring.redis.port")
            .required(true)
            .validator(value -> isValidInteger(value, 1, 65535))
            .errorMessage("Redis port must be between 1 and 65535")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("spring.redis.timeout")
            .required(false)
            .defaultValue("2s")
            .validator(this::validateDuration)
            .errorMessage("Redis timeout must be a valid duration")
            .build());
    }
    
    private void addSecurityValidationRules() {
        validationRules.add(ConfigurationValidationRule.builder()
            .property("jwt.secret")
            .required(true)
            .validator(value -> value.length() >= 32)
            .errorMessage("JWT secret must be at least 32 characters long")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("jwt.expiration")
            .required(true)
            .validator(this::validateDuration)
            .errorMessage("JWT expiration must be a valid duration")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("security.cors.allowed-origins")
            .required(true)
            .validator(value -> !value.trim().isEmpty())
            .errorMessage("CORS allowed origins cannot be empty")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("security.rate-limit.requests-per-minute")
            .required(false)
            .defaultValue("100")
            .validator(value -> isValidInteger(value, 1, 10000))
            .errorMessage("Rate limit must be between 1 and 10000 requests per minute")
            .build());
    }
    
    private void addEncryptionValidationRules() {
        validationRules.add(ConfigurationValidationRule.builder()
            .property("encryption.master-key")
            .required(true)
            .validator(value -> value.length() >= 32)
            .errorMessage("Encryption master key must be at least 32 characters long")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("encryption.algorithm")
            .required(true)
            .validator(value -> Arrays.asList("AES-256-GCM", "AES-256-CBC").contains(value))
            .errorMessage("Encryption algorithm must be AES-256-GCM or AES-256-CBC")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("encryption.key-rotation-interval")
            .required(false)
            .defaultValue("P30D")
            .validator(this::validateDuration)
            .errorMessage("Key rotation interval must be a valid duration")
            .build());
    }
    
    private void addExternalServiceValidationRules() {
        // Payment gateway validation
        validationRules.add(ConfigurationValidationRule.builder()
            .property("payment.gateway.base-url")
            .required(true)
            .validator(this::validateUrl)
            .errorMessage("Payment gateway base URL is invalid")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("payment.gateway.api-key")
            .required(true)
            .validator(value -> !value.trim().isEmpty())
            .errorMessage("Payment gateway API key cannot be empty")
            .build());
            
        // KYC service validation
        validationRules.add(ConfigurationValidationRule.builder()
            .property("kyc.service.base-url")
            .required(true)
            .validator(this::validateUrl)
            .errorMessage("KYC service base URL is invalid")
            .build());
            
        // Notification service validation
        validationRules.add(ConfigurationValidationRule.builder()
            .property("notification.email.smtp-host")
            .required(true)
            .validator(this::validateHostname)
            .errorMessage("SMTP host is invalid")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("notification.email.smtp-port")
            .required(true)
            .validator(value -> isValidInteger(value, 1, 65535))
            .errorMessage("SMTP port must be between 1 and 65535")
            .build());
    }
    
    private void addMonitoringValidationRules() {
        validationRules.add(ConfigurationValidationRule.builder()
            .property("management.endpoints.web.exposure.include")
            .required(false)
            .defaultValue("health,info,metrics")
            .validator(value -> !value.trim().isEmpty())
            .warningMessage("No management endpoints exposed - monitoring may be limited")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("management.metrics.export.prometheus.enabled")
            .required(false)
            .defaultValue("true")
            .validator(value -> "true".equals(value) || "false".equals(value))
            .warningMessage("Prometheus metrics export should be enabled for production monitoring")
            .build());
    }
    
    private void addPerformanceValidationRules() {
        validationRules.add(ConfigurationValidationRule.builder()
            .property("server.tomcat.max-threads")
            .required(false)
            .defaultValue("200")
            .validator(value -> isValidInteger(value, 10, 1000))
            .errorMessage("Tomcat max threads must be between 10 and 1000")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("spring.jpa.hibernate.ddl-auto")
            .required(false)
            .defaultValue("validate")
            .validator(value -> Arrays.asList("validate", "update", "create", "create-drop", "none").contains(value))
            .warningMessage("DDL auto should be 'validate' or 'none' in production")
            .build());
    }
    
    private void addBusinessValidationRules() {
        validationRules.add(ConfigurationValidationRule.builder()
            .property("business.transaction.max-amount")
            .required(true)
            .validator(this::validatePositiveDecimal)
            .errorMessage("Maximum transaction amount must be a positive decimal")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("business.transaction.daily-limit")
            .required(true)
            .validator(this::validatePositiveDecimal)
            .errorMessage("Daily transaction limit must be a positive decimal")
            .build());
            
        validationRules.add(ConfigurationValidationRule.builder()
            .property("business.fraud.threshold-score")
            .required(false)
            .defaultValue("75.0")
            .validator(value -> isValidDecimal(value, 0.0, 100.0))
            .errorMessage("Fraud threshold score must be between 0.0 and 100.0")
            .build());
    }
    
    private ConfigurationValidationResult performValidation() {
        ConfigurationValidationResult result = new ConfigurationValidationResult();
        
        for (ConfigurationValidationRule rule : validationRules) {
            String value = environment.getProperty(rule.getProperty());
            
            if (value == null) {
                if (rule.isRequired()) {
                    result.addError(String.format("Required property '%s' is missing: %s", 
                        rule.getProperty(), rule.getErrorMessage()));
                } else if (rule.getDefaultValue() != null) {
                    log.debug("Using default value for property '{}': {}", 
                        rule.getProperty(), rule.getDefaultValue());
                    value = rule.getDefaultValue();
                }
            }
            
            if (value != null && rule.getValidator() != null) {
                if (!rule.getValidator().test(value)) {
                    if (rule.getErrorMessage() != null) {
                        result.addError(String.format("Property '%s' validation failed: %s", 
                            rule.getProperty(), rule.getErrorMessage()));
                    } else if (rule.getWarningMessage() != null) {
                        result.addWarning(String.format("Property '%s' validation warning: %s", 
                            rule.getProperty(), rule.getWarningMessage()));
                    }
                }
            }
        }
        
        return result;
    }
    
    // Validation helper methods
    
    private boolean validateDatabaseUrl(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        return url.startsWith("jdbc:") && url.contains("://");
    }
    
    private boolean validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() != null && (uri.getScheme().equals("http") || uri.getScheme().equals("https"));
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean validateHostname(String hostname) {
        if (hostname == null || hostname.trim().isEmpty()) return false;
        Pattern hostnamePattern = Pattern.compile("^[a-zA-Z0-9.-]+$");
        return hostnamePattern.matcher(hostname).matches();
    }
    
    private boolean validateDuration(String duration) {
        try {
            Duration.parse("PT" + duration.replaceAll("^PT", ""));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean validatePositiveDecimal(String value) {
        try {
            BigDecimal decimal = new BigDecimal(value);
            return decimal.compareTo(BigDecimal.ZERO) > 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isValidInteger(String value, int min, int max) {
        try {
            int intValue = Integer.parseInt(value);
            return intValue >= min && intValue <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private boolean isValidDecimal(String value, double min, double max) {
        try {
            double doubleValue = Double.parseDouble(value);
            return doubleValue >= min && doubleValue <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}