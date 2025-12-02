package com.waqiti.virtualcard.exception;

/**
 * Exception thrown when user has insufficient funds for an operation
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
