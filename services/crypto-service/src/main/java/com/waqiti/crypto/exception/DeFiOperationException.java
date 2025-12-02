package com.waqiti.crypto.exception;

public class DeFiOperationException extends RuntimeException {
    
    public DeFiOperationException(String message) {
        super(message);
    }
    
    public DeFiOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}