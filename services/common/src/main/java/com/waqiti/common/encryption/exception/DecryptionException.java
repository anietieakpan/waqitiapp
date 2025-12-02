package com.waqiti.common.encryption.exception;

/**
 * Exception thrown during decryption operations
 */
public class DecryptionException extends EncryptionException {
    
    public DecryptionException(String message) {
        super(message);
    }
    
    public DecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public DecryptionException(Throwable cause) {
        super(cause);
    }
}