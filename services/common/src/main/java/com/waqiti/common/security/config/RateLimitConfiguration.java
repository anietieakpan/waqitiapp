package com.waqiti.common.security.config;

import com.waqiti.common.security.model.RateLimitTier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limit configuration
 */
@Component
public class RateLimitConfiguration {
    
    private final Map<String, RateLimitTier> endpointTiers = new ConcurrentHashMap<>();
    
    public RateLimitConfiguration() {
        // Configure endpoint-specific rate limits
        endpointTiers.put("/api/v*/auth/login", RateLimitTier.LOW);
        endpointTiers.put("/api/v*/auth/register", RateLimitTier.RESTRICTED);
        endpointTiers.put("/api/v*/payments/create", RateLimitTier.STANDARD);
        endpointTiers.put("/api/v*/transactions/history", RateLimitTier.HIGH);
        endpointTiers.put("/api/v*/users/profile", RateLimitTier.HIGH);
        endpointTiers.put("/api/v*/wallets/balance", RateLimitTier.HIGH);
    }
    
    public RateLimitTier getTierForEndpoint(String endpoint) {
        return endpointTiers.entrySet().stream()
            .filter(entry -> endpoint.matches(entry.getKey().replace("*", "\\\\d+")))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(RateLimitTier.STANDARD);
    }
}