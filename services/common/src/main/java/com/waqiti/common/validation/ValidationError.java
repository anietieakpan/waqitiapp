package com.waqiti.common.validation;

import lombok.Builder;
import lombok.Data;

/**
 * Individual validation error with details
 */
@Data
@Builder
public class ValidationError {
    private String field;
    private String code;
    private String message;
    private ValidationSeverity severity;
    private Object rejectedValue;
    
    public static ValidationError error(String field, String code, String message) {
        return ValidationError.builder()
            .field(field)
            .code(code)
            .message(message)
            .severity(ValidationSeverity.ERROR)
            .build();
    }
    
    public static ValidationError warning(String field, String code, String message) {
        return ValidationError.builder()
            .field(field)
            .code(code)
            .message(message)
            .severity(ValidationSeverity.WARNING)
            .build();
    }
    
    public boolean isError() {
        return severity == ValidationSeverity.ERROR;
    }
    
    public boolean isWarning() {
        return severity == ValidationSeverity.WARNING;
    }
}