package com.waqiti.common.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown when payment processing operations fail.
 * This exception should be used for cases where payments cannot be processed
 * due to system errors, invalid payment data, or business rule violations.
 *
 * DOMAIN-SPECIFIC FIELDS:
 * - paymentId: Unique payment transaction identifier
 * - paymentMethod: Payment method used (e.g., "CARD", "BANK_TRANSFER", "WALLET")
 *
 * USAGE EXAMPLES:
 * - Simple: throw new PaymentProcessingException("Payment gateway timeout")
 * - With payment ID: throw new PaymentProcessingException("Insufficient funds", "PAY-123")
 * - With full context: throw new PaymentProcessingException("Card declined", "PAY-123", "CREDIT_CARD")
 * - With cause: throw new PaymentProcessingException("Network error", cause, "PAY-123", "WALLET")
 */
public class PaymentProcessingException extends BusinessException {

    private final String paymentId;
    private final String paymentMethod;

    /**
     * Create payment processing exception with message only
     */
    public PaymentProcessingException(String message) {
        super(ErrorCode.PAYMENT_PROCESSING_ERROR, message);
        this.paymentId = null;
        this.paymentMethod = null;
    }

    /**
     * Create payment processing exception with message and root cause
     */
    public PaymentProcessingException(String message, Throwable cause) {
        super(ErrorCode.PAYMENT_PROCESSING_ERROR, message, cause);
        this.paymentId = null;
        this.paymentMethod = null;
    }

    /**
     * Create payment processing exception with payment ID
     * Automatically adds paymentId to metadata for tracking
     */
    public PaymentProcessingException(String message, String paymentId) {
        super(ErrorCode.PAYMENT_PROCESSING_ERROR, message, Map.of("paymentId", paymentId));
        this.paymentId = paymentId;
        this.paymentMethod = null;
    }

    /**
     * Create payment processing exception with payment ID and payment method
     * Automatically adds both to metadata
     */
    public PaymentProcessingException(String message, String paymentId, String paymentMethod) {
        super(ErrorCode.PAYMENT_PROCESSING_ERROR, message,
            buildMetadata(paymentId, paymentMethod));
        this.paymentId = paymentId;
        this.paymentMethod = paymentMethod;
    }

    /**
     * Create payment processing exception with cause, payment ID, and payment method
     */
    public PaymentProcessingException(String message, Throwable cause, String paymentId, String paymentMethod) {
        super(ErrorCode.PAYMENT_PROCESSING_ERROR, message, cause,
            buildMetadata(paymentId, paymentMethod));
        this.paymentId = paymentId;
        this.paymentMethod = paymentMethod;
    }

    /**
     * Create payment processing exception with cause and payment ID only
     */
    public PaymentProcessingException(String message, Throwable cause, String paymentId) {
        super(ErrorCode.PAYMENT_PROCESSING_ERROR, message, cause, Map.of("paymentId", paymentId));
        this.paymentId = paymentId;
        this.paymentMethod = null;
    }

    /**
     * Get payment transaction ID
     */
    public String getPaymentId() {
        return paymentId;
    }

    /**
     * Get payment method
     */
    public String getPaymentMethod() {
        return paymentMethod;
    }

    /**
     * Helper method to build metadata map
     */
    private static Map<String, Object> buildMetadata(String paymentId, String paymentMethod) {
        Map<String, Object> metadata = new HashMap<>();
        if (paymentId != null) {
            metadata.put("paymentId", paymentId);
        }
        if (paymentMethod != null) {
            metadata.put("paymentMethod", paymentMethod);
        }
        return metadata;
    }
}