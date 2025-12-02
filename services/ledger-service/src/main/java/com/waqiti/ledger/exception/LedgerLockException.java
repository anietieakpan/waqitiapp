package com.waqiti.ledger.exception;

/**
 * Exception thrown when unable to acquire ledger locks
 */
public class LedgerLockException extends LedgerException {
    
    public LedgerLockException(String message) {
        super(message);
    }
    
    public LedgerLockException(String message, Throwable cause) {
        super(message, cause);
    }
}