package com.waqiti.audit.exception;

/**
 * Exception thrown when audit service operations fail
 * Extends RuntimeException for unchecked exception handling
 */
public class AuditServiceException extends RuntimeException {
    
    private String errorCode;
    private Object[] parameters;
    
    public AuditServiceException(String message) {
        super(message);
    }
    
    public AuditServiceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public AuditServiceException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public AuditServiceException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public AuditServiceException(String message, String errorCode, Object[] parameters, Throwable cause) {
        super(message);
        this.errorCode = errorCode;
        this.parameters = parameters;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public Object[] getParameters() {
        return parameters;
    }
}