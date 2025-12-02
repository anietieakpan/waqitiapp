package com.waqiti.common.encryption;

/**
 * Encryption-specific exception
 */
public class EncryptionException extends RuntimeException {
    
    private final String errorCode;
    private final boolean isSecurityCritical;
    
    public EncryptionException(String message) {
        super(message);
        this.errorCode = "ENCRYPTION_ERROR";
        this.isSecurityCritical = false;
    }
    
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "ENCRYPTION_ERROR";
        this.isSecurityCritical = false;
    }
    
    public EncryptionException(String message, String errorCode, boolean isSecurityCritical) {
        super(message);
        this.errorCode = errorCode;
        this.isSecurityCritical = isSecurityCritical;
    }
    
    public EncryptionException(String message, Throwable cause, String errorCode, boolean isSecurityCritical) {
        super(message, cause);
        this.errorCode = errorCode;
        this.isSecurityCritical = isSecurityCritical;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public boolean isSecurityCritical() {
        return isSecurityCritical;
    }
    
    /**
     * Create security-critical encryption exception
     */
    public static EncryptionException securityCritical(String message) {
        return new EncryptionException(message, "SECURITY_CRITICAL", true);
    }
    
    /**
     * Create key management exception
     */
    public static EncryptionException keyManagement(String message, Throwable cause) {
        return new EncryptionException(message, cause, "KEY_MANAGEMENT_ERROR", true);
    }
    
    /**
     * Create validation exception
     */
    public static EncryptionException validation(String message) {
        return new EncryptionException(message, "VALIDATION_ERROR", false);
    }
}