package com.waqiti.payment.core.exception;

/**
 * Exception thrown when an unsupported payment type is requested
 */
public class UnsupportedPaymentTypeException extends RuntimeException {
    
    public UnsupportedPaymentTypeException(String message) {
        super(message);
    }
    
    public UnsupportedPaymentTypeException(String message, Throwable cause) {
        super(message, cause);
    }
}