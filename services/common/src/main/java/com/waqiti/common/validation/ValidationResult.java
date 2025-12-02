package com.waqiti.common.validation;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of input validation containing errors and status
 */
@Data
@Builder
public class ValidationResult {
    private boolean valid;
    private List<ValidationError> errors;
    
    public boolean hasErrors() {
        return !valid || (errors != null && !errors.isEmpty());
    }
    
    public boolean hasErrorsWithSeverity(ValidationSeverity severity) {
        return errors != null && errors.stream()
            .anyMatch(error -> error.getSeverity() == severity);
    }
    
    public List<ValidationError> getErrorsWithSeverity(ValidationSeverity severity) {
        return errors != null ? errors.stream()
            .filter(error -> error.getSeverity() == severity)
            .collect(Collectors.toList()) : List.of();
    }
    
    public String getErrorSummary() {
        if (errors == null || errors.isEmpty()) {
            return "No validation errors";
        }
        
        return errors.stream()
            .map(error -> error.getField() + ": " + error.getMessage())
            .collect(Collectors.joining(", "));
    }
    
    public static ValidationResult success() {
        return ValidationResult.builder()
            .valid(true)
            .errors(List.of())
            .build();
    }
    
    public static ValidationResult failure(List<ValidationError> errors) {
        return ValidationResult.builder()
            .valid(false)
            .errors(errors)
            .build();
    }
}