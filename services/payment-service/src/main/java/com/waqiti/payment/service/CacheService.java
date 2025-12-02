package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class CacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public void put(String key, Object value) {
        put(key, value, Duration.ofHours(1));
    }
    
    public void put(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            log.debug("Cached value for key: {}", key);
        } catch (Exception e) {
            log.error("Failed to cache value for key: {}", key, e);
        }
    }
    
    public Optional<Object> get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.error("Failed to get cached value for key: {}", key, e);
            return Optional.empty();
        }
    }
    
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null && type.isInstance(value)) {
                return Optional.of((T) value);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get cached value for key: {}", key, e);
            return Optional.empty();
        }
    }
    
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("Deleted cache key: {}", key);
        } catch (Exception e) {
            log.error("Failed to delete cache key: {}", key, e);
        }
    }
    
    public void evict(String pattern) {
        try {
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Evicted {} keys matching pattern: {}", keys.size(), pattern);
            }
        } catch (Exception e) {
            log.error("Failed to evict cache keys for pattern: {}", pattern, e);
        }
    }
    
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Failed to check if key exists: {}", key, e);
            return false;
        }
    }
    
    public void expire(String key, Duration ttl) {
        try {
            redisTemplate.expire(key, ttl);
            log.debug("Set expiration for key: {} to {}", key, ttl);
        } catch (Exception e) {
            log.error("Failed to set expiration for key: {}", key, e);
        }
    }
}