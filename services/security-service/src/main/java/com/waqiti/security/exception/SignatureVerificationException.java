package com.waqiti.security.exception;

import com.waqiti.common.exception.BusinessException;

/**
 * Exception thrown when signature verification fails
 */
public class SignatureVerificationException extends BusinessException {
    
    public SignatureVerificationException(String message) {
        super(message);
    }
    
    public SignatureVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public SignatureVerificationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}