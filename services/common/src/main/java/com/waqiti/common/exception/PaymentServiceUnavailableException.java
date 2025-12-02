package com.waqiti.common.exception;

/**
 * Exception thrown when a payment service is temporarily unavailable
 * Used for retry logic decisions
 */
public class PaymentServiceUnavailableException extends RuntimeException {

    public PaymentServiceUnavailableException(String message) {
        super(message);
    }

    public PaymentServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
