package com.waqiti.common.validation;

import lombok.Builder;
import lombok.Data;

/**
 * Result of validating a specific configuration property
 */
@Data
@Builder
public class PropertyValidationResult {
    
    private String propertyName;
    private String value;
    private boolean exists;
    private boolean valid;
    private String validationMessage;
    
    public boolean isMissing() {
        return !exists;
    }
    
    public boolean isInvalid() {
        return exists && !valid;
    }
    
    public String getStatus() {
        if (!exists) {
            return "MISSING";
        } else if (!valid) {
            return "INVALID";
        } else {
            return "VALID";
        }
    }
}