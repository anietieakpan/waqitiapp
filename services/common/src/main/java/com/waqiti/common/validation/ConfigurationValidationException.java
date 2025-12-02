package com.waqiti.common.validation;

import java.util.List;

/**
 * Exception thrown when configuration validation fails
 */
public class ConfigurationValidationException extends RuntimeException {
    
    private final List<String> errors;
    
    public ConfigurationValidationException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    @Override
    public String getMessage() {
        StringBuilder message = new StringBuilder(super.getMessage());
        if (errors != null && !errors.isEmpty()) {
            message.append(": ");
            for (String error : errors) {
                message.append("\n  - ").append(error);
            }
        }
        return message.toString();
    }
}