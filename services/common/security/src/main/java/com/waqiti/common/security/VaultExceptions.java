package com.waqiti.common.security;

/**
 * Enterprise Vault exception hierarchy for proper error handling
 */
public class VaultExceptions {
    
    /**
     * Base exception for all Vault-related errors
     */
    public static class VaultException extends RuntimeException {
        public VaultException(String message) {
            super(message);
        }
        
        public VaultException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Thrown when a secret is not found in Vault
     */
    public static class VaultSecretNotFoundException extends VaultException {
        public VaultSecretNotFoundException(String message) {
            super(message);
        }
    }
    
    /**
     * Thrown when secret retrieval fails
     */
    public static class VaultSecretException extends VaultException {
        public VaultSecretException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Thrown when encryption/decryption operations fail
     */
    public static class VaultEncryptionException extends VaultException {
        public VaultEncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Thrown when key rotation fails
     */
    public static class VaultKeyRotationException extends VaultException {
        public VaultKeyRotationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Thrown when Vault authentication fails
     */
    public static class VaultAuthenticationException extends VaultException {
        public VaultAuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Thrown when Vault connection fails
     */
    public static class VaultConnectionException extends VaultException {
        public VaultConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}