package com.waqiti.ledger.exception;

/**
 * General ledger service exception
 */
public class LedgerServiceException extends LedgerException {
    
    public LedgerServiceException(String message) {
        super(message);
    }
    
    public LedgerServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}