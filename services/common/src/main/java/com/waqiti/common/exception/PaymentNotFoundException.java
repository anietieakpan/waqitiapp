package com.waqiti.common.exception;

/**
 * Exception thrown when a payment cannot be found
 */
public class PaymentNotFoundException extends BusinessException {
    
    public PaymentNotFoundException(String transactionId) {
        super(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found with transaction ID: " + transactionId);
    }
    
    public PaymentNotFoundException(String transactionId, Throwable cause) {
        super(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found with transaction ID: " + transactionId, cause);
    }
}