package com.waqiti.common.security.exception;

/**
 * Exception thrown when encryption key rotation fails
 */
public class VaultKeyRotationException extends RuntimeException {

    public VaultKeyRotationException(String message) {
        super(message);
    }

    public VaultKeyRotationException(String message, Throwable cause) {
        super(message, cause);
    }
}
