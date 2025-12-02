package com.waqiti.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/**
 * Request DTO for creating webhook subscriptions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionRequest {
    
    @NotBlank(message = "Tenant ID is required")
    private String tenantId;
    
    @NotBlank(message = "Subscription name is required")
    private String name;
    
    private String description;
    
    @NotBlank(message = "Endpoint URL is required")
    @Pattern(regexp = "^https?://.*", message = "Endpoint URL must be a valid HTTP/HTTPS URL")
    private String endpointUrl;
    
    @NotEmpty(message = "At least one event subscription is required")
    private List<String> events;
    
    private String httpMethod; // POST, PUT, PATCH
    
    private String authType; // NONE, BASIC, BEARER, OAUTH2, HMAC
    
    private String authCredentials; // Encrypted credentials
    
    private Map<String, String> customHeaders;
    
    private RetryConfigRequest retryConfig;
    
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryConfigRequest {
        private int maxAttempts = 5;
        private long initialDelayMs = 1000;
        private double backoffMultiplier = 2.0;
    }
}