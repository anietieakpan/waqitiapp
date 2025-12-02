package com.waqiti.common.async;

/**
 * Exception thrown when max retries are exceeded
 */
public class MaxRetriesExceededException extends AsyncOperationException {
    
    public MaxRetriesExceededException(String message) {
        super(message);
    }
    
    public MaxRetriesExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}