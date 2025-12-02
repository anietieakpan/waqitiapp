package com.waqiti.common.security.exception;

/**
 * Exception thrown when a secret cannot be found in Vault
 */
public class VaultSecretNotFoundException extends RuntimeException {

    private final String secretPath;

    public VaultSecretNotFoundException(String secretPath) {
        super("Secret not found at path: " + secretPath);
        this.secretPath = secretPath;
    }

    public VaultSecretNotFoundException(String secretPath, Throwable cause) {
        super("Secret not found at path: " + secretPath, cause);
        this.secretPath = secretPath;
    }

    public String getSecretPath() {
        return secretPath;
    }
}
