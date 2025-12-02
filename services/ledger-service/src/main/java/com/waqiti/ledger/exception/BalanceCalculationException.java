package com.waqiti.ledger.exception;

/**
 * Exception thrown when balance calculation fails
 */
public class BalanceCalculationException extends LedgerException {
    
    public BalanceCalculationException(String message) {
        super(message);
    }
    
    public BalanceCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}