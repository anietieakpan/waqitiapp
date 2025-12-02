package com.waqiti.common.security;

import lombok.Builder;
import lombok.Data;

/**
 * Result of CSRF token validation
 */
@Data
@Builder
public class CsrfValidationResult {
    private boolean valid;
    private String errorMessage;
    private String sessionId;
    
    public static CsrfValidationResult success() {
        return CsrfValidationResult.builder()
            .valid(true)
            .build();
    }
    
    public static CsrfValidationResult failure(String errorMessage) {
        return CsrfValidationResult.builder()
            .valid(false)
            .errorMessage(errorMessage)
            .build();
    }
    
    public boolean isFailure() {
        return !valid;
    }
}