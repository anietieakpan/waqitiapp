package com.waqiti.common.exceptions;

/**
 * Exception thrown when account closure operations fail
 */
public class AccountClosureException extends RuntimeException {
    
    public AccountClosureException(String message) {
        super(message);
    }
    
    public AccountClosureException(String message, Throwable cause) {
        super(message, cause);
    }
}