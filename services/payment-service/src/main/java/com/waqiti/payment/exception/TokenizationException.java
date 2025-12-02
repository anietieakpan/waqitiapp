package com.waqiti.payment.exception;

/**
 * CRITICAL: Tokenization Exception for PCI DSS Operations
 * 
 * This exception is thrown when tokenization operations fail.
 * It ensures no sensitive data is exposed in error messages.
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
public class TokenizationException extends RuntimeException {
    
    public TokenizationException(String message) {
        super(message);
    }
    
    public TokenizationException(String message, Throwable cause) {
        super(message, cause);
    }
}