package com.waqiti.notification.exception;

public class InvalidTopicException extends RuntimeException {
    
    public InvalidTopicException(String message) {
        super(message);
    }
    
    public InvalidTopicException(String message, Throwable cause) {
        super(message, cause);
    }
}