package com.waqiti.nft.security;

/**
 * Exception for key management operations
 */
public class KeyManagementException extends RuntimeException {
    
    public KeyManagementException(String message) {
        super(message);
    }
    
    public KeyManagementException(String message, Throwable cause) {
        super(message, cause);
    }
}