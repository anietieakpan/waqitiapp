package com.waqiti.common.validation;

import com.waqiti.common.exception.BusinessException;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Exception thrown when validation fails
 */
@Getter
public class ValidationException extends BusinessException {
    
    private final Map<String, List<String>> validationErrors;
    
    public ValidationException(String message, Map<String, List<String>> validationErrors) {
        super(message);
        this.validationErrors = validationErrors;
    }
    
    public ValidationException(String message, String errorCode, Map<String, List<String>> validationErrors) {
        super(message, errorCode);
        this.validationErrors = validationErrors;
    }
    
    public ValidationException(String message, Map<String, List<String>> validationErrors, Throwable cause) {
        super(message, cause);
        this.validationErrors = validationErrors;
    }
    
    // Explicit getter as fallback for Lombok processing issues
    public Map<String, List<String>> getValidationErrors() {
        return validationErrors;
    }
}