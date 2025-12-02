package com.waqiti.payment.core.exception;

/**
 * Exception thrown when an unsupported payment provider is requested
 */
public class UnsupportedProviderException extends RuntimeException {
    
    public UnsupportedProviderException(String message) {
        super(message);
    }
    
    public UnsupportedProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}