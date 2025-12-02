package com.waqiti.common.validation;

import lombok.Builder;
import lombok.Data;

import java.util.function.Predicate;

/**
 * Represents a single configuration validation rule
 */
@Data
@Builder
public class ConfigurationValidationRule {
    
    private String property;
    private boolean required;
    private String defaultValue;
    private Predicate<String> validator;
    private String errorMessage;
    private String warningMessage;
    
    public boolean hasValidator() {
        return validator != null;
    }
    
    public boolean hasDefaultValue() {
        return defaultValue != null && !defaultValue.trim().isEmpty();
    }
    
    public boolean isErrorRule() {
        return errorMessage != null;
    }
    
    public boolean isWarningRule() {
        return warningMessage != null;
    }
}