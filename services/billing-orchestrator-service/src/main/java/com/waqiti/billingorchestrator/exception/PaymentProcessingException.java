package com.waqiti.billingorchestrator.exception;

/**
 * Exception thrown when payment processing fails
 *
 * @author Waqiti Billing Team
 * @version 1.0
 * @since 2025-10-17
 */
public class PaymentProcessingException extends BillingOrchestratorException {

    public PaymentProcessingException(String message) {
        super(message, "PAYMENT_PROCESSING_FAILED");
    }

    public PaymentProcessingException(String message, Throwable cause) {
        super(message, "PAYMENT_PROCESSING_FAILED", cause);
    }

    public PaymentProcessingException(String message, String errorCode) {
        super(message, errorCode);
    }
}
