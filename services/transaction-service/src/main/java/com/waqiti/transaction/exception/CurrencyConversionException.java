package com.waqiti.transaction.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Exception thrown when currency conversion operations fail.
 * This is a business exception that should be handled gracefully by the application.
 * 
 * PRODUCTION-READY: Provides detailed error context for troubleshooting and user feedback
 */
@Getter
public class CurrencyConversionException extends RuntimeException {
    
    private final String errorCode;
    private final Map<String, String> errorContext;
    
    public CurrencyConversionException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.errorContext = Map.of();
    }
    
    public CurrencyConversionException(String message, String errorCode, Map<String, String> errorContext) {
        super(message);
        this.errorCode = errorCode;
        this.errorContext = errorContext;
    }
    
    public CurrencyConversionException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorContext = Map.of();
    }
    
    public CurrencyConversionException(String message, String errorCode, Map<String, String> errorContext, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorContext = errorContext;
    }
    
    /**
     * Get formatted error message for logging and debugging
     */
    public String getFormattedErrorMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorCode).append("] ").append(getMessage());
        
        if (!errorContext.isEmpty()) {
            sb.append(" | Context: ");
            errorContext.forEach((key, value) -> 
                sb.append(key).append("=").append(value).append(", ")
            );
            sb.setLength(sb.length() - 2); // Remove trailing ", "
        }
        
        return sb.toString();
    }
    
    /**
     * Check if this is a critical error that requires immediate attention
     */
    public boolean isCriticalError() {
        return errorCode.startsWith("CRITICAL_") || 
               errorCode.equals("PROVIDER_UNAVAILABLE") ||
               errorCode.equals("RATE_FETCH_FAILED");
    }
    
    /**
     * Check if this error is recoverable through retry
     */
    public boolean isRetryable() {
        return errorCode.equals("RATE_FETCH_TIMEOUT") ||
               errorCode.equals("PROVIDER_TEMPORARY_ERROR") ||
               errorCode.equals("NETWORK_ERROR");
    }
}