package com.waqiti.ledger.exception;

/**
 * Exception thrown when there are insufficient funds for a transaction
 */
public class InsufficientFundsException extends LedgerException {
    
    public InsufficientFundsException(String message) {
        super(message);
    }
    
    public InsufficientFundsException(String message, Throwable cause) {
        super(message, cause);
    }
}