package com.waqiti.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/security/csrf")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "security.csrf.enabled", havingValue = "true", matchIfMissing = true)
public class CSRFAdminController {

    private final CSRFProtectionService csrfProtectionService;
    private final CSRFSecurityConfigValidator configValidator;
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String CSRF_TOKEN_PREFIX = "csrf:token:";

    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    public ResponseEntity<Map<String, Object>> getCSRFStatus() {
        log.info("SECURITY: Admin requested CSRF protection status");
        
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", true);
        status.put("configuration", configValidator.getConfigurationSummary());
        status.put("properlyConfigured", configValidator.isProperlyConfigured());
        status.put("timestamp", System.currentTimeMillis());
        
        try {
            Set<String> keys = redisTemplate.keys(CSRF_TOKEN_PREFIX + "*");
            status.put("activeTokenCount", keys != null ? keys.size() : 0);
        } catch (Exception e) {
            log.error("SECURITY: Error retrieving active token count", e);
            status.put("activeTokenCount", -1);
            status.put("error", "Could not retrieve active token count");
        }
        
        return ResponseEntity.ok(status);
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    public ResponseEntity<Map<String, Object>> getCSRFMetrics() {
        log.info("SECURITY: Admin requested CSRF protection metrics");
        
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            Set<String> tokenKeys = redisTemplate.keys(CSRF_TOKEN_PREFIX + "*");
            
            if (tokenKeys != null && !tokenKeys.isEmpty()) {
                List<Map<String, Object>> tokens = tokenKeys.stream()
                    .limit(100)
                    .map(key -> {
                        Map<String, Object> tokenInfo = new HashMap<>();
                        tokenInfo.put("key", key.replace(CSRF_TOKEN_PREFIX, ""));
                        
                        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                        tokenInfo.put("ttlSeconds", ttl != null ? ttl : -1);
                        
                        return tokenInfo;
                    })
                    .collect(Collectors.toList());
                
                metrics.put("totalTokens", tokenKeys.size());
                metrics.put("sampleTokens", tokens);
                
                long expiringSoon = tokens.stream()
                    .filter(t -> {
                        Object ttl = t.get("ttlSeconds");
                        return ttl instanceof Long && (Long)ttl < 300;
                    })
                    .count();
                
                metrics.put("tokensExpiringSoon", expiringSoon);
            } else {
                metrics.put("totalTokens", 0);
                metrics.put("sampleTokens", Collections.emptyList());
                metrics.put("tokensExpiringSoon", 0);
            }
            
            metrics.put("timestamp", System.currentTimeMillis());
            
        } catch (Exception e) {
            log.error("SECURITY: Error retrieving CSRF metrics", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to retrieve metrics", 
                           "message", e.getMessage()));
        }
        
        return ResponseEntity.ok(metrics);
    }

    @PostMapping("/tokens/invalidate-session/{sessionId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    public ResponseEntity<Map<String, Object>> invalidateSessionToken(@PathVariable String sessionId) {
        log.warn("SECURITY: Admin requested token invalidation for session: {}", sessionId);
        
        try {
            csrfProtectionService.invalidateToken(sessionId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "CSRF token invalidated for session: " + sessionId,
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("SECURITY: Error invalidating CSRF token for session: {}", sessionId, e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, 
                           "error", "Failed to invalidate token",
                           "message", e.getMessage()));
        }
    }

    @PostMapping("/tokens/cleanup-expired")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanupExpiredTokens() {
        log.info("SECURITY: Admin requested cleanup of expired CSRF tokens");
        
        try {
            Set<String> tokenKeys = redisTemplate.keys(CSRF_TOKEN_PREFIX + "*");
            int cleanedCount = 0;
            
            if (tokenKeys != null) {
                for (String key : tokenKeys) {
                    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                    if (ttl != null && ttl <= 0) {
                        redisTemplate.delete(key);
                        cleanedCount++;
                    }
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "cleanedTokens", cleanedCount,
                "message", "Expired CSRF tokens cleaned up",
                "timestamp", System.currentTimeMillis()
            ));
            
        } catch (Exception e) {
            log.error("SECURITY: Error cleaning up expired CSRF tokens", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false,
                           "error", "Failed to cleanup tokens",
                           "message", e.getMessage()));
        }
    }

    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        log.debug("SECURITY: Admin requested CSRF protection health check");
        
        Map<String, Object> health = new HashMap<>();
        health.put("csrfEnabled", true);
        health.put("properlyConfigured", configValidator.isProperlyConfigured());
        
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            health.put("redisConnected", true);
        } catch (Exception e) {
            health.put("redisConnected", false);
            health.put("redisError", e.getMessage());
        }
        
        boolean healthy = configValidator.isProperlyConfigured() && 
                         (boolean)health.getOrDefault("redisConnected", false);
        
        health.put("healthy", healthy);
        health.put("timestamp", System.currentTimeMillis());
        
        return healthy ? 
            ResponseEntity.ok(health) : 
            ResponseEntity.status(503).body(health);
    }

    @GetMapping("/configuration")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SECURITY_ADMIN')")
    public ResponseEntity<Map<String, Object>> getConfiguration() {
        log.info("SECURITY: Admin requested CSRF configuration details");
        
        Map<String, Object> config = new HashMap<>();
        config.put("summary", configValidator.getConfigurationSummary());
        config.put("properlyConfigured", configValidator.isProperlyConfigured());
        config.put("cookieAttributes", csrfProtectionService.generateCookieAttributes());
        config.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(config);
    }
}