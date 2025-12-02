package com.waqiti.ledger.exception;

/**
 * Exception thrown when an account is not found
 */
public class AccountNotFoundException extends LedgerException {
    
    public AccountNotFoundException(String message) {
        super(message);
    }
    
    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}