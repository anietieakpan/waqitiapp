package com.waqiti.notification.exception;

public class DeviceNotFoundException extends RuntimeException {
    
    public DeviceNotFoundException(String message) {
        super(message);
    }
    
    public DeviceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}