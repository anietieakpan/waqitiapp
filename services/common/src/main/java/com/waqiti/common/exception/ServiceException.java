package com.waqiti.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

/**
 * Exception thrown when a service operation fails
 * 
 * This exception is used for inter-service communication failures,
 * external service unavailability, or internal service errors.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ServiceException extends RuntimeException {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private final String serviceId;
    private final String operationName;
    private final Integer errorCode;
    
    /**
     * Constructor with message
     */
    public ServiceException(String message) {
        super(message);
        this.serviceId = null;
        this.operationName = null;
        this.errorCode = null;
    }
    
    /**
     * Constructor with message and cause
     */
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
        this.serviceId = null;
        this.operationName = null;
        this.errorCode = null;
    }
    
    /**
     * Constructor with service details
     */
    public ServiceException(String message, String serviceId, String operationName) {
        super(message);
        this.serviceId = serviceId;
        this.operationName = operationName;
        this.errorCode = null;
    }
    
    /**
     * Constructor with full details
     */
    public ServiceException(String message, String serviceId, String operationName, 
                           Integer errorCode, Throwable cause) {
        super(message, cause);
        this.serviceId = serviceId;
        this.operationName = operationName;
        this.errorCode = errorCode;
    }
    
    /**
     * Get formatted error message
     */
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder(getMessage());
        
        if (serviceId != null) {
            sb.append(" [Service: ").append(serviceId).append("]");
        }
        
        if (operationName != null) {
            sb.append(" [Operation: ").append(operationName).append("]");
        }
        
        if (errorCode != null) {
            sb.append(" [Code: ").append(errorCode).append("]");
        }
        
        return sb.toString();
    }
    
    // Getters
    public String getServiceId() {
        return serviceId;
    }
    
    public String getOperationName() {
        return operationName;
    }
    
    public Integer getErrorCode() {
        return errorCode;
    }
}