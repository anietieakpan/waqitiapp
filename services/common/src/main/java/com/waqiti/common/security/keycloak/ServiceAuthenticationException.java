package com.waqiti.common.security.keycloak;

/**
 * Exception thrown when service-to-service authentication fails
 */
public class ServiceAuthenticationException extends RuntimeException {
    
    private final String service;
    private final String errorCode;
    
    public ServiceAuthenticationException(String message) {
        super(message);
        this.service = null;
        this.errorCode = "SERVICE_AUTH_FAILED";
    }
    
    public ServiceAuthenticationException(String message, Throwable cause) {
        super(message, cause);
        this.service = null;
        this.errorCode = "SERVICE_AUTH_FAILED";
    }
    
    public ServiceAuthenticationException(String message, String service) {
        super(message);
        this.service = service;
        this.errorCode = "SERVICE_AUTH_FAILED";
    }
    
    public ServiceAuthenticationException(String message, String service, Throwable cause) {
        super(message, cause);
        this.service = service;
        this.errorCode = "SERVICE_AUTH_FAILED";
    }
    
    public ServiceAuthenticationException(String message, String service, String errorCode) {
        super(message);
        this.service = service;
        this.errorCode = errorCode;
    }
    
    public String getService() {
        return service;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}