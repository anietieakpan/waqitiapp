package com.waqiti.common.batch;

/**
 * Exception for batch processing failures
 */
public class BatchProcessingException extends RuntimeException {
    
    public BatchProcessingException(String message) {
        super(message);
    }
    
    public BatchProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}