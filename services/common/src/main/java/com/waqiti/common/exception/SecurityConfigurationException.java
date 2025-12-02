package com.waqiti.common.exception;

/**
 * Exception thrown when security configuration is invalid or missing
 */
public class SecurityConfigurationException extends RuntimeException {
    
    public SecurityConfigurationException(String message) {
        super(message);
    }
    
    public SecurityConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}