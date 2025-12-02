package com.waqiti.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for updating webhook subscriptions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionUpdateRequest {
    
    private String name;
    
    private String description;
    
    private List<String> events;
    
    private Map<String, String> customHeaders;
    
    private SubscriptionRequest.RetryConfigRequest retryConfig;
    
    private Boolean active;
    
    private Map<String, Object> metadata;
}