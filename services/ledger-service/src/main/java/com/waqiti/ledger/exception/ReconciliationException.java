package com.waqiti.ledger.exception;

/**
 * Exception thrown for reconciliation service related errors
 */
public class ReconciliationException extends LedgerException {
    
    public ReconciliationException(String message) {
        super(message);
    }
    
    public ReconciliationException(String message, Throwable cause) {
        super(message, cause);
    }
}