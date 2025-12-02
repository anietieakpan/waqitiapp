package com.waqiti.ledger.exception;

/**
 * Exception thrown for chart of accounts related errors
 */
public class ChartOfAccountsException extends LedgerException {
    
    public ChartOfAccountsException(String message) {
        super(message);
    }
    
    public ChartOfAccountsException(String message, Throwable cause) {
        super(message, cause);
    }
}