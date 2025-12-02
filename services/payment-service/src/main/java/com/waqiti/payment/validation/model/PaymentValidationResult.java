package com.waqiti.payment.validation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Payment Validation Result
 * 
 * Comprehensive validation result for payment operations including:
 * - Validation status and detailed error information
 * - Risk assessment and compliance flags
 * - Performance metrics and timing data
 * - Structured error codes and recommendations
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentValidationResult {
    
    // Basic validation result
    private boolean valid;
    private ValidationStatus status;
    private String errorMessage;
    private String errorCode;
    private List<ValidationError> validationErrors;
    private List<ValidationWarning> validationWarnings;
    
    // Risk assessment
    private RiskLevel riskLevel;
    private Integer riskScore;
    private List<String> riskFactors;
    private boolean requiresManualReview;
    private boolean requiresAdditionalAuth;
    
    // Compliance information
    private boolean complianceApproved;
    private List<String> complianceFlags;
    private String complianceCheckId;
    private boolean requiresReporting;
    
    // Validation metadata
    private String validationId;
    private Instant validatedAt;
    private String validatedBy;
    private Long validationDurationMillis;
    private Map<String, Object> validationContext;
    
    // Recommendations
    private List<String> recommendations;
    private String suggestedAction;
    private Map<String, Object> actionParameters;
    
    // Enums
    public enum ValidationStatus {
        VALID,
        INVALID,
        WARNING,
        REQUIRES_REVIEW,
        REQUIRES_AUTH,
        BLOCKED
    }
    
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    // Helper methods
    public boolean isValid() {
        return valid && status == ValidationStatus.VALID;
    }
    
    public boolean hasErrors() {
        return validationErrors != null && !validationErrors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return validationWarnings != null && !validationWarnings.isEmpty();
    }
    
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }
    
    public String getPrimaryErrorMessage() {
        if (hasErrors()) {
            return validationErrors.get(0).getMessage();
        }
        return errorMessage;
    }
    
    // Static factory methods
    public static PaymentValidationResult valid() {
        return PaymentValidationResult.builder()
            .valid(true)
            .status(ValidationStatus.VALID)
            .riskLevel(RiskLevel.LOW)
            .complianceApproved(true)
            .validatedAt(Instant.now())
            .build();
    }
    
    public static PaymentValidationResult invalid(String errorMessage, String errorCode) {
        return PaymentValidationResult.builder()
            .valid(false)
            .status(ValidationStatus.INVALID)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .validatedAt(Instant.now())
            .build();
    }
    
    public static PaymentValidationResult blocked(String reason) {
        return PaymentValidationResult.builder()
            .valid(false)
            .status(ValidationStatus.BLOCKED)
            .errorMessage(reason)
            .errorCode("BLOCKED")
            .riskLevel(RiskLevel.CRITICAL)
            .validatedAt(Instant.now())
            .build();
    }
    
    public static PaymentValidationResult requiresReview(String reason, RiskLevel riskLevel) {
        return PaymentValidationResult.builder()
            .valid(false)
            .status(ValidationStatus.REQUIRES_REVIEW)
            .errorMessage(reason)
            .riskLevel(riskLevel)
            .requiresManualReview(true)
            .validatedAt(Instant.now())
            .build();
    }
    
    // Supporting classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String field;
        private String code;
        private String message;
        private Object rejectedValue;
        private String suggestion;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationWarning {
        private String field;
        private String code;
        private String message;
        private String recommendation;
    }
}