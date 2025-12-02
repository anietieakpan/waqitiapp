package com.waqiti.notification.exception;

/**
 * Exception thrown when SMS delivery fails
 */
public class SmsDeliveryException extends RuntimeException {
    
    public SmsDeliveryException(String message) {
        super(message);
    }
    
    public SmsDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}