package com.waqiti.common.ratelimit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rate limit request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitRequest {
    private String endpoint;
    private String userId;
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private String apiKey;
    private String requestPath;
    private String httpMethod;
    private long timestamp;
}