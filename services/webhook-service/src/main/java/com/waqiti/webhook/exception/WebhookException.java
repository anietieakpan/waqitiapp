package com.waqiti.webhook.exception;

/**
 * Exception for webhook-related errors
 */
public class WebhookException extends RuntimeException {
    
    private final String errorCode;
    private final boolean retryable;
    
    public WebhookException(String message) {
        super(message);
        this.errorCode = "WEBHOOK_ERROR";
        this.retryable = false;
    }
    
    public WebhookException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "WEBHOOK_ERROR";
        this.retryable = false;
    }
    
    public WebhookException(String message, String errorCode, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    
    public WebhookException(String message, Throwable cause, String errorCode, boolean retryable) {
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
     * Create delivery exception
     */
    public static WebhookException delivery(String message) {
        return new WebhookException(message, "DELIVERY_ERROR", true);
    }
    
    /**
     * Create timeout exception
     */
    public static WebhookException timeout(String message) {
        return new WebhookException(message, "TIMEOUT_ERROR", true);
    }
    
    /**
     * Create authentication exception
     */
    public static WebhookException authentication(String message) {
        return new WebhookException(message, "AUTH_ERROR", false);
    }
    
    /**
     * Create rate limit exception
     */
    public static WebhookException rateLimit(String message) {
        return new WebhookException(message, "RATE_LIMIT", true);
    }
    
    /**
     * Create max retries exception
     */
    public static WebhookException maxRetries(String message) {
        return new WebhookException(message, "MAX_RETRIES", false);
    }
}