package com.waqiti.common.validation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of configuration validation containing errors and warnings
 */
@Data
public class ConfigurationValidationResult {
    
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    
    public void addError(String error) {
        errors.add(error);
    }
    
    public void addWarning(String warning) {
        warnings.add(warning);
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    public int getErrorCount() {
        return errors.size();
    }
    
    public int getWarningCount() {
        return warnings.size();
    }
    
    public boolean isValid() {
        return errors.isEmpty();
    }
    
    public String getSummary() {
        if (isValid() && !hasWarnings()) {
            return "Configuration is valid";
        } else if (isValid() && hasWarnings()) {
            return String.format("Configuration is valid with %d warnings", getWarningCount());
        } else {
            return String.format("Configuration is invalid with %d errors and %d warnings", 
                getErrorCount(), getWarningCount());
        }
    }
}