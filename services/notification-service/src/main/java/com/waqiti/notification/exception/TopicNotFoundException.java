package com.waqiti.notification.exception;

public class TopicNotFoundException extends RuntimeException {
    
    public TopicNotFoundException(String message) {
        super(message);
    }
    
    public TopicNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}