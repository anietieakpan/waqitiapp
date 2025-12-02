package com.waqiti.common.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API Key validation result model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyValidationResult {
    private boolean valid;
    private String reason;
    private ApiKeyInfo apiKeyInfo;
    
    public static ApiKeyValidationResult valid(ApiKeyInfo keyInfo) {
        return ApiKeyValidationResult.builder()
            .valid(true)
            .apiKeyInfo(keyInfo)
            .build();
    }
    
    public static ApiKeyValidationResult invalid() {
        return ApiKeyValidationResult.builder()
            .valid(false)
            .reason("Invalid API key")
            .build();
    }
    
    public static ApiKeyValidationResult expired() {
        return ApiKeyValidationResult.builder()
            .valid(false)
            .reason("API key expired")
            .build();
    }
    
    public static ApiKeyValidationResult inactive() {
        return ApiKeyValidationResult.builder()
            .valid(false)
            .reason("API key inactive")
            .build();
    }
}