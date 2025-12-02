package com.waqiti.common.kyc.exception;

public class KYCServiceUnavailableException extends RuntimeException {
    
    public KYCServiceUnavailableException(String message) {
        super(message);
    }
    
    public KYCServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}