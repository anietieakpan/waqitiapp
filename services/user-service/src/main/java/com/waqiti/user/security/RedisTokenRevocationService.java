package com.waqiti.user.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Redis-based distributed token revocation service
 * Handles token blacklisting across multiple service instances
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisTokenRevocationService {

    private static final String REVOKED_TOKEN_PREFIX = "revoked_token:";
    private static final String USER_REVOKED_TOKENS_PREFIX = "user_revoked_tokens:";
    
    private final StringRedisTemplate redisTemplate;

    /**
     * Revoke a specific token by its ID
     */
    public void revokeToken(String tokenId, Date expiration) {
        try {
            // Calculate TTL based on token expiration
            long ttlSeconds = calculateTtl(expiration);
            
            if (ttlSeconds > 0) {
                String key = REVOKED_TOKEN_PREFIX + tokenId;
                redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(ttlSeconds));
                log.info("Token revoked in Redis: {} (TTL: {} seconds)", tokenId, ttlSeconds);
            } else {
                log.debug("Token already expired, not adding to revocation list: {}", tokenId);
            }
        } catch (Exception e) {
            log.error("Error revoking token in Redis: {}", tokenId, e);
            throw new RuntimeException("Failed to revoke token", e);
        }
    }

    /**
     * Check if a token is revoked
     */
    public boolean isTokenRevoked(String tokenId) {
        try {
            String key = REVOKED_TOKEN_PREFIX + tokenId;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Error checking token revocation status: {}", tokenId, e);
            // In case of Redis failure, assume token is not revoked for availability
            // but log the error for monitoring
            return false;
        }
    }

    /**
     * Revoke all tokens for a specific user
     */
    public void revokeAllUserTokens(String userId) {
        try {
            String key = USER_REVOKED_TOKENS_PREFIX + userId;
            long currentTime = Instant.now().getEpochSecond();
            
            // Set user revocation timestamp with 30 days TTL (max refresh token lifetime)
            redisTemplate.opsForValue().set(key, String.valueOf(currentTime), Duration.ofDays(30));
            log.info("All tokens revoked for user: {}", userId);
        } catch (Exception e) {
            log.error("Error revoking all tokens for user: {}", userId, e);
            throw new RuntimeException("Failed to revoke user tokens", e);
        }
    }

    /**
     * Check if user tokens were globally revoked after token issue time
     */
    public boolean areUserTokensRevoked(String userId, Date tokenIssuedAt) {
        try {
            String key = USER_REVOKED_TOKENS_PREFIX + userId;
            String revocationTimeStr = redisTemplate.opsForValue().get(key);
            
            if (revocationTimeStr == null) {
                return false;
            }
            
            long revocationTime = Long.parseLong(revocationTimeStr);
            long tokenIssueTime = tokenIssuedAt.toInstant().getEpochSecond();
            
            return revocationTime > tokenIssueTime;
        } catch (Exception e) {
            log.error("Error checking user token revocation: {}", userId, e);
            return false;
        }
    }

    /**
     * Calculate TTL for token revocation entry
     */
    private long calculateTtl(Date expiration) {
        if (expiration == null) {
            return Duration.ofDays(30).toSeconds(); // Default 30 days
        }
        
        long expirationTime = expiration.toInstant().getEpochSecond();
        long currentTime = Instant.now().getEpochSecond();
        
        return Math.max(0, expirationTime - currentTime);
    }

    /**
     * Clean up expired revocation entries (called by scheduled task)
     */
    public void cleanupExpiredRevocations() {
        try {
            // Redis automatically handles TTL, but we can add custom cleanup logic here if needed
            log.debug("Redis TTL automatically handles cleanup of expired revocation entries");
        } catch (Exception e) {
            log.error("Error during revocation cleanup", e);
        }
    }

    /**
     * Get revocation statistics for monitoring
     */
    public long getActiveRevocationsCount() {
        try {
            return redisTemplate.countExistingKeys(
                redisTemplate.keys(REVOKED_TOKEN_PREFIX + "*")
            );
        } catch (Exception e) {
            log.error("Error getting revocation count", e);
            return -1;
        }
    }

    /**
     * Mark user tokens for revalidation due to security trigger
     */
    public void markUserTokensForRevalidation(String userId, Object securityTrigger) {
        try {
            String key = "revalidation:" + userId;
            String triggerInfo = securityTrigger != null ? securityTrigger.toString() : "SECURITY_EVENT";
            long currentTime = Instant.now().getEpochSecond();
            
            String value = currentTime + ":" + triggerInfo;
            redisTemplate.opsForValue().set(key, value, Duration.ofHours(1)); // 1 hour revalidation window
            
            log.info("Marked tokens for revalidation for user: {} due to: {}", userId, triggerInfo);
        } catch (Exception e) {
            log.error("Error marking tokens for revalidation for user: {}", userId, e);
        }
    }
    
    /**
     * Check if user tokens need revalidation
     */
    public boolean needsRevalidation(String userId, Date tokenIssuedAt) {
        try {
            String key = "revalidation:" + userId;
            String value = redisTemplate.opsForValue().get(key);
            
            if (value == null) {
                return false;
            }
            
            String[] parts = value.split(":", 2);
            if (parts.length < 1) {
                return false;
            }
            
            long revalidationTime = Long.parseLong(parts[0]);
            long tokenIssueTime = tokenIssuedAt.toInstant().getEpochSecond();
            
            return revalidationTime > tokenIssueTime;
        } catch (Exception e) {
            log.error("Error checking revalidation status for user: {}", userId, e);
            return false;
        }
    }
    
    /**
     * Store token metadata for security tracking
     */
    public void storeTokenMetadata(String tokenId, Object tokenMetadata) {
        try {
            String key = "token_metadata:" + tokenId;
            String metadataJson = tokenMetadata != null ? tokenMetadata.toString() : "{}";
            
            // Store with 1 day TTL for metadata tracking
            redisTemplate.opsForValue().set(key, metadataJson, Duration.ofDays(1));
            
            log.debug("Stored token metadata for token: {}", tokenId);
        } catch (Exception e) {
            log.error("Error storing token metadata for token: {}", tokenId, e);
        }
    }
    
    /**
     * Retrieve token metadata
     */
    public String getTokenMetadata(String tokenId) {
        try {
            String key = "token_metadata:" + tokenId;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error retrieving token metadata for token: {}", tokenId, e);
            return null;
        }
    }
}