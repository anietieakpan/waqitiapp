package com.waqiti.ledger.exception;

/**
 * Exception thrown for accounting service related errors
 */
public class AccountingException extends LedgerException {
    
    public AccountingException(String message) {
        super(message);
    }
    
    public AccountingException(String message, Throwable cause) {
        super(message, cause);
    }
}