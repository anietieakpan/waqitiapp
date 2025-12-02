package com.waqiti.common.exception;

/**
 * Exception thrown when token vault operations fail
 */
public class TokenVaultException extends RuntimeException {
    
    public TokenVaultException(String message) {
        super(message);
    }
    
    public TokenVaultException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public TokenVaultException(Throwable cause) {
        super(cause);
    }
}