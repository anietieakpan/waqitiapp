package com.waqiti.kyc.exception;

public class KYCException extends RuntimeException {
    
    private final String errorCode;
    private final String userId;
    
    public KYCException(String message) {
        super(message);
        this.errorCode = "KYC_ERROR";
        this.userId = null;
    }
    
    public KYCException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "KYC_ERROR";
        this.userId = null;
    }
    
    public KYCException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.userId = null;
    }
    
    public KYCException(String message, String errorCode, String userId) {
        super(message);
        this.errorCode = errorCode;
        this.userId = userId;
    }
    
    public KYCException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.userId = null;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getUserId() {
        return userId;
    }
}