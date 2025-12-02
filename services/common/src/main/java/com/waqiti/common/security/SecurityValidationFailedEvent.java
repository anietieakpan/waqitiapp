package com.waqiti.common.security;

import org.springframework.context.ApplicationEvent;
import java.time.LocalDateTime;

/**
 * Security Validation Failed Event
 * Published when critical security validation fails during application startup
 * This allows other components to react to security failures gracefully
 */
public class SecurityValidationFailedEvent extends ApplicationEvent {
    
    private final String failureReason;
    private final LocalDateTime failureTime;
    private final String validationType;
    private final boolean criticalFailure;
    
    /**
     * Create a security validation failed event
     */
    public SecurityValidationFailedEvent(Object source, String failureReason) {
        super(source);
        this.failureReason = failureReason;
        this.failureTime = LocalDateTime.now();
        this.validationType = determineValidationType(failureReason);
        this.criticalFailure = true;
    }
    
    /**
     * Create a security validation failed event with specific validation type
     */
    public SecurityValidationFailedEvent(Object source, String failureReason, String validationType) {
        super(source);
        this.failureReason = failureReason;
        this.failureTime = LocalDateTime.now();
        this.validationType = validationType;
        this.criticalFailure = true;
    }
    
    /**
     * Create a security validation failed event with criticality flag
     */
    public SecurityValidationFailedEvent(Object source, String failureReason, String validationType, boolean criticalFailure) {
        super(source);
        this.failureReason = failureReason;
        this.failureTime = LocalDateTime.now();
        this.validationType = validationType;
        this.criticalFailure = criticalFailure;
    }
    
    /**
     * Determine validation type from failure reason
     */
    private String determineValidationType(String reason) {
        if (reason == null) {
            return "UNKNOWN";
        }
        
        String lowerReason = reason.toLowerCase();
        if (lowerReason.contains("secret") || lowerReason.contains("credential")) {
            return "SECRET_VALIDATION";
        } else if (lowerReason.contains("vault")) {
            return "VAULT_VALIDATION";
        } else if (lowerReason.contains("encryption") || lowerReason.contains("crypto")) {
            return "ENCRYPTION_VALIDATION";
        } else if (lowerReason.contains("certificate") || lowerReason.contains("ssl") || lowerReason.contains("tls")) {
            return "CERTIFICATE_VALIDATION";
        } else if (lowerReason.contains("permission") || lowerReason.contains("access")) {
            return "PERMISSION_VALIDATION";
        }
        
        return "CONFIGURATION_VALIDATION";
    }
    
    // Getters
    public String getFailureReason() {
        return failureReason;
    }
    
    public LocalDateTime getFailureTime() {
        return failureTime;
    }
    
    public String getValidationType() {
        return validationType;
    }
    
    public boolean isCriticalFailure() {
        return criticalFailure;
    }
    
    @Override
    public String toString() {
        return String.format("SecurityValidationFailedEvent[type=%s, critical=%s, time=%s]: %s",
                validationType, criticalFailure, failureTime, failureReason);
    }
}