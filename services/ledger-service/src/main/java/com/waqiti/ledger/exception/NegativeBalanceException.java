package com.waqiti.ledger.exception;

/**
 * Exception thrown when a negative balance is detected for asset accounts
 */
public class NegativeBalanceException extends LedgerException {
    
    public NegativeBalanceException(String message) {
        super(message);
    }
    
    public NegativeBalanceException(String message, Throwable cause) {
        super(message, cause);
    }
}