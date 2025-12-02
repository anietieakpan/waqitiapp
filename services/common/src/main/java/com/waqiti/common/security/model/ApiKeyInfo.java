package com.waqiti.common.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * API Key information model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyInfo {
    private String apiKey;
    private String clientId;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean active;
    private RateLimitTier rateLimitTier;
    private Set<String> permissions;
    
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}