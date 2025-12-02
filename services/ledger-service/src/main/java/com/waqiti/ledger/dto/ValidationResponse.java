package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Validation Response DTO
 * 
 * Response structure for validation operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResponse {
    
    private boolean valid;
    private String message;
    private List<ValidationError> errors;
    private Map<String, Object> metadata;
    private LocalDateTime validatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationError {
        private String field;
        private String code;
        private String message;
        private Object rejectedValue;
    }
    
    public static ValidationResponse success() {
        return ValidationResponse.builder()
            .valid(true)
            .message("Validation successful")
            .validatedAt(LocalDateTime.now())
            .build();
    }
    
    public static ValidationResponse success(String message) {
        return ValidationResponse.builder()
            .valid(true)
            .message(message)
            .validatedAt(LocalDateTime.now())
            .build();
    }
    
    public static ValidationResponse failure(String message) {
        return ValidationResponse.builder()
            .valid(false)
            .message(message)
            .validatedAt(LocalDateTime.now())
            .build();
    }
    
    public static ValidationResponse failure(String message, List<ValidationError> errors) {
        return ValidationResponse.builder()
            .valid(false)
            .message(message)
            .errors(errors)
            .validatedAt(LocalDateTime.now())
            .build();
    }
}