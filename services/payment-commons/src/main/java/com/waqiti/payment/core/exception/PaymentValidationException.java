package com.waqiti.payment.core.exception;

/**
 * Exception thrown when payment validation fails
 */
public class PaymentValidationException extends RuntimeException {
    
    public PaymentValidationException(String message) {
        super(message);
    }
    
    public PaymentValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}