package com.waqiti.payment.refund.model;

import com.waqiti.payment.domain.PaymentRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Refund Validation Result
 * 
 * Comprehensive validation result for refund requests including:
 * - Business rule validation
 * - Financial validation
 * - Compliance checks
 * - Risk assessment
 * - Policy enforcement
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundValidationResult {
    
    // Validation status
    private boolean valid;
    private ValidationStatus status;
    private String errorMessage;
    private String errorCode;
    private List<ValidationError> validationErrors;
    private List<ValidationWarning> validationWarnings;
    
    // Original payment information
    private PaymentRequest originalPayment;
    private String originalPaymentId;
    private BigDecimal originalAmount;
    private String originalCurrency;
    private Instant originalPaymentDate;
    private String originalPaymentStatus;
    
    // Refund eligibility
    private boolean eligibleForRefund;
    private boolean withinRefundWindow;
    private boolean hasRemainingBalance;
    private BigDecimal maxRefundableAmount;
    private BigDecimal alreadyRefundedAmount;
    private BigDecimal remainingRefundableAmount;
    
    // Policy validation
    private RefundPolicy applicablePolicy;
    private boolean policyCompliant;
    private List<String> policyViolations;
    private boolean requiresApproval;
    private String approvalReason;
    private ApprovalLevel requiredApprovalLevel;
    
    // Financial validation
    private boolean amountValid;
    private boolean currencyValid;
    private boolean sufficientFunds;
    private BigDecimal refundFee;
    private BigDecimal netRefundAmount;
    private String feeCalculationMethod;
    
    // Risk assessment
    private RiskLevel riskLevel;
    private Integer riskScore;
    private List<String> riskFactors;
    private boolean requiresFraudCheck;
    private boolean requiresManualReview;
    
    // Compliance validation
    private boolean amlCompliant;
    private boolean sanctionsCompliant;
    private boolean pepCheckRequired;
    private String complianceCheckId;
    private List<String> complianceFlags;
    
    // Business rules
    private boolean merchantAllowsRefunds;
    private boolean userAuthorized;
    private boolean paymentMethodValid;
    private boolean chargebackRisk;
    
    // Timing validation
    private Instant refundWindowStart;
    private Instant refundWindowEnd;
    private boolean afterBusinessHours;
    private boolean holidayProcessing;
    
    // Technical validation
    private boolean idempotencyValid;
    private boolean requestFormatValid;
    private boolean systemAvailable;
    private boolean providerSupportsRefund;
    
    // Metadata
    private String validationId;
    private Instant validatedAt;
    private String validatedBy;
    private Long validationDurationMillis;
    private Map<String, Object> additionalData;
    
    // Enums
    public enum ValidationStatus {
        VALID,
        INVALID,
        WARNING,
        MANUAL_REVIEW_REQUIRED,
        APPROVAL_REQUIRED
    }
    
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public enum ApprovalLevel {
        SUPERVISOR,
        MANAGER,
        SENIOR_MANAGER,
        EXECUTIVE
    }
    
    public enum RefundPolicy {
        STANDARD_14_DAYS,
        EXTENDED_30_DAYS,
        PREMIUM_90_DAYS,
        NO_REFUND,
        CUSTOM
    }
    
    // Validation error details
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
    
    // Validation warning details
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
    
    // Helper methods
    public boolean hasErrors() {
        return validationErrors != null && !validationErrors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return validationWarnings != null && !validationWarnings.isEmpty();
    }
    
    public boolean isFullRefund() {
        return originalAmount != null && remainingRefundableAmount != null &&
               remainingRefundableAmount.compareTo(originalAmount) == 0;
    }
    
    public boolean isPartialRefund() {
        return originalAmount != null && remainingRefundableAmount != null &&
               remainingRefundableAmount.compareTo(originalAmount) < 0 &&
               remainingRefundableAmount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    public boolean hasRemainingBalance() {
        return remainingRefundableAmount != null &&
               remainingRefundableAmount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }
    
    public boolean requiresEnhancedValidation() {
        return requiresApproval || requiresManualReview || isHighRisk();
    }
    
    public String getPrimaryErrorMessage() {
        if (hasErrors()) {
            return validationErrors.get(0).getMessage();
        }
        return errorMessage;
    }
    
    public List<String> getAllErrorMessages() {
        return validationErrors != null ? 
               validationErrors.stream().map(ValidationError::getMessage).toList() : 
               List.of();
    }
    
    public BigDecimal calculateNetRefund(BigDecimal requestedAmount) {
        if (refundFee != null && requestedAmount != null) {
            return requestedAmount.subtract(refundFee);
        }
        return requestedAmount;
    }
    
    // Static factory methods
    public static RefundValidationResult valid(PaymentRequest originalPayment) {
        return RefundValidationResult.builder()
            .valid(true)
            .status(ValidationStatus.VALID)
            .originalPayment(originalPayment)
            .originalPaymentId(originalPayment.getId().toString())
            .originalAmount(originalPayment.getAmount())
            .originalCurrency(originalPayment.getCurrency())
            .eligibleForRefund(true)
            .withinRefundWindow(true)
            .policyCompliant(true)
            .amountValid(true)
            .currencyValid(true)
            .validatedAt(Instant.now())
            .build();
    }
    
    public static RefundValidationResult invalid(String errorMessage, String errorCode) {
        return RefundValidationResult.builder()
            .valid(false)
            .status(ValidationStatus.INVALID)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .eligibleForRefund(false)
            .validatedAt(Instant.now())
            .build();
    }
    
    public static RefundValidationResult requiresApproval(PaymentRequest originalPayment, 
                                                         ApprovalLevel level, String reason) {
        return RefundValidationResult.builder()
            .valid(false)
            .status(ValidationStatus.APPROVAL_REQUIRED)
            .originalPayment(originalPayment)
            .requiresApproval(true)
            .requiredApprovalLevel(level)
            .approvalReason(reason)
            .validatedAt(Instant.now())
            .build();
    }
    
    public static RefundValidationResult requiresManualReview(PaymentRequest originalPayment, 
                                                             String reason, RiskLevel riskLevel) {
        return RefundValidationResult.builder()
            .valid(false)
            .status(ValidationStatus.MANUAL_REVIEW_REQUIRED)
            .originalPayment(originalPayment)
            .requiresManualReview(true)
            .riskLevel(riskLevel)
            .errorMessage(reason)
            .validatedAt(Instant.now())
            .build();
    }
    
    public static RefundValidationResult outsideRefundWindow(PaymentRequest originalPayment) {
        return RefundValidationResult.builder()
            .valid(false)
            .status(ValidationStatus.INVALID)
            .originalPayment(originalPayment)
            .errorMessage("Payment is outside the refund window")
            .errorCode("REFUND_WINDOW_EXPIRED")
            .eligibleForRefund(false)
            .withinRefundWindow(false)
            .validatedAt(Instant.now())
            .build();
    }
    
    public static RefundValidationResult insufficientBalance(PaymentRequest originalPayment, 
                                                            BigDecimal requestedAmount,
                                                            BigDecimal availableAmount) {
        return RefundValidationResult.builder()
            .valid(false)
            .status(ValidationStatus.INVALID)
            .originalPayment(originalPayment)
            .errorMessage(String.format("Insufficient refundable balance. Requested: %s, Available: %s", 
                         requestedAmount, availableAmount))
            .errorCode("INSUFFICIENT_REFUNDABLE_BALANCE")
            .eligibleForRefund(false)
            .remainingRefundableAmount(availableAmount)
            .validatedAt(Instant.now())
            .build();
    }
    
    // Builder customization
    public static class RefundValidationResultBuilder {
        
        public RefundValidationResultBuilder withError(String code, String message) {
            this.valid = false;
            this.status = ValidationStatus.INVALID;
            this.errorCode = code;
            this.errorMessage = message;
            return this;
        }
        
        public RefundValidationResultBuilder withRisk(RiskLevel level, List<String> factors) {
            this.riskLevel = level;
            this.riskFactors = factors;
            this.requiresFraudCheck = level == RiskLevel.HIGH || level == RiskLevel.CRITICAL;
            return this;
        }
        
        public RefundValidationResultBuilder withFinancials(BigDecimal originalAmount, 
                                                           BigDecimal alreadyRefunded,
                                                           BigDecimal fee) {
            this.originalAmount = originalAmount;
            this.alreadyRefundedAmount = alreadyRefunded;
            this.refundFee = fee;
            
            if (originalAmount != null && alreadyRefunded != null) {
                this.remainingRefundableAmount = originalAmount.subtract(alreadyRefunded);
                this.hasRemainingBalance = this.remainingRefundableAmount.compareTo(BigDecimal.ZERO) > 0;
            }
            
            return this;
        }
        
        public RefundValidationResultBuilder withPolicy(RefundPolicy policy, boolean compliant) {
            this.applicablePolicy = policy;
            this.policyCompliant = compliant;
            return this;
        }
        
        public RefundValidationResultBuilder withCompliance(boolean aml, boolean sanctions, boolean pep) {
            this.amlCompliant = aml;
            this.sanctionsCompliant = sanctions;
            this.pepCheckRequired = pep;
            return this;
        }
    }
}