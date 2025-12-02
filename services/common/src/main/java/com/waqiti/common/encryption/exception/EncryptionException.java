package com.waqiti.common.encryption.exception;

/**
 * Base exception for encryption operations
 */
public class EncryptionException extends RuntimeException {
    
    public EncryptionException(String message) {
        super(message);
    }
    
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public EncryptionException(Throwable cause) {
        super(cause);
    }
}