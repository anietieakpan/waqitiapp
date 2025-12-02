package com.waqiti.transaction.exception;

public class BatchRollbackException extends RuntimeException {
    public BatchRollbackException(String message) {
        super(message);
    }
    
    public BatchRollbackException(String message, Throwable cause) {
        super(message, cause);
    }
}