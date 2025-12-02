package com.waqiti.transaction.exception;

public class TransactionOperationNotAllowedException extends RuntimeException {
    public TransactionOperationNotAllowedException(String message) {
        super(message);
    }
    
    public TransactionOperationNotAllowedException(String message, Throwable cause) {
        super(message, cause);
    }
}