package com.waqiti.common.security;

import com.waqiti.common.security.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Service for managing JWT token blacklist
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final JwtProperties jwtProperties;
    
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String USER_TOKENS_PREFIX = "jwt:user_tokens:";
    
    /**
     * Blacklist a JWT token
     */
    public void blacklistToken(String tokenId, Instant expirationTime) {
        try {
            String blacklistKey = BLACKLIST_PREFIX + tokenId;
            Duration ttl = Duration.between(Instant.now(), expirationTime);
            
            if (!ttl.isNegative() && !ttl.isZero()) {
                redisTemplate.opsForValue().set(blacklistKey, "blacklisted", ttl);
                log.debug("Token blacklisted: {}", tokenId);
            }
            
        } catch (Exception e) {
            log.error("Failed to blacklist token: {}", tokenId, e);
        }
    }
    
    /**
     * Check if a token is blacklisted
     */
    public boolean isTokenBlacklisted(String tokenId) {
        try {
            String blacklistKey = BLACKLIST_PREFIX + tokenId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey));
            
        } catch (Exception e) {
            log.error("Failed to check blacklist status for token: {}", tokenId, e);
            return false; // Fail open for availability
        }
    }
    
    /**
     * Blacklist all tokens for a user
     */
    public void blacklistAllUserTokens(String userId) {
        try {
            String userTokensKey = USER_TOKENS_PREFIX + userId;
            Set<String> userTokens = redisTemplate.opsForSet().members(userTokensKey);
            
            if (userTokens != null) {
                for (String tokenId : userTokens) {
                    // Set to expire immediately for existing tokens
                    blacklistToken(tokenId, Instant.now().plusSeconds(86400)); // 24 hours
                }
                
                // Clear user tokens set
                redisTemplate.delete(userTokensKey);
                log.info("Blacklisted all tokens for user: {}, count: {}", userId, userTokens.size());
            }
            
        } catch (Exception e) {
            log.error("Failed to blacklist all tokens for user: {}", userId, e);
        }
    }
    
    /**
     * Track token for a user
     */
    public void trackUserToken(String userId, String tokenId, Instant expirationTime) {
        try {
            String userTokensKey = USER_TOKENS_PREFIX + userId;
            Duration ttl = Duration.between(Instant.now(), expirationTime);
            
            if (ttl.isPositive()) {
                redisTemplate.opsForSet().add(userTokensKey, tokenId);
                redisTemplate.expire(userTokensKey, ttl);
                log.debug("Tracking token for user: {}, token: {}", userId, tokenId);
            }
            
        } catch (Exception e) {
            log.error("Failed to track token for user: {}, token: {}", userId, tokenId, e);
        }
    }
    
    /**
     * Remove token tracking for a user
     */
    public void untrackUserToken(String userId, String tokenId) {
        try {
            String userTokensKey = USER_TOKENS_PREFIX + userId;
            redisTemplate.opsForSet().remove(userTokensKey, tokenId);
            log.debug("Removed token tracking for user: {}, token: {}", userId, tokenId);
            
        } catch (Exception e) {
            log.error("Failed to untrack token for user: {}, token: {}", userId, tokenId, e);
        }
    }
    
    /**
     * Get count of active tokens for a user
     */
    public long getActiveTokenCount(String userId) {
        try {
            String userTokensKey = USER_TOKENS_PREFIX + userId;
            Long count = redisTemplate.opsForSet().size(userTokensKey);
            return count != null ? count : 0;
            
        } catch (Exception e) {
            log.error("Failed to get active token count for user: {}", userId, e);
            return 0;
        }
    }
    
    /**
     * Clean up expired blacklist entries
     */
    public void cleanupExpiredEntries() {
        try {
            // Redis automatically handles TTL expiration, but we could add
            // additional cleanup logic here if needed
            log.debug("Cleanup of expired blacklist entries completed");
            
        } catch (Exception e) {
            log.error("Failed to cleanup expired blacklist entries", e);
        }
    }
}