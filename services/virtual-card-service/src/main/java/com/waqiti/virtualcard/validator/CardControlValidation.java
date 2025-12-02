package com.waqiti.virtualcard.validator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of card control validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardControlValidation {
    
    private boolean valid;
    private String reason;
    private ValidationCode code;
    private List<ValidationDetail> details;
    private LocalDateTime validatedAt;
    private String validatedBy;
    
    public enum ValidationCode {
        SUCCESS,
        ONLINE_TRANSACTIONS_BLOCKED,
        INTERNATIONAL_TRANSACTIONS_BLOCKED,
        ATM_WITHDRAWALS_BLOCKED,
        CONTACTLESS_PAYMENTS_BLOCKED,
        RECURRING_PAYMENTS_BLOCKED,
        COUNTRY_NOT_ALLOWED,
        COUNTRY_BLOCKED,
        MERCHANT_CATEGORY_NOT_ALLOWED,
        MERCHANT_CATEGORY_BLOCKED,
        MERCHANT_NOT_ALLOWED,
        MERCHANT_BLOCKED,
        TIME_RESTRICTION_VIOLATION,
        MAX_TRANSACTIONS_EXCEEDED,
        CARD_EXPIRED,
        HIGH_RISK_MERCHANT_BLOCKED,
        GAMBLING_BLOCKED,
        ADULT_CONTENT_BLOCKED,
        CRYPTO_PURCHASE_BLOCKED,
        CASH_ADVANCE_BLOCKED,
        MFA_REQUIRED,
        VELOCITY_CHECK_FAILED,
        FRAUD_CHECK_FAILED,
        UNKNOWN_ERROR
    }
    
    /**
     * Create successful validation
     */
    public static CardControlValidation success() {
        return CardControlValidation.builder()
            .valid(true)
            .code(ValidationCode.SUCCESS)
            .reason("All controls passed")
            .validatedAt(LocalDateTime.now())
            .details(new ArrayList<>())
            .build();
    }
    
    /**
     * Create failed validation
     */
    public static CardControlValidation failure(ValidationCode code, String reason) {
        return CardControlValidation.builder()
            .valid(false)
            .code(code)
            .reason(reason)
            .validatedAt(LocalDateTime.now())
            .details(new ArrayList<>())
            .build();
    }
    
    /**
     * Create failed validation with details
     */
    public static CardControlValidation failureWithDetails(ValidationCode code, String reason, 
                                                          List<ValidationDetail> details) {
        return CardControlValidation.builder()
            .valid(false)
            .code(code)
            .reason(reason)
            .validatedAt(LocalDateTime.now())
            .details(details != null ? details : new ArrayList<>())
            .build();
    }
    
    /**
     * Add validation detail
     */
    public void addDetail(ValidationDetail detail) {
        if (details == null) {
            details = new ArrayList<>();
        }
        details.add(detail);
    }
    
    /**
     * Add validation detail
     */
    public void addDetail(String field, String message, String value) {
        addDetail(new ValidationDetail(field, message, value));
    }
    
    /**
     * Check if MFA is required
     */
    public boolean isMfaRequired() {
        return code == ValidationCode.MFA_REQUIRED;
    }
    
    /**
     * Check if this is a security block
     */
    public boolean isSecurityBlock() {
        return code == ValidationCode.VELOCITY_CHECK_FAILED ||
               code == ValidationCode.FRAUD_CHECK_FAILED ||
               code == ValidationCode.HIGH_RISK_MERCHANT_BLOCKED;
    }
    
    /**
     * Check if this is a category block
     */
    public boolean isCategoryBlock() {
        return code == ValidationCode.GAMBLING_BLOCKED ||
               code == ValidationCode.ADULT_CONTENT_BLOCKED ||
               code == ValidationCode.CRYPTO_PURCHASE_BLOCKED ||
               code == ValidationCode.CASH_ADVANCE_BLOCKED;
    }
    
    /**
     * Get severity level
     */
    public ValidationSeverity getSeverity() {
        if (valid) {
            return ValidationSeverity.NONE;
        }
        
        if (isSecurityBlock()) {
            return ValidationSeverity.HIGH;
        } else if (isCategoryBlock()) {
            return ValidationSeverity.MEDIUM;
        } else if (code == ValidationCode.MAX_TRANSACTIONS_EXCEEDED ||
                   code == ValidationCode.TIME_RESTRICTION_VIOLATION) {
            return ValidationSeverity.LOW;
        } else {
            return ValidationSeverity.MEDIUM;
        }
    }
    
    public enum ValidationSeverity {
        NONE, LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * Validation detail
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ValidationDetail {
        private String field;
        private String message;
        private String value;
        private LocalDateTime timestamp;
        
        public ValidationDetail(String field, String message, String value) {
            this.field = field;
            this.message = message;
            this.value = value;
            this.timestamp = LocalDateTime.now();
        }
    }
}