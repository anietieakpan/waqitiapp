package com.waqiti.common.security;

import lombok.Builder;
import lombok.Data;

/**
 * Rate limiting request context
 */
@Data
@Builder
public class RateLimitRequest {
    private String userId;
    private String clientIp;
    private String endpoint;
    private String userType;
    private String apiKey;
    private String tenantId;
    
    @Builder.Default
    private int requestedTokens = 1;
    
    public static RateLimitRequest forUser(String userId, String clientIp, String endpoint) {
        return RateLimitRequest.builder()
            .userId(userId)
            .clientIp(clientIp)
            .endpoint(endpoint)
            .requestedTokens(1)
            .build();
    }
    
    public static RateLimitRequest forIp(String clientIp, String endpoint) {
        return RateLimitRequest.builder()
            .clientIp(clientIp)
            .endpoint(endpoint)
            .requestedTokens(1)
            .build();
    }
    
    public static RateLimitRequest forApiKey(String apiKey, String clientIp, String endpoint) {
        return RateLimitRequest.builder()
            .apiKey(apiKey)
            .clientIp(clientIp)
            .endpoint(endpoint)
            .requestedTokens(1)
            .build();
    }
}