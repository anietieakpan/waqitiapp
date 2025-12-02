package com.waqiti.analytics.exception;

/**
 * Exception thrown for analytics-related errors
 */
public class AnalyticsException extends RuntimeException {
    
    public AnalyticsException(String message) {
        super(message);
    }
    
    public AnalyticsException(String message, Throwable cause) {
        super(message, cause);
    }
}