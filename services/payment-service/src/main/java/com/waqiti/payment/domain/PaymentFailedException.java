package com.waqiti.payment.domain;

/**
 * Thrown when a payment operation fails
 */
public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException(String message) {
        super(message);
    }
    
    public PaymentFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
