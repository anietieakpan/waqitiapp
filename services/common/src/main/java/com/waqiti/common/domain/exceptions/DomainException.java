package com.waqiti.common.domain.exceptions;

/**
 * Base Domain Exception
 * All domain-specific exceptions should extend this class
 */
public abstract class DomainException extends RuntimeException {
    
    private final String errorCode;
    private final String domain;
    
    protected DomainException(String message, String errorCode, String domain) {
        super(message);
        this.errorCode = errorCode;
        this.domain = domain;
    }
    
    protected DomainException(String message, String errorCode, String domain, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.domain = domain;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getDomain() {
        return domain;
    }
    
    public String getFullErrorCode() {
        return domain + "." + errorCode;
    }
}