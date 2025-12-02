package com.waqiti.common.validation;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Status model for configuration validation results
 */
@Data
@Builder
public class ConfigurationValidationStatus {
    
    private boolean valid;
    private int errorCount;
    private int warningCount;
    private String summary;
    private List<String> errors;
    private List<String> warnings;
    private long lastValidated;
    
    public boolean hasIssues() {
        return errorCount > 0 || warningCount > 0;
    }
    
    public boolean isCritical() {
        return errorCount > 0;
    }
    
    public String getHealthStatus() {
        if (errorCount > 0) {
            return "CRITICAL";
        } else if (warningCount > 0) {
            return "WARNING";
        } else {
            return "HEALTHY";
        }
    }
}