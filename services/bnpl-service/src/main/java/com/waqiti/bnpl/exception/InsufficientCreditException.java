package com.waqiti.bnpl.exception;

/**
 * Exception thrown when there is insufficient credit
 */
public class InsufficientCreditException extends BnplException {

    public InsufficientCreditException(String message) {
        super(message);
    }

    public InsufficientCreditException(String message, Throwable cause) {
        super(message, cause);
    }
}