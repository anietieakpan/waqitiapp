package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Result of template registration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateRegistrationResult {
    
    /**
     * Template ID
     */
    private String templateId;
    
    /**
     * Registration status
     */
    private RegistrationStatus status;
    
    /**
     * Template version
     */
    private String version;
    
    /**
     * Registration timestamp
     */
    private Instant registeredAt;
    
    /**
     * Validation results
     */
    private ValidationResult validation;
    
    /**
     * Template metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Error message if failed
     */
    private String errorMessage;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private Map<String, String> errors;
        private Map<String, String> warnings;
        private int variableCount;
        private int channelCount;
    }
    
    public enum RegistrationStatus {
        SUCCESS,
        FAILED,
        VALIDATION_ERROR,
        DUPLICATE,
        PENDING_APPROVAL
    }
}