package com.waqiti.common.exception;

/**
 * Exception thrown when an external service call fails
 */
public class ExternalServiceException extends BusinessException {
    
    private final String serviceName;
    private final int statusCode;
    private final String endpoint;
    private final Long responseTime;
    
    public ExternalServiceException(String message, String serviceName) {
        super(message);
        this.serviceName = serviceName;
        this.statusCode = -1;
        this.endpoint = null;
        this.responseTime = null;
    }
    
    public ExternalServiceException(String message, String serviceName, int statusCode) {
        super(message);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
        this.endpoint = null;
        this.responseTime = null;
    }
    
    public ExternalServiceException(String message, String serviceName, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.statusCode = -1;
        this.endpoint = null;
        this.responseTime = null;
    }
    
    public ExternalServiceException(String message, String serviceName, int statusCode, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
        this.endpoint = null;
        this.responseTime = null;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    public Long getResponseTime() {
        return responseTime;
    }
    
    public long getRetryAfter() {
        return 30; // Default retry after 30 seconds
    }
}