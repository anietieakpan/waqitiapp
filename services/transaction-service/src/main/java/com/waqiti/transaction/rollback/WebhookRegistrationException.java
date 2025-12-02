package com.waqiti.transaction.rollback;

/**
 * Exception thrown when webhook registration fails
 */
public class WebhookRegistrationException extends RuntimeException {
    
    public WebhookRegistrationException(String message) {
        super(message);
    }
    
    public WebhookRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}