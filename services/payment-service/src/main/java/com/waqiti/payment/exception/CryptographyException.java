package com.waqiti.payment.exception;

/**
 * Exception thrown when cryptographic operations fail
 */
public class CryptographyException extends RuntimeException {
    
    private final String errorCode;
    
    public CryptographyException(String message) {
        super(message);
        this.errorCode = "CRYPTO_ERROR";
    }
    
    public CryptographyException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "CRYPTO_ERROR";
    }
    
    public CryptographyException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public CryptographyException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}