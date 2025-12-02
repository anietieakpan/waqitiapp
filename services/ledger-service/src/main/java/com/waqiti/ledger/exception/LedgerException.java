package com.waqiti.ledger.exception;

/**
 * Base exception for all ledger-related exceptions
 */
public abstract class LedgerException extends RuntimeException {
    
    public LedgerException(String message) {
        super(message);
    }
    
    public LedgerException(String message, Throwable cause) {
        super(message, cause);
    }
}