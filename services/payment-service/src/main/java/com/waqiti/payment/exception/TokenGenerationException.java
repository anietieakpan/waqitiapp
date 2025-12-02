package com.waqiti.payment.exception;

/**
 * CRITICAL: Token Generation Exception
 * 
 * This exception is thrown when secure token generation fails.
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
public class TokenGenerationException extends RuntimeException {
    
    public TokenGenerationException(String message) {
        super(message);
    }
    
    public TokenGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}