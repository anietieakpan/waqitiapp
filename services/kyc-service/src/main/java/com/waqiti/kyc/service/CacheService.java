package com.waqiti.kyc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.Callable;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {

    private final CacheManager cacheManager;

    // Cache user verification status with custom key
    @Cacheable(value = "kyc-user-verified", key = "#userId + '-' + #level")
    public Boolean getUserVerificationStatus(String userId, String level, Callable<Boolean> valueLoader) {
        try {
            log.debug("Loading verification status from source for user: {} level: {}", userId, level);
            return valueLoader.call();
        } catch (Exception e) {
            log.error("Error loading verification status: ", e);
            return false; // Fail safe
        }
    }

    // Cache user action permissions
    @Cacheable(value = "kyc-user-actions", key = "#userId + '-' + #action")
    public Boolean getUserActionPermission(String userId, String action, Callable<Boolean> valueLoader) {
        try {
            log.debug("Loading action permission from source for user: {} action: {}", userId, action);
            return valueLoader.call();
        } catch (Exception e) {
            log.error("Error loading action permission: ", e);
            return false; // Fail safe
        }
    }

    // Evict user-specific caches when KYC status changes
    @CacheEvict(value = {"kyc-user-verified", "kyc-user-actions", "kyc-user-status"}, 
                allEntries = false, 
                key = "#userId")
    public void evictUserKYCCache(String userId) {
        log.info("Evicting KYC cache for user: {}", userId);
        
        // Also evict pattern-based cache entries
        evictCachePattern("kyc-user-verified", userId + "-");
        evictCachePattern("kyc-user-actions", userId + "-");
    }

    // Evict verification-specific cache
    @CacheEvict(value = "kyc-verifications", key = "#verificationId")
    public void evictVerificationCache(String verificationId) {
        log.info("Evicting verification cache for: {}", verificationId);
    }

    // Evict all user-related caches
    public void evictAllUserCaches(String userId) {
        log.info("Evicting all KYC caches for user: {}", userId);
        
        String[] cacheNames = {
            "kyc-user-verified",
            "kyc-user-actions", 
            "kyc-user-status",
            "kyc-user-verifications"
        };
        
        for (String cacheName : cacheNames) {
            evictCachePattern(cacheName, userId);
        }
    }

    // Warm up cache with commonly accessed data
    public void warmUpCache(String userId) {
        log.info("Warming up KYC cache for user: {}", userId);
        
        // Pre-load common verification levels
        String[] levels = {"BASIC", "INTERMEDIATE", "ADVANCED"};
        for (String level : levels) {
            try {
                Cache cache = cacheManager.getCache("kyc-user-verified");
                if (cache != null) {
                    String key = userId + "-" + level;
                    if (cache.get(key) == null) {
                        // Would typically call the actual service method here
                        log.debug("Pre-loading verification status for user: {} level: {}", userId, level);
                    }
                }
            } catch (Exception e) {
                log.warn("Error warming up cache for user: {} level: {}", userId, level, e);
            }
        }
        
        // Pre-load common actions
        String[] actions = {"SEND_MONEY", "RECEIVE_MONEY", "INTERNATIONAL_TRANSFER"};
        for (String action : actions) {
            try {
                Cache cache = cacheManager.getCache("kyc-user-actions");
                if (cache != null) {
                    String key = userId + "-" + action;
                    if (cache.get(key) == null) {
                        // Would typically call the actual service method here
                        log.debug("Pre-loading action permission for user: {} action: {}", userId, action);
                    }
                }
            } catch (Exception e) {
                log.warn("Error warming up cache for user: {} action: {}", userId, action, e);
            }
        }
    }

    // Get cache statistics
    public void logCacheStatistics() {
        Set<String> cacheNames = Set.of(
            "kyc-verifications",
            "kyc-user-verified", 
            "kyc-user-actions",
            "kyc-user-status",
            "kyc-documents"
        );
        
        for (String cacheName : cacheNames) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                log.info("Cache '{}' statistics: {}", cacheName, getCacheInfo(cache));
            }
        }
    }

    // Clear all KYC caches (for maintenance)
    public void clearAllKYCCaches() {
        log.warn("Clearing all KYC caches");
        
        Set<String> cacheNames = Set.of(
            "kyc-verifications",
            "kyc-user-verified", 
            "kyc-user-actions",
            "kyc-user-status",
            "kyc-user-verifications",
            "kyc-documents",
            "kyc-provider-sessions",
            "kyc-compliance-checks"
        );
        
        for (String cacheName : cacheNames) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                log.info("Cleared cache: {}", cacheName);
            }
        }
    }

    // Helper method to evict cache entries by pattern
    private void evictCachePattern(String cacheName, String pattern) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                // Note: This is a simplified approach. In production, you might need
                // a more sophisticated pattern matching mechanism depending on your cache provider
                cache.evictIfPresent(pattern);
            }
        } catch (Exception e) {
            log.error("Error evicting cache pattern '{}' from cache '{}': ", pattern, cacheName, e);
        }
    }

    // Helper method to get cache information
    private String getCacheInfo(Cache cache) {
        try {
            // This would be implementation-specific based on your cache provider
            return String.format("Cache: %s", cache.getName());
        } catch (Exception e) {
            return "Statistics unavailable";
        }
    }

    // Cache health check
    public boolean isCacheHealthy() {
        try {
            Cache cache = cacheManager.getCache("kyc-user-verified");
            if (cache != null) {
                // Test cache operations
                String testKey = "health-check-" + System.currentTimeMillis();
                cache.put(testKey, Boolean.TRUE);
                Boolean result = cache.get(testKey, Boolean.class);
                cache.evict(testKey);
                return Boolean.TRUE.equals(result);
            }
            return false;
        } catch (Exception e) {
            log.error("Cache health check failed: ", e);
            return false;
        }
    }
}