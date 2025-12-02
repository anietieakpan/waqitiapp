package com.waqiti.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map; /**
 * Advanced Rate Limiting Configuration for specific endpoints
 */
@Component
@Slf4j
public class EndpointRateLimitConfig {
    
    // SECURITY FIX: More restrictive limits for critical financial endpoints
    private static final Map<String, Integer> ENDPOINT_LIMITS = Map.of(
            // 5 transfers per minute (reduced from 10)
            // 3 withdrawals per minute (reduced from 5)
            // 5 payments per minute
            // 8 payment requests per minute
            // 30 balance checks per minute
            // 10 transaction queries per minute
            // 2 registrations per minute (reduced from 3)
            // 5 login attempts per minute (reduced from 20)
            // 2 password reset requests per minute
            // 2 password resets per minute (reduced from 3)
            // 3 crypto purchases per minute
            // 3 crypto sales per minute
            // 2 virtual card creations per minute
            // 5 report requests per minute
            // 10 admin operations per minute
    );
    
    public int getRateLimitForEndpoint(String path) {
        // Check exact match first
        Integer exactLimit = ENDPOINT_LIMITS.get(path);
        if (exactLimit != null) {
            return exactLimit;
        }
        
        // Check prefix match
        for (Map.Entry<String, Integer> entry : ENDPOINT_LIMITS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return -1; // No specific limit
    }
}
