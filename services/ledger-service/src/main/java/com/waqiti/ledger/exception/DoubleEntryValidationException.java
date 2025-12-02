package com.waqiti.ledger.exception;

/**
 * Exception thrown when double-entry validation fails
 */
public class DoubleEntryValidationException extends LedgerException {
    
    public DoubleEntryValidationException(String message) {
        super(message);
    }
    
    public DoubleEntryValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}