package com.waqiti.ledger.exception;

public class DiscrepancyException extends RuntimeException {
    public DiscrepancyException(String message) {
        super(message);
    }
    
    public DiscrepancyException(String message, Throwable cause) {
        super(message, cause);
    }
}
