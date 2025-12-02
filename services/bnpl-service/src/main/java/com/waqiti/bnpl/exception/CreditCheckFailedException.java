package com.waqiti.bnpl.exception;

/**
 * Exception thrown when credit check fails
 */
public class CreditCheckFailedException extends BnplException {

    public CreditCheckFailedException(String message) {
        super(message);
    }

    public CreditCheckFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}