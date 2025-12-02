package com.waqiti.common.exception;

/**
 * Exception thrown when a resource already exists
 */
public class DuplicateResourceException extends BusinessException {
    private String resourceType;
    private String conflictingField;
    
    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: %s", resourceName, fieldName, fieldValue));
        this.resourceType = resourceName;
        this.conflictingField = fieldName;
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public String getConflictingField() {
        return conflictingField;
    }
    
    public String getField() {
        return conflictingField;
    }
}