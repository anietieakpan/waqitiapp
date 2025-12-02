package com.waqiti.common.security.exception;

/**
 * Exception thrown when token generation fails
 */
public class TokenGenerationException extends RuntimeException {
    
    public TokenGenerationException(String message) {
        super(message);
    }
    
    public TokenGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}