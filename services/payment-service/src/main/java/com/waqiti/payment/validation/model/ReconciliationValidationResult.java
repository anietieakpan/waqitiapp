package com.waqiti.payment.validation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Reconciliation Validation Result
 * 
 * Comprehensive validation result for reconciliation operations including:
 * - Settlement amount validation
 * - Period date validation
 * - Provider data validation
 * - Discrepancy detection
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationValidationResult {
    
    // Basic validation result
    private boolean valid;
    private ValidationStatus status;
    private String errorMessage;
    private String errorCode;
    private List<ValidationError> validationErrors;
    
    // Settlement validation
    private String settlementId;
    private BigDecimal expectedGrossAmount;
    private BigDecimal actualGrossAmount;
    private BigDecimal expectedNetAmount;
    private BigDecimal actualNetAmount;
    private BigDecimal varianceAmount;
    private BigDecimal variancePercentage;
    
    // Period validation
    private Instant reconciliationPeriodStart;
    private Instant reconciliationPeriodEnd;
    private boolean validPeriod;
    private String periodErrorMessage;
    
    // Provider validation
    private boolean providerDataValid;
    private List<String> providerDataErrors;
    private Map<String, Object> providerMetadata;
    
    // Discrepancy analysis
    private boolean hasDiscrepancies;
    private List<ReconciliationDiscrepancy> discrepancies;
    private String discrepancySummary;
    private boolean requiresInvestigation;
    
    // Validation metadata
    private String validationId;
    private Instant validatedAt;
    private String validatedBy;
    private Long validationDurationMillis;
    
    // Enums
    public enum ValidationStatus {
        VALID,
        INVALID,
        WARNING,
        DISCREPANCY_FOUND,
        REQUIRES_INVESTIGATION
    }
    
    // Helper methods
    public boolean isValid() {
        return valid && status == ValidationStatus.VALID;
    }
    
    public boolean hasErrors() {
        return validationErrors != null && !validationErrors.isEmpty();
    }
    
    public boolean isWithinTolerance(BigDecimal tolerancePercentage) {
        return variancePercentage != null && 
               variancePercentage.abs().compareTo(tolerancePercentage) <= 0;
    }
    
    // Static factory methods
    public static ReconciliationValidationResult valid() {
        return ReconciliationValidationResult.builder()
            .valid(true)
            .status(ValidationStatus.VALID)
            .validPeriod(true)
            .providerDataValid(true)
            .hasDiscrepancies(false)
            .validatedAt(Instant.now())
            .build();
    }
    
    public static ReconciliationValidationResult invalid(String errorMessage, String errorCode) {
        return ReconciliationValidationResult.builder()
            .valid(false)
            .status(ValidationStatus.INVALID)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .validatedAt(Instant.now())
            .build();
    }
    
    public static ReconciliationValidationResult withDiscrepancy(String summary, 
                                                               List<ReconciliationDiscrepancy> discrepancies) {
        return ReconciliationValidationResult.builder()
            .valid(false)
            .status(ValidationStatus.DISCREPANCY_FOUND)
            .hasDiscrepancies(true)
            .discrepancies(discrepancies)
            .discrepancySummary(summary)
            .requiresInvestigation(true)
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
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconciliationDiscrepancy {
        private String type;
        private String description;
        private BigDecimal expectedValue;
        private BigDecimal actualValue;
        private BigDecimal variance;
        private String severity;
        private String recommendedAction;
    }
}