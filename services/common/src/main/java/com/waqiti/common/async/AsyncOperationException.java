package com.waqiti.common.async;

/**
 * Exception for async operation failures
 */
public class AsyncOperationException extends RuntimeException {
    
    public AsyncOperationException(String message) {
        super(message);
    }
    
    public AsyncOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}