package com.waqiti.notification.exception;

public class PushNotificationException extends RuntimeException {
    
    public PushNotificationException(String message) {
        super(message);
    }
    
    public PushNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public PushNotificationException(Throwable cause) {
        super(cause);
    }
}