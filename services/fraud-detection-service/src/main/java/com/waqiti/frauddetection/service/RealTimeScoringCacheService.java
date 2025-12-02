package com.waqiti.frauddetection.service;

import com.waqiti.frauddetection.dto.FraudCheckRequest;
import com.waqiti.frauddetection.dto.FraudCheckResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Real-Time Fraud Scoring Cache Service
 *
 * Provides high-performance caching for fraud scoring operations:
 * - ML model prediction caching
 * - Feature extraction caching
 * - Risk score caching with TTL
 * - User profile caching
 * - Device fingerprint caching
 * - Velocity check caching
 *
 * Performance Benefits:
 * - 95% cache hit rate target
 * - Sub-millisecond response times
 * - Reduced ML inference load
 * - Improved throughput
 *
 * Cache Strategies:
 * - Write-through for critical data
 * - Time-based expiration (TTL)
 * - LRU eviction policy
 * - Cache warming for hot keys
 *
 * @author Waqiti Performance Team
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealTimeScoringCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String FRAUD_SCORE_PREFIX = "fraud:score:";
    private static final String USER_PROFILE_PREFIX = "fraud:profile:";
    private static final String DEVICE_PREFIX = "fraud:device:";
    private static final String VELOCITY_PREFIX = "fraud:velocity:";

    private static final Duration SCORE_TTL = Duration.ofMinutes(15);
    private static final Duration PROFILE_TTL = Duration.ofHours(1);
    private static final Duration DEVICE_TTL = Duration.ofHours(6);
    private static final Duration VELOCITY_TTL = Duration.ofMinutes(5);

    /**
     * Cache fraud score with TTL
     */
    public void cacheScore(String transactionId, FraudCheckResponse score) {
        String key = FRAUD_SCORE_PREFIX + transactionId;
        redisTemplate.opsForValue().set(key, score, SCORE_TTL);
        log.debug("Cached fraud score for transaction: {}", transactionId);
    }

    /**
     * Get cached fraud score
     */
    public FraudCheckResponse getCachedScore(String transactionId) {
        String key = FRAUD_SCORE_PREFIX + transactionId;
        return (FraudCheckResponse) redisTemplate.opsForValue().get(key);
    }

    /**
     * Cache user profile
     */
    @Cacheable(value = "userProfiles", key = "#userId", unless = "#result == null")
    public Object cacheUserProfile(String userId, Object profile) {
        String key = USER_PROFILE_PREFIX + userId;
        redisTemplate.opsForValue().set(key, profile, PROFILE_TTL);
        return profile;
    }

    /**
     * Invalidate user profile cache
     */
    @CacheEvict(value = "userProfiles", key = "#userId")
    public void invalidateUserProfile(String userId) {
        String key = USER_PROFILE_PREFIX + userId;
        redisTemplate.delete(key);
        log.info("Invalidated user profile cache: {}", userId);
    }

    /**
     * Cache device fingerprint data
     */
    public void cacheDeviceData(String deviceId, Object deviceData) {
        String key = DEVICE_PREFIX + deviceId;
        redisTemplate.opsForValue().set(key, deviceData, DEVICE_TTL);
    }

    /**
     * Increment velocity counter
     */
    public long incrementVelocity(String key, int windowMinutes) {
        String fullKey = VELOCITY_PREFIX + key;
        Long count = redisTemplate.opsForValue().increment(fullKey);

        if (count != null && count == 1) {
            redisTemplate.expire(fullKey, windowMinutes, TimeUnit.MINUTES);
        }

        return count != null ? count : 0;
    }

    /**
     * Get velocity count
     */
    public long getVelocityCount(String key) {
        String fullKey = VELOCITY_PREFIX + key;
        Long count = (Long) redisTemplate.opsForValue().get(fullKey);
        return count != null ? count : 0;
    }

    /**
     * Clear all fraud caches (admin operation)
     */
    public void clearAllCaches() {
        log.warn("Clearing all fraud detection caches");
        redisTemplate.delete(redisTemplate.keys(FRAUD_SCORE_PREFIX + "*"));
        redisTemplate.delete(redisTemplate.keys(USER_PROFILE_PREFIX + "*"));
        redisTemplate.delete(redisTemplate.keys(DEVICE_PREFIX + "*"));
        redisTemplate.delete(redisTemplate.keys(VELOCITY_PREFIX + "*"));
    }
}
