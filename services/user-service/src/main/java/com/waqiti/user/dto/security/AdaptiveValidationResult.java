package com.waqiti.user.dto.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Adaptive Validation Result DTO
 * 
 * Contains comprehensive validation results for adaptive security checks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveValidationResult {
    
    // Overall validation result
    private Boolean passed;
    private Boolean valid; // Alias for passed to support builder pattern
    private String status; // PASS, FAIL, WARNING, REVIEW_REQUIRED
    private Double confidenceScore; // 0.0 to 1.0
    private String decisionReason;
    private String reason; // Alias for decisionReason to support builder pattern
    private List<String> issues; // Validation issues
    private String recommendation; // Security recommendation
    
    // Risk assessment
    private Double riskScore; // 0.0 to 1.0 (1.0 = highest risk)
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private List<String> riskFactors;
    private Map<String, Double> riskComponentScores;
    
    // Validation components
    private ValidationComponent identityValidation;
    private ValidationComponent deviceValidation;
    private ValidationComponent locationValidation;
    private ValidationComponent behaviorValidation;
    private ValidationComponent networkValidation;
    private ValidationComponent timeValidation;
    
    // Adaptive decisions
    private List<String> requiredActions;
    private List<String> recommendedActions;
    private Boolean requiresAdditionalAuth;
    private List<String> suggestedAuthMethods;
    
    // Security constraints
    private List<String> allowedOperations;
    private List<String> deniedOperations;
    private Map<String, Object> operationLimits;
    
    // Monitoring and alerting
    private Boolean triggerAlert;
    private String alertLevel; // INFO, WARNING, CRITICAL
    private List<String> alertReasons;
    private Boolean requiresManualReview;
    
    // Session management
    private Boolean continueSession;
    private Boolean terminateSession;
    private Boolean refreshRequired;
    private Long sessionExtension; // seconds
    
    // Compliance and regulatory
    private Boolean complianceCheck;
    private List<String> complianceViolations;
    private String regulatoryContext;
    
    // Machine learning insights
    private Map<String, Double> mlModelScores;
    private List<String> anomaliesDetected;
    private String patternCategory;
    
    // Historical context
    private Boolean matchesHistoricalPattern;
    private Double historicalDeviation;
    private String baselineComparison;
    
    // Metadata
    private String validationId;
    private LocalDateTime validatedAt;
    private Long processingTimeMs;
    private String validatorVersion;
    private Map<String, Object> debugInfo;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationComponent {
        private String componentName;
        private Boolean passed;
        private Double score;
        private String status;
        private String reason;
        private List<String> warnings;
        private List<String> errors;
        private Map<String, Object> metadata;
        private Long processingTimeMs;
    }
    
    /**
     * Check if validation is valid/passed
     */
    public boolean isValid() {
        return Boolean.TRUE.equals(passed) || Boolean.TRUE.equals(valid);
    }
    
    /**
     * Set valid field and synchronize with passed
     */
    public void setValid(Boolean valid) {
        this.valid = valid;
        this.passed = valid;
    }
    
    /**
     * Set passed field and synchronize with valid
     */
    public void setPassed(Boolean passed) {
        this.passed = passed;
        this.valid = passed;
    }
    
    /**
     * Set reason field and synchronize with decisionReason
     */
    public void setReason(String reason) {
        this.reason = reason;
        this.decisionReason = reason;
    }
    
    /**
     * Set decisionReason field and synchronize with reason
     */
    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
        this.reason = decisionReason;
    }
    
    /**
     * Check if validation requires manual review
     */
    public boolean requiresReview() {
        return Boolean.TRUE.equals(requiresManualReview);
    }
    
    /**
     * Check if validation requires additional authentication
     */
    public boolean requiresAdditionalAuthentication() {
        return Boolean.TRUE.equals(requiresAdditionalAuth);
    }
    
    /**
     * Get the overall validation message
     */
    public String getValidationMessage() {
        if (isValid()) {
            return "Validation passed";
        } else if (requiresReview()) {
            return "Manual review required: " + decisionReason;
        } else {
            return "Validation failed: " + decisionReason;
        }
    }
    
    /**
     * Check if this is a high-risk validation result
     */
    public boolean isHighRisk() {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }

    /**
     * Get the reason for validation result
     */
    public String getReason() {
        return decisionReason;
    }
    
    /**
     * Static factory method for creating a passed validation result
     */
    public static AdaptiveValidationResult passed(String reason) {
        return AdaptiveValidationResult.builder()
            .passed(true)
            .status("PASS")
            .decisionReason(reason)
            .riskLevel("LOW")
            .confidenceScore(0.95)
            .continueSession(true)
            .validatedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Static factory method for creating a failed validation result
     */
    public static AdaptiveValidationResult failed(String reason) {
        return AdaptiveValidationResult.builder()
            .passed(false)
            .status("FAIL")
            .decisionReason(reason)
            .riskLevel("HIGH")
            .confidenceScore(0.1)
            .terminateSession(true)
            .triggerAlert(true)
            .alertLevel("CRITICAL")
            .validatedAt(LocalDateTime.now())
            .build();
    }
}