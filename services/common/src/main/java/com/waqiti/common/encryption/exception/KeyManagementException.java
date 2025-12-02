package com.waqiti.common.encryption.exception;

/**
 * Exception thrown during key management operations
 */
public class KeyManagementException extends EncryptionException {
    
    public KeyManagementException(String message) {
        super(message);
    }
    
    public KeyManagementException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public KeyManagementException(Throwable cause) {
        super(cause);
    }
}