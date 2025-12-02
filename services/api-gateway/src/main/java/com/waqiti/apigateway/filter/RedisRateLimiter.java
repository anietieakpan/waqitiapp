package com.waqiti.apigateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit; /**
 * Distributed Rate Limiter using Redis
 */
@Component("customRedisRateLimiter")
@RequiredArgsConstructor
@Slf4j
public class RedisRateLimiter {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public boolean allowRequest(String key, int limit, Duration window) {
        String redisKey = "rate_limit:" + key;
        Long currentCount = redisTemplate.opsForValue().increment(redisKey);
        
        if (currentCount == 1) {
            // First request in window, set expiration
            redisTemplate.expire(redisKey, window);
        }
        
        return currentCount <= limit;
    }
    
    public long getRequestCount(String key) {
        String redisKey = "rate_limit:" + key;
        Object count = redisTemplate.opsForValue().get(redisKey);
        return count != null ? Long.parseLong(count.toString()) : 0L;
    }
    
    public long getTimeToReset(String key) {
        String redisKey = "rate_limit:" + key;
        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        return ttl != null ? ttl : 0L;
    }
}
