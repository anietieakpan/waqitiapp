package com.waqiti.common.async;

/**
 * Exception thrown when async operation times out
 */
public class AsyncTimeoutException extends AsyncOperationException {
    
    public AsyncTimeoutException(String message) {
        super(message);
    }
    
    public AsyncTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}