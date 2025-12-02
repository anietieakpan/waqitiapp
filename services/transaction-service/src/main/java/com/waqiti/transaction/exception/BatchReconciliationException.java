package com.waqiti.transaction.exception;

public class BatchReconciliationException extends RuntimeException {
    public BatchReconciliationException(String message) {
        super(message);
    }
    
    public BatchReconciliationException(String message, Throwable cause) {
        super(message, cause);
    }
}