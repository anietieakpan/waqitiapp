/**
 * Crypto Service Exception
 * Base exception for cryptocurrency service operations
 */
package com.waqiti.crypto.exception;

public class CryptoServiceException extends RuntimeException {
    
    private final String errorCode;
    private final Object[] parameters;
    
    public CryptoServiceException(String message) {
        super(message);
        this.errorCode = "CRYPTO_ERROR";
        this.parameters = new Object[0];
    }
    
    public CryptoServiceException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "CRYPTO_ERROR";
        this.parameters = new Object[0];
    }
    
    public CryptoServiceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.parameters = new Object[0];
    }
    
    public CryptoServiceException(String errorCode, String message, Object... parameters) {
        super(message);
        this.errorCode = errorCode;
        this.parameters = parameters;
    }
    
    public CryptoServiceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.parameters = new Object[0];
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public Object[] getParameters() {
        return parameters;
    }
}