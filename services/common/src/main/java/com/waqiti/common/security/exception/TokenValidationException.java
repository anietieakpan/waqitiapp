package com.waqiti.common.security.exception;

/**
 * Exception thrown when token validation fails
 */
public class TokenValidationException extends RuntimeException {
    
    public TokenValidationException(String message) {
        super(message);
    }
    
    public TokenValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}