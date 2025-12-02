package com.waqiti.common.security.exception;

/**
 * Exception thrown when secret management operations fail
 */
public class SecretManagementException extends RuntimeException {
    
    public SecretManagementException(String message) {
        super(message);
    }
    
    public SecretManagementException(String message, Throwable cause) {
        super(message, cause);
    }
}