package com.waqiti.common.security.service;

import com.waqiti.common.security.model.ApiKeyInfo;
import com.waqiti.common.security.model.ApiKeyValidationResult;
import com.waqiti.common.security.model.RateLimitTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API key service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiKeyService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, ApiKeyInfo> apiKeys = new ConcurrentHashMap<>();
    
    public ApiKeyValidationResult validateApiKey(String apiKey) {
        // Check cache first
        ApiKeyInfo keyInfo = apiKeys.get(apiKey);
        
        if (keyInfo == null) {
            // Check Redis
            keyInfo = (ApiKeyInfo) redisTemplate.opsForValue()
                .get("api-key:" + apiKey);
            
            if (keyInfo != null) {
                apiKeys.put(apiKey, keyInfo);
            }
        }
        
        if (keyInfo == null) {
            return ApiKeyValidationResult.invalid();
        }
        
        if (keyInfo.isExpired()) {
            apiKeys.remove(apiKey);
            redisTemplate.delete("api-key:" + apiKey);
            return ApiKeyValidationResult.expired();
        }
        
        if (!keyInfo.isActive()) {
            return ApiKeyValidationResult.inactive();
        }
        
        return ApiKeyValidationResult.valid(keyInfo);
    }
    
    public void recordApiKeyUsage(String apiKey, String endpoint) {
        String usageKey = "api-key-usage:" + apiKey + ":" + 
            java.time.LocalDate.now().toString();
        
        redisTemplate.opsForHash().increment(usageKey, endpoint, 1);
        redisTemplate.expire(usageKey, Duration.ofDays(30));
    }
    
    public String generateApiKey(String clientId, LocalDateTime expiresAt) {
        String apiKey = "ak_" + UUID.randomUUID().toString().replace("-", "");
        
        ApiKeyInfo keyInfo = ApiKeyInfo.builder()
            .apiKey(apiKey)
            .clientId(clientId)
            .createdAt(LocalDateTime.now())
            .expiresAt(expiresAt)
            .active(true)
            .rateLimitTier(RateLimitTier.STANDARD)
            .build();
        
        redisTemplate.opsForValue().set("api-key:" + apiKey, keyInfo);
        apiKeys.put(apiKey, keyInfo);
        
        return apiKey;
    }
    
    /**
     * CRITICAL SECURITY: Get user/client ID associated with an API key
     * 
     * This method is used for rate limiting, audit trails, and access control.
     * It implements a two-tier caching strategy for optimal performance while
     * maintaining security and data consistency.
     * 
     * @param apiKey The API key to lookup (required, will be sanitized)
     * @return The client/user ID associated with the key, or null if key is invalid/not found
     * @throws IllegalArgumentException if apiKey is null or empty
     * @throws RuntimeException if Redis connection fails and fallback is unavailable
     */
    public String getUserIdForApiKey(String apiKey) {
        // SECURITY: Input validation - prevent injection attacks
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("SECURITY: Attempted API key lookup with null or empty key");
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        
        // SECURITY: Sanitize input - only allow valid API key format
        if (!apiKey.matches("^ak_[a-f0-9]{32}$")) {
            log.warn("SECURITY: Invalid API key format attempted: {}", 
                apiKey.substring(0, Math.min(10, apiKey.length())) + "...");
            return null;
        }
        
        long startTime = System.currentTimeMillis();
        String clientId = null;
        boolean cacheHit = false;
        
        try {
            // PERFORMANCE: Check in-memory cache first (sub-millisecond lookup)
            ApiKeyInfo keyInfo = apiKeys.get(apiKey);
            
            if (keyInfo != null) {
                cacheHit = true;
                clientId = keyInfo.getClientId();
                
                // SECURITY: Verify key hasn't expired in cache
                if (keyInfo.isExpired()) {
                    log.info("SECURITY: Expired API key found in cache, removing: {}", 
                        maskApiKey(apiKey));
                    apiKeys.remove(apiKey);
                    redisTemplate.delete("api-key:" + apiKey);
                    return null;
                }
                
                // SECURITY: Verify key is still active
                if (!keyInfo.isActive()) {
                    log.warn("SECURITY: Inactive API key attempted access: {}", 
                        maskApiKey(apiKey));
                    return null;
                }
            } else {
                // PERFORMANCE: Check Redis distributed cache
                try {
                    keyInfo = (ApiKeyInfo) redisTemplate.opsForValue()
                        .get("api-key:" + apiKey);
                    
                    if (keyInfo != null) {
                        // SECURITY: Validate before caching
                        if (keyInfo.isExpired() || !keyInfo.isActive()) {
                            log.info("SECURITY: Invalid/expired key found in Redis, cleaning up: {}", 
                                maskApiKey(apiKey));
                            redisTemplate.delete("api-key:" + apiKey);
                            return null;
                        }
                        
                        // PERFORMANCE: Populate in-memory cache for future requests
                        apiKeys.put(apiKey, keyInfo);
                        clientId = keyInfo.getClientId();
                        
                        log.debug("API key loaded from Redis and cached: {}", maskApiKey(apiKey));
                    } else {
                        log.debug("API key not found in any cache: {}", maskApiKey(apiKey));
                    }
                } catch (Exception redisEx) {
                    // RESILIENCE: Redis failure shouldn't break the system
                    log.error("CRITICAL: Redis lookup failed for API key, system degraded", redisEx);
                    // Continue with null result - fail secure
                }
            }
            
            // MONITORING: Record metrics
            long duration = System.currentTimeMillis() - startTime;
            if (duration > 100) {
                log.warn("PERFORMANCE: Slow API key lookup detected: {}ms for key {}", 
                    duration, maskApiKey(apiKey));
            }
            
            // AUDIT: Log access for security monitoring (successful lookups only)
            if (clientId != null) {
                log.debug("API key lookup successful - ClientID: {}, CacheHit: {}, Duration: {}ms",
                    clientId, cacheHit, duration);
            }
            
            return clientId;
            
        } catch (IllegalArgumentException e) {
            // Re-throw validation exceptions
            throw e;
        } catch (Exception e) {
            // RESILIENCE: Catch-all for unexpected errors
            log.error("CRITICAL: Unexpected error during API key lookup for key: {}", 
                maskApiKey(apiKey), e);
            
            // SECURITY: Fail secure - return null on error
            return null;
        }
    }
    
    /**
     * SECURITY: Mask API key for safe logging
     * Shows first 7 chars + last 4 chars for debugging while protecting the key
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 12) {
            return "***";
        }
        return apiKey.substring(0, 7) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}