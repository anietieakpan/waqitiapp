package com.waqiti.messaging.service.impl;

import com.waqiti.messaging.service.SecureKeyStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisSecureKeyStorage implements SecureKeyStorage {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final String KEY_PREFIX = "secure:keys:";
    private static final long KEY_TTL_DAYS = 365; // 1 year TTL
    
    @Override
    public void store(String key, String value) {
        try {
            String redisKey = KEY_PREFIX + key;
            redisTemplate.opsForValue().set(redisKey, value, KEY_TTL_DAYS, TimeUnit.DAYS);
            log.debug("Stored key in Redis: {}", redisKey);
        } catch (Exception e) {
            log.error("Failed to store key in Redis: {}", key, e);
            throw new RuntimeException("Failed to store key securely", e);
        }
    }
    
    @Override
    public String retrieve(String key) {
        try {
            String redisKey = KEY_PREFIX + key;
            String value = redisTemplate.opsForValue().get(redisKey);
            
            if (value != null) {
                // Refresh TTL on access
                redisTemplate.expire(redisKey, KEY_TTL_DAYS, TimeUnit.DAYS);
                log.debug("Retrieved key from Redis: {}", redisKey);
            }
            
            return value;
        } catch (Exception e) {
            log.error("Failed to retrieve key from Redis: {}", key, e);
            throw new RuntimeException("Failed to retrieve key securely", e);
        }
    }
    
    @Override
    public void delete(String key) {
        try {
            String redisKey = KEY_PREFIX + key;
            Boolean deleted = redisTemplate.delete(redisKey);
            log.debug("Deleted key from Redis: {} (existed: {})", redisKey, deleted);
        } catch (Exception e) {
            log.error("Failed to delete key from Redis: {}", key, e);
            throw new RuntimeException("Failed to delete key securely", e);
        }
    }
    
    @Override
    public boolean exists(String key) {
        try {
            String redisKey = KEY_PREFIX + key;
            Boolean exists = redisTemplate.hasKey(redisKey);
            return exists != null && exists;
        } catch (Exception e) {
            log.error("Failed to check key existence in Redis: {}", key, e);
            return false;
        }
    }
}