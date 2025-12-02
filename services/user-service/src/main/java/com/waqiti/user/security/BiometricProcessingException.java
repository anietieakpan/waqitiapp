package com.waqiti.user.security;

/**
 * Exception thrown during biometric processing operations
 */
public class BiometricProcessingException extends RuntimeException {
    
    public BiometricProcessingException(String message) {
        super(message);
    }
    
    public BiometricProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}