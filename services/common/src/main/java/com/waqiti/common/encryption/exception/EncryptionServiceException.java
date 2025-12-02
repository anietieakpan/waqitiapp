package com.waqiti.common.encryption.exception;

/**
 * Exception thrown when encryption/decryption operations fail
 */
public class EncryptionServiceException extends Exception {
    
    public EncryptionServiceException(String message) {
        super(message);
    }
    
    public EncryptionServiceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public EncryptionServiceException(Throwable cause) {
        super(cause);
    }
}