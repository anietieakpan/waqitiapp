package com.waqiti.transaction.exception;

public class BatchNotFoundException extends RuntimeException {
    public BatchNotFoundException(String message) {
        super(message);
    }
    
    public BatchNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}