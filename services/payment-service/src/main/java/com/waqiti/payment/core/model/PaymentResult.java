package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ENHANCED Unified payment result model that standardizes payment responses
 * across different payment types and providers with comprehensive status tracking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResult {
    private String paymentId;
    private String requestId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String transactionId;
    private String provider;
    private ProviderType providerType;
    private LocalDateTime processedAt;
    private String errorMessage;
    private String errorCode;
    private Map<String, Object> metadata;
    private BigDecimal fee;
    private BigDecimal netAmount;
    private String confirmationNumber;
    
    // Enhanced fields
    private String originalPaymentId; // For refunds
    private LocalDateTime timestamp;
    private String message;
    private List<String> riskFactors;
    private String blockReason;
    private BigDecimal refundedAmount;
    private int retryCount;
    private String idempotencyKey;
    
    // Audit fields
    private String processedBy;
    private String approvedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();
    
    public enum PaymentStatus {
        // Initial states
        PENDING,
        PROCESSING,
        AWAITING_APPROVAL,
        
        // Success states
        COMPLETED,
        SUCCESS,
        SETTLED,
        
        // Failure states
        FAILED,
        ERROR,
        DECLINED,
        INSUFFICIENT_FUNDS,
        
        // Cancellation states
        CANCELLED,
        REVERSED,
        
        // Refund states
        REFUNDED,
        PARTIALLY_REFUNDED,
        REFUND_PENDING,
        
        // Hold states
        HELD,
        BLOCKED,
        FRAUD_BLOCKED,
        COMPLIANCE_BLOCKED,
        
        // Other states
        EXPIRED,
        NOT_FOUND
    }
    
    // Helper factory methods
    public static PaymentResult success(String paymentId, String transactionId, BigDecimal amount) {
        return PaymentResult.builder()
            .paymentId(paymentId)
            .transactionId(transactionId)
            .status(PaymentStatus.COMPLETED)
            .amount(amount)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static PaymentResult failure(String paymentId, String errorMessage, String errorCode) {
        return PaymentResult.builder()
            .paymentId(paymentId)
            .status(PaymentStatus.FAILED)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static PaymentResult blocked(String paymentId, String blockReason) {
        return PaymentResult.builder()
            .paymentId(paymentId)
            .status(PaymentStatus.BLOCKED)
            .blockReason(blockReason)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static PaymentResult fraudBlocked(List<String> riskFactors) {
        return PaymentResult.builder()
            .status(PaymentStatus.FRAUD_BLOCKED)
            .riskFactors(riskFactors)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static PaymentResult error(String message) {
        return PaymentResult.builder()
            .status(PaymentStatus.ERROR)
            .message(message)
            .errorMessage(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * PRODUCTION HARDENING (November 17, 2025):
     * Added validation, logging, and deprecation for Double compatibility methods.
     * These methods exist ONLY for backward compatibility with legacy integrations.
     *
     * WARNING: Converting between Double and BigDecimal can cause precision loss.
     * Always use BigDecimal for new code.
     */

    /**
     * Get amount as Double for legacy API compatibility.
     *
     * @return amount as Double (may lose precision)
     * @deprecated Use getAmount() which returns BigDecimal for exact precision
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    public Double getAmountAsDouble() {
        if (amount != null) {
            // Log warning if amount has fractional cents (precision loss risk)
            if (amount.scale() > 2) {
                log.warn("PRECISION LOSS RISK: Converting BigDecimal with scale {} to Double. " +
                        "PaymentId: {}, Amount: {}", amount.scale(), paymentId, amount);
            }
            return amount.doubleValue();
        }
        return null;
    }

    /**
     * Set amount from Double for legacy API compatibility.
     * Converts Double to BigDecimal with validation.
     *
     * @param amount amount as Double (precision may be lost)
     * @deprecated Use setAmount(BigDecimal) for exact precision
     * @throws IllegalArgumentException if amount is NaN or Infinite
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    public void setAmount(Double amount) {
        if (amount != null) {
            // Validate Double value
            if (Double.isNaN(amount)) {
                throw new IllegalArgumentException(
                    "Invalid payment amount: NaN is not allowed. PaymentId: " + paymentId);
            }
            if (Double.isInfinite(amount)) {
                throw new IllegalArgumentException(
                    "Invalid payment amount: Infinite is not allowed. PaymentId: " + paymentId);
            }
            if (amount < 0) {
                throw new IllegalArgumentException(
                    "Invalid payment amount: Negative values not allowed. PaymentId: " + paymentId +
                    ", Amount: " + amount);
            }

            // Log warning for monitoring
            log.warn("DEPRECATED: Setting payment amount from Double. " +
                    "Use BigDecimal for exact precision. PaymentId: {}, Amount: {}",
                    paymentId, amount);

            // Convert to BigDecimal
            this.amount = BigDecimal.valueOf(amount);
        } else {
            this.amount = null;
        }
    }

    // Add logger for deprecation warnings
    private static final org.slf4j.Logger log =
        org.slf4j.LoggerFactory.getLogger(PaymentResult.class);
}