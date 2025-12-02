package com.waqiti.kyc.exception;

/**
 * Exception thrown when batch processing fails
 */
public class BatchProcessingException extends RuntimeException {
    
    public BatchProcessingException(String message) {
        super(message);
    }
    
    public BatchProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}