package com.waqiti.common.security;

/**
 * Exception thrown when CSRF protection operations fail
 */
public class CsrfException extends RuntimeException {
    
    public CsrfException(String message) {
        super(message);
    }
    
    public CsrfException(String message, Throwable cause) {
        super(message, cause);
    }
}