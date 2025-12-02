package com.waqiti.analytics.exception;

/**
 * Exception for analytics processing errors
 */
public class AnalyticsProcessingException extends RuntimeException {
    
    public AnalyticsProcessingException(String message) {
        super(message);
    }
    
    public AnalyticsProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}