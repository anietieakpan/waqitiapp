package com.waqiti.common.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of notification validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    
    /**
     * Whether validation passed
     */
    private boolean valid;
    
    /**
     * Validation errors
     */
    private List<ValidationError> errors;
    
    /**
     * Validation warnings
     */
    private List<ValidationWarning> warnings;
    
    /**
     * Field-specific validation results
     */
    private Map<String, FieldValidation> fieldValidations;
    
    /**
     * Overall validation score
     */
    private double validationScore;
    
    /**
     * Validation metadata
     */
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String field;
        private String errorCode;
        private String message;
        private ErrorSeverity severity;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationWarning {
        private String field;
        private String warningCode;
        private String message;
        private String suggestion;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldValidation {
        private String fieldName;
        private boolean valid;
        private String value;
        private String expectedFormat;
        private List<String> issues;
    }
    
    public enum ErrorSeverity {
        MINOR,
        MAJOR,
        CRITICAL,
        BLOCKING
    }
}