package com.waqiti.dispute.exception;

/**
 * Exception for dispute-related errors
 */
public class DisputeException extends RuntimeException {
    
    private final String errorCode;
    private final boolean retryable;
    
    public DisputeException(String message) {
        super(message);
        this.errorCode = "DISPUTE_ERROR";
        this.retryable = false;
    }
    
    public DisputeException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "DISPUTE_ERROR";
        this.retryable = false;
    }
    
    public DisputeException(String message, String errorCode, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    
    public DisputeException(String message, Throwable cause, String errorCode, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    /**
     * Create validation exception
     */
    public static DisputeException validation(String message) {
        return new DisputeException(message, "VALIDATION_ERROR", false);
    }
    
    /**
     * Create not found exception
     */
    public static DisputeException notFound(String message) {
        return new DisputeException(message, "NOT_FOUND", false);
    }
    
    /**
     * Create duplicate exception
     */
    public static DisputeException duplicate(String message) {
        return new DisputeException(message, "DUPLICATE_DISPUTE", false);
    }
    
    /**
     * Create chargeback exception
     */
    public static DisputeException chargeback(String message) {
        return new DisputeException(message, "CHARGEBACK_ERROR", false);
    }
    
    /**
     * Create SLA breach exception
     */
    public static DisputeException slaBreach(String message) {
        return new DisputeException(message, "SLA_BREACH", false);
    }
}