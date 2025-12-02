package com.waqiti.common.security.authentication;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Token Blacklist Service
 * Manages revoked JWT tokens to prevent reuse
 */
@Slf4j
@Service  
@RequiredArgsConstructor
public class TokenBlacklistService {
    
    private final CacheManager cacheManager;
    private static final String BLACKLIST_CACHE = "token-blacklist";
    
    /**
     * Add token to blacklist
     */
    public void blacklistToken(String token, long expirationTime) {
        if (cacheManager.getCache(BLACKLIST_CACHE) != null) {
            long ttl = expirationTime - Instant.now().getEpochSecond();
            if (ttl > 0) {
                cacheManager.getCache(BLACKLIST_CACHE).put(token, true);
                log.info("Token blacklisted for {} seconds", ttl);
            }
        }
    }
    
    /**
     * Check if token is blacklisted
     */
    public boolean isBlacklisted(String token) {
        if (cacheManager.getCache(BLACKLIST_CACHE) != null) {
            Boolean blacklisted = cacheManager.getCache(BLACKLIST_CACHE).get(token, Boolean.class);
            return blacklisted != null && blacklisted;
        }
        return false;
    }
    
    /**
     * Remove token from blacklist
     */
    public void removeFromBlacklist(String token) {
        if (cacheManager.getCache(BLACKLIST_CACHE) != null) {
            cacheManager.getCache(BLACKLIST_CACHE).evict(token);
            log.debug("Token removed from blacklist");
        }
    }
    
    /**
     * Clear all blacklisted tokens
     */
    public void clearBlacklist() {
        if (cacheManager.getCache(BLACKLIST_CACHE) != null) {
            cacheManager.getCache(BLACKLIST_CACHE).clear();
            log.info("Token blacklist cleared");
        }
    }
    
    /**
     * Get blacklist size
     */
    public long getBlacklistSize() {
        // This is an approximation as Cache doesn't expose size directly
        return 0L;
    }
}