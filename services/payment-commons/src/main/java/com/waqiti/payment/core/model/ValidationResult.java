package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.ArrayList;

/**
 * Payment validation result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    
    private boolean valid;
    private String errorMessage;
    private String errorCode;
    private List<String> validationErrors;
    
    public static ValidationResult valid() {
        return ValidationResult.builder()
            .valid(true)
            .validationErrors(new ArrayList<>())
            .build();
    }
    
    public static ValidationResult invalid(String errorMessage) {
        return ValidationResult.builder()
            .valid(false)
            .errorMessage(errorMessage)
            .validationErrors(List.of(errorMessage))
            .build();
    }
    
    public static ValidationResult invalid(String errorMessage, String errorCode) {
        return ValidationResult.builder()
            .valid(false)
            .errorMessage(errorMessage)
            .errorCode(errorCode)
            .validationErrors(List.of(errorMessage))
            .build();
    }
    
    public static ValidationResult invalid(List<String> errors) {
        return ValidationResult.builder()
            .valid(false)
            .errorMessage(String.join("; ", errors))
            .validationErrors(errors)
            .build();
    }
}