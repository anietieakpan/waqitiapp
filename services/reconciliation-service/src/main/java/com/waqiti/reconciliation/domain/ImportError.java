package com.waqiti.reconciliation.domain;

import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ImportError {
    private final Integer rowNumber;
    private final String fieldName;
    private final String errorType;
    private final String errorMessage;
    private final String expectedValue;
    private final String actualValue;
    private final Map<String, Object> record;
    private final LocalDateTime timestamp;
    private final String severity;
    
    public enum ErrorType {
        VALIDATION_ERROR,
        TYPE_MISMATCH,
        REQUIRED_FIELD_MISSING,
        INVALID_FORMAT,
        CONSTRAINT_VIOLATION,
        TRANSFORMATION_ERROR,
        PARSING_ERROR
    }
    
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    public boolean isCritical() {
        return "CRITICAL".equals(severity);
    }
    
    public boolean isRecoverable() {
        return !"CRITICAL".equals(severity) && 
               !ErrorType.PARSING_ERROR.name().equals(errorType);
    }
    
    public String getFormattedMessage() {
        if (fieldName != null && rowNumber != null) {
            return String.format("Row %d, Field '%s': %s", rowNumber, fieldName, errorMessage);
        } else if (rowNumber != null) {
            return String.format("Row %d: %s", rowNumber, errorMessage);
        } else {
            return errorMessage;
        }
    }
}