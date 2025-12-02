package com.waqiti.common.security.exception;

/**
 * General exception for Vault secret operations
 */
public class VaultSecretException extends RuntimeException {

    public VaultSecretException(String message) {
        super(message);
    }

    public VaultSecretException(String message, Throwable cause) {
        super(message, cause);
    }
}
