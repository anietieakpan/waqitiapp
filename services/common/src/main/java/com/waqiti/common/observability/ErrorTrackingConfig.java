package com.waqiti.common.observability;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Configuration for error tracking and monitoring
 * Manages error collection, filtering, and alerting policies
 */
@Data
@Builder
public class ErrorTrackingConfig {
    
    private boolean enabled;
    private boolean captureStackTraces;
    private boolean captureRequestData;
    private Set<String> ignoredExceptions;
    private Set<String> sensitiveFields;
    private int maxStackTraceDepth;
    private Duration errorRetentionPeriod;
    private boolean enableErrorAggregation;
    private int errorThresholdForAlert;
    private Duration errorWindowDuration;
    private boolean trackBusinessErrors;
    private boolean trackSystemErrors;
    private boolean trackSecurityEvents;
    private boolean includeStackTrace;
    private boolean includeRequestContext;
    private double errorThreshold;
    
    /**
     * Default error tracking configuration
     */
    public static ErrorTrackingConfig defaultConfig() {
        return ErrorTrackingConfig.builder()
            .enabled(true)
            .captureStackTraces(true)
            .captureRequestData(true)
            .ignoredExceptions(Set.of(
                "org.springframework.security.access.AccessDeniedException",
                "org.springframework.web.servlet.NoHandlerFoundException",
                "java.lang.IllegalArgumentException"
            ))
            .sensitiveFields(Set.of(
                "password", "token", "secret", "key", "authorization",
                "credit_card", "ssn", "account_number", "pin"
            ))
            .maxStackTraceDepth(50)
            .errorRetentionPeriod(Duration.ofDays(30))
            .enableErrorAggregation(true)
            .errorThresholdForAlert(10)
            .errorWindowDuration(Duration.ofMinutes(5))
            .build();
    }
    
    /**
     * Check if exception should be ignored
     */
    public boolean shouldIgnoreException(String exceptionClass) {
        return ignoredExceptions != null && ignoredExceptions.contains(exceptionClass);
    }
    
    /**
     * Check if field is sensitive and should be masked
     */
    public boolean isSensitiveField(String fieldName) {
        if (sensitiveFields == null || fieldName == null) {
            return false;
        }
        return sensitiveFields.stream()
            .anyMatch(sensitive -> fieldName.toLowerCase().contains(sensitive.toLowerCase()));
    }
    
    /**
     * Mask sensitive field value
     */
    public String maskSensitiveField(String fieldName, Object value) {
        if (!isSensitiveField(fieldName) || value == null) {
            return String.valueOf(value);
        }
        
        String stringValue = String.valueOf(value);
        if (stringValue.length() <= 4) {
            return "****";
        }
        
        return stringValue.substring(0, 2) + "****" + 
               stringValue.substring(stringValue.length() - 2);
    }
    
    /**
     * Get truncated stack trace
     */
    public List<String> getTruncatedStackTrace(StackTraceElement[] stackTrace) {
        if (!captureStackTraces || stackTrace == null) {
            return List.of();
        }
        
        int limit = Math.min(stackTrace.length, maxStackTraceDepth);
        return java.util.Arrays.stream(stackTrace)
            .limit(limit)
            .map(StackTraceElement::toString)
            .toList();
    }
}