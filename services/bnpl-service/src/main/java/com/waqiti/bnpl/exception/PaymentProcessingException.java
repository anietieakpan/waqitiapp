package com.waqiti.bnpl.exception;

/**
 * Exception thrown when payment processing fails
 */
public class PaymentProcessingException extends BnplException {

    public PaymentProcessingException(String message) {
        super(message);
    }

    public PaymentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}