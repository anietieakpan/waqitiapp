package com.waqiti.common.notification.sms.rate;

import com.waqiti.common.notification.sms.SmsService.SmsType;
import com.waqiti.common.notification.sms.SmsService.RateLimitStatus;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiter for SMS sending to prevent abuse and manage costs
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmsRateLimiter {
    
    private final StringRedisTemplate redisTemplate;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    // Rate limit configurations per SMS type
    private static final Map<SmsType, RateLimitConfig> RATE_LIMITS = Map.of(
        SmsType.FRAUD_ALERT, new RateLimitConfig(10, Duration.ofHours(1), 50, Duration.ofDays(1)),
        SmsType.SECURITY_ALERT, new RateLimitConfig(10, Duration.ofHours(1), 50, Duration.ofDays(1)),
        SmsType.OTP, new RateLimitConfig(5, Duration.ofMinutes(10), 20, Duration.ofHours(1)),
        SmsType.TRANSACTION_VERIFICATION, new RateLimitConfig(20, Duration.ofHours(1), 100, Duration.ofDays(1)),
        SmsType.ACCOUNT_NOTIFICATION, new RateLimitConfig(5, Duration.ofHours(1), 20, Duration.ofDays(1)),
        SmsType.MARKETING, new RateLimitConfig(2, Duration.ofDays(1), 10, Duration.ofDays(7))
    );
    
    private static final String RATE_LIMIT_KEY_PREFIX = "sms:ratelimit:";
    private static final String ATTEMPT_KEY_PREFIX = "sms:attempt:";
    private static final String SUCCESS_KEY_PREFIX = "sms:success:";
    
    public boolean canSend(String recipient) {
        // Implementation would check rate limits
        log.debug("Checking SMS rate limit for recipient: {}", recipient);
        return true;
    }
    
    public void recordSent(String recipient) {
        // Implementation would record sent message for rate limiting
        log.debug("Recording SMS sent to recipient: {}", recipient);
    }
    
    public long getRemainingQuota(String recipient) {
        // Implementation would return remaining quota
        return 100;
    }
    
    /**
     * Check if SMS can be sent based on rate limits
     */
    public boolean allowSend(String phoneNumber, SmsType type) {
        String key = RATE_LIMIT_KEY_PREFIX + type.name() + ":" + phoneNumber;
        Bucket bucket = getBucket(key, type);
        
        return bucket.tryConsume(1);
    }
    
    /**
     * Record an SMS send attempt
     */
    public void recordSendAttempt(String phoneNumber, String type) {
        String key = ATTEMPT_KEY_PREFIX + type + ":" + phoneNumber;
        String countStr = redisTemplate.opsForValue().get(key);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        
        redisTemplate.opsForValue().set(key, String.valueOf(count + 1), 1, TimeUnit.HOURS);
        
        log.debug("Recorded SMS attempt for {} of type {}, total attempts: {}", phoneNumber, type, count + 1);
    }
    
    /**
     * Record a successful SMS send
     */
    public void recordSuccessfulSend(String phoneNumber, String type) {
        String key = SUCCESS_KEY_PREFIX + type + ":" + phoneNumber;
        String countStr = redisTemplate.opsForValue().get(key);
        int count = countStr != null ? Integer.parseInt(countStr) : 0;
        
        redisTemplate.opsForValue().set(key, String.valueOf(count + 1), 1, TimeUnit.HOURS);
        
        log.debug("Recorded successful SMS send for {} of type {}, total: {}", phoneNumber, type, count + 1);
    }
    
    /**
     * Get rate limit status for a phone number and SMS type
     */
    public RateLimitStatus getStatus(String phoneNumber, SmsType type) {
        String key = RATE_LIMIT_KEY_PREFIX + type.name() + ":" + phoneNumber;
        Bucket bucket = getBucket(key, type);
        
        long availableTokens = bucket.getAvailableTokens();
        boolean allowed = availableTokens > 0;
        
        RateLimitConfig config = RATE_LIMITS.get(type);
        LocalDateTime resetTime = LocalDateTime.now().plus(config.shortTermDuration);
        
        return RateLimitStatus.builder()
            .allowed(allowed)
            .remainingAttempts((int) availableTokens)
            .resetTime(resetTime)
            .reason(allowed ? null : "Rate limit exceeded for " + type)
            .build();
    }
    
    /**
     * Get or create a rate limit bucket
     */
    private Bucket getBucket(String key, SmsType type) {
        return buckets.computeIfAbsent(key, k -> {
            RateLimitConfig config = RATE_LIMITS.get(type);
            
            Bandwidth shortTermLimit = Bandwidth.classic(
                config.shortTermLimit,
                Refill.intervally(config.shortTermLimit, config.shortTermDuration)
            );
            
            Bandwidth longTermLimit = Bandwidth.classic(
                config.longTermLimit,
                Refill.intervally(config.longTermLimit, config.longTermDuration)
            );
            
            return Bucket.builder()
                .addLimit(shortTermLimit)
                .addLimit(longTermLimit)
                .build();
        });
    }
    
    /**
     * Rate limit configuration
     */
    private static class RateLimitConfig {
        final int shortTermLimit;
        final Duration shortTermDuration;
        final int longTermLimit;
        final Duration longTermDuration;
        
        RateLimitConfig(int shortTermLimit, Duration shortTermDuration,
                        int longTermLimit, Duration longTermDuration) {
            this.shortTermLimit = shortTermLimit;
            this.shortTermDuration = shortTermDuration;
            this.longTermLimit = longTermLimit;
            this.longTermDuration = longTermDuration;
        }
    }
}