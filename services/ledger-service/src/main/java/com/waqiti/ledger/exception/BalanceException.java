package com.waqiti.ledger.exception;

public class BalanceException extends RuntimeException {
    public BalanceException(String message) {
        super(message);
    }
    
    public BalanceException(String message, Throwable cause) {
        super(message, cause);
    }
}
