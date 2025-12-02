/**
 * File: ./common/src/main/java/com/waqiti/common/exception/InvalidResourceStateException.java
 */
package com.waqiti.common.exception;

/**
 * Exception thrown when an operation is attempted on a resource that is in an invalid state
 */
public class InvalidResourceStateException extends BusinessException {
    private String resourceType;
    private String currentState;
    private String expectedState;
    
    public InvalidResourceStateException(String message) {
        super(message);
    }

    public InvalidResourceStateException(String resourceName, String currentState, String requiredState) {
        super(String.format("%s is in invalid state: %s. Required state: %s",
                resourceName, currentState, requiredState));
        this.resourceType = resourceName;
        this.currentState = currentState;
        this.expectedState = requiredState;
    }

    public InvalidResourceStateException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public String getResourceType() {
        return resourceType;
    }
    
    public String getCurrentState() {
        return currentState;
    }
    
    public String getExpectedState() {
        return expectedState;
    }
}