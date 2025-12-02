package com.waqiti.common.security.exception;

/**
 * Exception thrown when Vault encryption/decryption operations fail
 */
public class VaultEncryptionException extends RuntimeException {

    public VaultEncryptionException(String message) {
        super(message);
    }

    public VaultEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
