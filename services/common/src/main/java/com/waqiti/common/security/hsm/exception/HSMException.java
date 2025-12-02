package com.waqiti.common.security.hsm.exception;

/**
 * Exception thrown by HSM operations
 */
public class HSMException extends Exception {
    
    private final String errorCode;
    
    public HSMException(String message) {
        super(message);
        this.errorCode = "HSM_ERROR";
    }
    
    public HSMException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "HSM_ERROR";
    }
    
    public HSMException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public HSMException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}