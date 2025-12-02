package com.waqiti.payment.exception;

/**
 * CRITICAL: Vault Storage Exception
 * 
 * This exception is thrown when vault storage operations fail.
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
public class VaultStorageException extends RuntimeException {
    
    public VaultStorageException(String message) {
        super(message);
    }
    
    public VaultStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}