package com.waqiti.arpayment.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * AR Cache Service
 * Redis-based caching for AR session data and frequently accessed objects
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ARCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    public void cacheSessionData(String sessionToken, Object data) {
        try {
            String key = "ar:session:" + sessionToken;
            redisTemplate.opsForValue().set(key, data, DEFAULT_TTL);
            log.debug("Cached session data for: {}", sessionToken);
        } catch (Exception e) {
            log.error("Failed to cache session data", e);
        }
    }

    public Object getSessionData(String sessionToken) {
        try {
            String key = "ar:session:" + sessionToken;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Failed to retrieve cached session data", e);
            return null;
        }
    }

    public void invalidateSession(String sessionToken) {
        try {
            String key = "ar:session:" + sessionToken;
            redisTemplate.delete(key);
            log.debug("Invalidated cache for session: {}", sessionToken);
        } catch (Exception e) {
            log.error("Failed to invalidate session cache", e);
        }
    }
}
