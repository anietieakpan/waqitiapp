package com.waqiti.common.exception;

import lombok.Getter;

/**
 * Exception thrown when security violations are detected
 */
@Getter
public class SecurityViolationException extends RuntimeException {
    
    private final String violationType;
    private final String clientIp;
    private final String userAgent;
    
    public SecurityViolationException(String message, String violationType) {
        super(message);
        this.violationType = violationType;
        this.clientIp = null;
        this.userAgent = null;
    }
    
    public SecurityViolationException(String message, String violationType, String clientIp, String userAgent) {
        super(message);
        this.violationType = violationType;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
    }
    
    public SecurityViolationException(String message, Throwable cause, String violationType) {
        super(message, cause);
        this.violationType = violationType;
        this.clientIp = null;
        this.userAgent = null;
    }
}