package com.waqiti.common.exception;

import java.util.UUID;

/**
 * Exception thrown when a resource is not found
 */
public class ResourceNotFoundException extends BusinessException {
    private String resourceType;
    private String resourceId;

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: %s", resourceName, fieldName, fieldValue));
        this.resourceType = resourceName;
        this.resourceId = fieldValue != null ? fieldValue.toString() : null;
    }

    public ResourceNotFoundException(String resourceName, UUID id) {
        super(String.format("%s not found with ID: %s", resourceName, id));
        this.resourceType = resourceName;
        this.resourceId = id != null ? id.toString() : null;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public String getResourceId() {
        return resourceId;
    }
}