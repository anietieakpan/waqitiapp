package com.waqiti.common.ratelimit;

import com.waqiti.common.ratelimit.RateLimitModels.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Advanced Rate Limiting Service
 * 
 * Implements multiple rate limiting algorithms:
 * - Token Bucket
 * - Sliding Window Log
 * - Fixed Window Counter
 * - Sliding Window Counter
 * - Distributed rate limiting with Redis
 * - Per-user, per-IP, per-API endpoint limits
 * - Burst handling and traffic shaping
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    
    private static final String RATE_LIMIT_PREFIX = "rate_limit:";
    private static final String TOKEN_BUCKET_PREFIX = "token_bucket:";
    private static final String SLIDING_WINDOW_PREFIX = "sliding_window:";
    private static final String FIXED_WINDOW_PREFIX = "fixed_window:";
    
    // Lua script for atomic token bucket operation
    private static final String TOKEN_BUCKET_SCRIPT = """
        local key = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local refill_rate = tonumber(ARGV[2])
        local refill_period = tonumber(ARGV[3])
        local requested_tokens = tonumber(ARGV[4])
        local current_time = tonumber(ARGV[5])
        
        local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
        local tokens = tonumber(bucket[1]) or capacity
        local last_refill = tonumber(bucket[2]) or current_time
        
        -- Calculate tokens to add based on elapsed time
        local time_elapsed = math.max(0, current_time - last_refill)
        local tokens_to_add = math.floor(time_elapsed / refill_period * refill_rate)
        tokens = math.min(capacity, tokens + tokens_to_add)
        
        -- Check if request can be fulfilled
        if tokens >= requested_tokens then
            tokens = tokens - requested_tokens
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', current_time)
            redis.call('EXPIRE', key, 3600) -- 1 hour expiry
            return {1, tokens}
        else
            redis.call('HMSET', key, 'tokens', tokens, 'last_refill', current_time)
            redis.call('EXPIRE', key, 3600)
            return {0, tokens}
        end
        """;
    
    // Lua script for sliding window rate limiting
    private static final String SLIDING_WINDOW_SCRIPT = """
        local key = KEYS[1]
        local window = tonumber(ARGV[1])
        local limit = tonumber(ARGV[2])
        local current_time = tonumber(ARGV[3])
        local identifier = ARGV[4]
        
        -- Remove expired entries
        redis.call('ZREMRANGEBYSCORE', key, 0, current_time - window * 1000)
        
        -- Count current requests in window
        local current_requests = redis.call('ZCARD', key)
        
        if current_requests < limit then
            -- Add current request
            redis.call('ZADD', key, current_time, identifier)
            redis.call('EXPIRE', key, window + 1)
            return {1, limit - current_requests - 1}
        else
            return {0, 0}
        end
        """;
    
    /**
     * Check rate limit using token bucket algorithm
     */
    public RateLimitResult checkTokenBucket(String identifier, TokenBucketConfig config) {
        return checkTokenBucket(identifier, config, 1);
    }
    
    /**
     * Check rate limit using token bucket algorithm with custom token count
     */
    public RateLimitResult checkTokenBucket(String identifier, TokenBucketConfig config, int tokens) {
        String key = TOKEN_BUCKET_PREFIX + identifier;
        long currentTime = System.currentTimeMillis();
        
        try {
            @SuppressWarnings("unchecked")
            List<Long> result = (List<Long>) redisTemplate.execute(
                RedisScript.of(TOKEN_BUCKET_SCRIPT, List.class),
                Collections.singletonList(key),
                String.valueOf(config.getCapacity()),
                String.valueOf(config.getRefillRate()),
                String.valueOf(config.getRefillPeriodMs()),
                String.valueOf(tokens),
                String.valueOf(currentTime)
            );
            
            boolean allowed = result.get(0) == 1;
            long remainingTokens = result.get(1);
            
            return RateLimitResult.builder()
                    .allowed(allowed)
                    .remainingTokens(remainingTokens)
                    .resetTimeMs(currentTime + config.getRefillPeriodMs())
                    .identifier(identifier)
                    .algorithm("TOKEN_BUCKET")
                    .build();
                    
        } catch (Exception e) {
            log.error("Error checking token bucket rate limit for: {}", identifier, e);
            // Fail open - allow request if Redis is down
            return RateLimitResult.builder()
                    .allowed(true)
                    .remainingTokens(config.getCapacity())
                    .identifier(identifier)
                    .algorithm("TOKEN_BUCKET")
                    .error("Redis error")
                    .build();
        }
    }
    
    /**
     * Check rate limit using sliding window log algorithm
     */
    public RateLimitResult checkSlidingWindow(String identifier, SlidingWindowConfig config) {
        String key = SLIDING_WINDOW_PREFIX + identifier;
        long currentTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        
        try {
            @SuppressWarnings("unchecked")
            List<Long> result = (List<Long>) redisTemplate.execute(
                RedisScript.of(SLIDING_WINDOW_SCRIPT, List.class),
                Collections.singletonList(key),
                String.valueOf(config.getWindowSizeSeconds()),
                String.valueOf(config.getMaxRequests()),
                String.valueOf(currentTime),
                requestId
            );
            
            boolean allowed = result.get(0) == 1;
            long remainingRequests = result.get(1);
            
            return RateLimitResult.builder()
                    .allowed(allowed)
                    .remainingRequests(remainingRequests)
                    .resetTimeMs(currentTime + (config.getWindowSizeSeconds() * 1000))
                    .identifier(identifier)
                    .algorithm("SLIDING_WINDOW")
                    .build();
                    
        } catch (Exception e) {
            log.error("Error checking sliding window rate limit for: {}", identifier, e);
            return RateLimitResult.builder()
                    .allowed(true)
                    .remainingRequests(config.getMaxRequests())
                    .identifier(identifier)
                    .algorithm("SLIDING_WINDOW")
                    .error("Redis error")
                    .build();
        }
    }
    
    /**
     * Check rate limit using fixed window counter
     */
    public RateLimitResult checkFixedWindow(String identifier, FixedWindowConfig config) {
        long currentTime = System.currentTimeMillis();
        long windowStart = (currentTime / (config.getWindowSizeSeconds() * 1000)) * (config.getWindowSizeSeconds() * 1000);
        String key = FIXED_WINDOW_PREFIX + identifier + ":" + windowStart;
        
        try {
            Long currentCount = redisTemplate.opsForValue().increment(key);
            
            if (currentCount == 1) {
                // Set expiry for new window
                redisTemplate.expire(key, Duration.ofSeconds(config.getWindowSizeSeconds() + 1));
            }
            
            boolean allowed = currentCount <= config.getMaxRequests();
            long remainingRequests = Math.max(0, config.getMaxRequests() - currentCount);
            long resetTime = windowStart + (config.getWindowSizeSeconds() * 1000);
            
            return RateLimitResult.builder()
                    .allowed(allowed)
                    .remainingRequests(remainingRequests)
                    .resetTimeMs(resetTime)
                    .identifier(identifier)
                    .algorithm("FIXED_WINDOW")
                    .currentCount(currentCount)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error checking fixed window rate limit for: {}", identifier, e);
            return RateLimitResult.builder()
                    .allowed(true)
                    .remainingRequests(config.getMaxRequests())
                    .identifier(identifier)
                    .algorithm("FIXED_WINDOW")
                    .error("Redis error")
                    .build();
        }
    }
    
    /**
     * Check multiple rate limits (e.g., per-user + per-IP + per-endpoint)
     */
    public RateLimitResult checkMultiple(String baseIdentifier, List<RateLimitCheck> checks) {
        for (RateLimitCheck check : checks) {
            String identifier = baseIdentifier + ":" + check.getSuffix();
            RateLimitResult result;
            
            switch (check.getType()) {
                case TOKEN_BUCKET:
                    result = checkTokenBucket(identifier, check.getTokenBucketConfig());
                    break;
                case SLIDING_WINDOW:
                    result = checkSlidingWindow(identifier, check.getSlidingWindowConfig());
                    break;
                case FIXED_WINDOW:
                    result = checkFixedWindow(identifier, check.getFixedWindowConfig());
                    break;
                default:
                    continue;
            }
            
            if (!result.isAllowed()) {
                // Return first violation
                return result;
            }
        }
        
        // All checks passed
        return RateLimitResult.builder()
                .allowed(true)
                .identifier(baseIdentifier)
                .algorithm("MULTIPLE")
                .build();
    }
    
    /**
     * Get current rate limit status without consuming tokens/requests
     */
    public RateLimitStatus getStatus(String identifier, RateLimitType type) {
        try {
            switch (type) {
                case TOKEN_BUCKET:
                    return getTokenBucketStatus(identifier);
                case SLIDING_WINDOW:
                    return getSlidingWindowStatus(identifier);
                case FIXED_WINDOW:
                    return getFixedWindowStatus(identifier);
                default:
                    return RateLimitStatus.builder()
                            .identifier(identifier)
                            .available(true)
                            .build();
            }
        } catch (Exception e) {
            log.error("Error getting rate limit status for: {}", identifier, e);
            return RateLimitStatus.builder()
                    .identifier(identifier)
                    .available(true)
                    .error("Redis error")
                    .build();
        }
    }
    
    /**
     * Reset rate limit for identifier
     */
    public void reset(String identifier, RateLimitType type) {
        try {
            String pattern = switch (type) {
                case TOKEN_BUCKET -> TOKEN_BUCKET_PREFIX + identifier;
                case SLIDING_WINDOW -> SLIDING_WINDOW_PREFIX + identifier;
                case FIXED_WINDOW -> FIXED_WINDOW_PREFIX + identifier + "*";
                case LEAKY_BUCKET -> TOKEN_BUCKET_PREFIX + identifier; // Use same prefix as token bucket
            };
            
            if (type == RateLimitType.FIXED_WINDOW) {
                // Delete all windows for this identifier
                Set<String> keys = redisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } else {
                redisTemplate.delete(pattern);
            }
            
            log.info("Reset rate limit for identifier: {} type: {}", identifier, type);
            
        } catch (Exception e) {
            log.error("Error resetting rate limit for: {}", identifier, e);
        }
    }
    
    /**
     * Bulk reset rate limits by pattern
     */
    public void resetByPattern(String pattern, RateLimitType type) {
        try {
            String fullPattern = switch (type) {
                case TOKEN_BUCKET -> TOKEN_BUCKET_PREFIX + pattern;
                case SLIDING_WINDOW -> SLIDING_WINDOW_PREFIX + pattern;
                case FIXED_WINDOW -> FIXED_WINDOW_PREFIX + pattern;
                case LEAKY_BUCKET -> TOKEN_BUCKET_PREFIX + pattern; // Use same prefix as token bucket
            };
            
            Set<String> keys = redisTemplate.keys(fullPattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Reset {} rate limit keys for pattern: {}", keys.size(), pattern);
            }
            
        } catch (Exception e) {
            log.error("Error resetting rate limits by pattern: {}", pattern, e);
        }
    }
    
    /**
     * Get rate limiting statistics
     */
    public RateLimitStats getStats() {
        try {
            // Count keys by type
            long tokenBucketKeys = countKeysWithPrefix(TOKEN_BUCKET_PREFIX);
            long slidingWindowKeys = countKeysWithPrefix(SLIDING_WINDOW_PREFIX);
            long fixedWindowKeys = countKeysWithPrefix(FIXED_WINDOW_PREFIX);
            
            return RateLimitStats.builder()
                    .tokenBucketKeys(tokenBucketKeys)
                    .slidingWindowKeys(slidingWindowKeys)
                    .fixedWindowKeys(fixedWindowKeys)
                    .totalKeys(tokenBucketKeys + slidingWindowKeys + fixedWindowKeys)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error getting rate limit stats", e);
            return RateLimitStats.builder().build();
        }
    }
    
    // Private helper methods
    
    private RateLimitStatus getTokenBucketStatus(String identifier) {
        String key = TOKEN_BUCKET_PREFIX + identifier;
        List<Object> valuesObj = redisTemplate.opsForHash().multiGet(key, 
                Arrays.asList("tokens", "last_refill"));
        List<String> values = valuesObj.stream()
                .map(v -> v != null ? v.toString() : null)
                .collect(java.util.stream.Collectors.toList());
        
        if (values.get(0) == null) {
            return RateLimitStatus.builder()
                    .identifier(identifier)
                    .available(true)
                    .build();
        }
        
        long tokens = Long.parseLong(values.get(0));
        long lastRefill = Long.parseLong(values.get(1));
        
        return RateLimitStatus.builder()
                .identifier(identifier)
                .available(tokens > 0)
                .remainingTokens(tokens)
                .lastRefillTime(Instant.ofEpochMilli(lastRefill))
                .build();
    }
    
    private RateLimitStatus getSlidingWindowStatus(String identifier) {
        String key = SLIDING_WINDOW_PREFIX + identifier;
        Long count = redisTemplate.opsForZSet().zCard(key);
        
        return RateLimitStatus.builder()
                .identifier(identifier)
                .available(true)
                .currentRequests(count != null ? count : 0)
                .build();
    }
    
    private RateLimitStatus getFixedWindowStatus(String identifier) {
        long currentTime = System.currentTimeMillis();
        long windowStart = (currentTime / 60000) * 60000; // Assuming 1-minute windows
        String key = FIXED_WINDOW_PREFIX + identifier + ":" + windowStart;
        
        String countStr = redisTemplate.opsForValue().get(key);
        long count = countStr != null ? Long.parseLong(countStr) : 0;
        
        return RateLimitStatus.builder()
                .identifier(identifier)
                .available(true)
                .currentRequests(count)
                .windowStart(Instant.ofEpochMilli(windowStart))
                .build();
    }
    
    private long countKeysWithPrefix(String prefix) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        return keys != null ? keys.size() : 0;
    }
}