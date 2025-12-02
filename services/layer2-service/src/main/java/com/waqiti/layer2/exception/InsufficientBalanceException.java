package com.waqiti.layer2.exception;

/**
 * Exception thrown when user has insufficient balance for operation
 */
public class InsufficientBalanceException extends Layer2ProcessingException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
