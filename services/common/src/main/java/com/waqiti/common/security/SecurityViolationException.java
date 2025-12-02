package com.waqiti.common.security;

import com.waqiti.common.exception.BusinessException;

/**
 * Exception thrown when a security violation occurs
 */
public class SecurityViolationException extends BusinessException {
    
    private final String violationType;
    
    public SecurityViolationException(String message) {
        super(message);
        this.violationType = "UNKNOWN";
    }
    
    public SecurityViolationException(String message, Throwable cause) {
        super(message, cause);
        this.violationType = "UNKNOWN";
    }
    
    public SecurityViolationException(String message, String errorCode) {
        super(message, errorCode);
        this.violationType = errorCode;
    }
    
    public SecurityViolationException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
        this.violationType = errorCode;
    }
    
    public SecurityViolationException(String message, String violationType, String errorCode) {
        super(message, errorCode);
        this.violationType = violationType;
    }
    
    public String getViolationType() {
        return violationType;
    }
}