package com.waqiti.common.security.hsm;

/**
 * Exception thrown by HSM operations
 */
public class HSMException extends Exception {
    
    private final HSMErrorCode errorCode;
    
    public HSMException(String message) {
        super(message);
        this.errorCode = HSMErrorCode.GENERAL_ERROR;
    }
    
    public HSMException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = HSMErrorCode.GENERAL_ERROR;
    }
    
    public HSMException(HSMErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public HSMException(HSMErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public HSMErrorCode getErrorCode() {
        return errorCode;
    }
    
    /**
     * HSM specific error codes
     */
    public enum HSMErrorCode {
        GENERAL_ERROR,
        CONNECTION_FAILED,
        AUTHENTICATION_FAILED,
        KEY_NOT_FOUND,
        KEY_GENERATION_FAILED,
        ENCRYPTION_FAILED,
        DECRYPTION_FAILED,
        SIGNATURE_FAILED,
        VERIFICATION_FAILED,
        HSM_UNAVAILABLE,
        INVALID_KEY_SIZE,
        UNSUPPORTED_ALGORITHM,
        PKCS11_ERROR,
        CONFIGURATION_ERROR,
        PERMISSION_DENIED,
        HSM_TAMPERED
    }
}