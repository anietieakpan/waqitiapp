package com.waqiti.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for rate limiting notification sending per user, category, and globally
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // In-memory rate limiters for performance
    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    
    // Rate limit configurations
    private static final int GLOBAL_EMAIL_LIMIT_PER_HOUR = 10000;
    private static final int GLOBAL_SMS_LIMIT_PER_HOUR = 5000;
    private static final int GLOBAL_PUSH_LIMIT_PER_HOUR = 50000;
    
    private static final int USER_EMAIL_LIMIT_PER_HOUR = 20;
    private static final int USER_SMS_LIMIT_PER_HOUR = 10;
    private static final int USER_PUSH_LIMIT_PER_HOUR = 100;
    
    private static final int TRANSACTIONAL_EMAIL_LIMIT_PER_MINUTE = 100;
    private static final int MARKETING_EMAIL_LIMIT_PER_MINUTE = 500;
    private static final int SYSTEM_EMAIL_LIMIT_PER_MINUTE = 200;
    
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(60);
    
    /**
     * Check if sending a notification is allowed for the given parameters
     */
    public boolean isAllowed(String channel, String category, String userId) {
        return isAllowed(channel, category, userId, 1);
    }
    
    /**
     * Check if sending multiple notifications is allowed
     */
    public boolean isAllowed(String channel, String category, String userId, int count) {
        // Check global rate limits
        if (!checkGlobalRateLimit(channel, count)) {
            log.warn("Global rate limit exceeded for channel: {}", channel);
            return false;
        }
        
        // Check category rate limits
        if (!checkCategoryRateLimit(channel, category, count)) {
            log.warn("Category rate limit exceeded for channel: {} category: {}", channel, category);
            return false;
        }
        
        // Check user rate limits
        if (!checkUserRateLimit(channel, userId, count)) {
            log.warn("User rate limit exceeded for channel: {} user: {}", channel, maskUserId(userId));
            return false;
        }
        
        return true;
    }
    
    /**
     * Consume rate limit tokens (call after sending successfully)
     */
    public void consume(String channel, String category, String userId) {
        consume(channel, category, userId, 1);
    }
    
    /**
     * Consume multiple rate limit tokens
     */
    public void consume(String channel, String category, String userId, int count) {
        // Consume from global rate limiter
        consumeGlobalRateLimit(channel, count);
        
        // Consume from category rate limiter
        consumeCategoryRateLimit(channel, category, count);
        
        // Consume from user rate limiter
        consumeUserRateLimit(channel, userId, count);
        
        log.debug("Rate limit tokens consumed - Channel: {}, Category: {}, User: {}, Count: {}", 
            channel, category, maskUserId(userId), count);
    }
    
    /**
     * Get remaining rate limit for user
     */
    public RateLimitInfo getRateLimitInfo(String channel, String userId) {
        RateLimitInfo info = new RateLimitInfo();
        info.setChannel(channel);
        info.setUserId(userId);
        
        String userKey = buildUserRateLimitKey(channel, userId);
        RateLimiter userLimiter = getRateLimiter(userKey, getUserRateLimit(channel));
        
        info.setLimit(userLimiter.getLimit());
        info.setRemaining(userLimiter.getRemaining());
        info.setResetTime(userLimiter.getResetTime());
        info.setWindowDuration(RATE_LIMIT_WINDOW);
        
        return info;
    }
    
    /**
     * Reset rate limits for a user (admin function)
     */
    public void resetUserRateLimit(String channel, String userId) {
        String userKey = buildUserRateLimitKey(channel, userId);
        rateLimiters.remove(userKey);
        
        try {
            redisTemplate.delete(RATE_LIMIT_KEY_PREFIX + userKey);
        } catch (Exception e) {
            log.error("Failed to reset user rate limit in Redis: {}", e.getMessage());
        }
        
        log.info("Rate limit reset for user: {} channel: {}", maskUserId(userId), channel);
    }
    
    /**
     * Get system-wide rate limit statistics
     */
    public RateLimitStats getStats() {
        RateLimitStats stats = new RateLimitStats();
        
        // Count active rate limiters
        stats.setActiveRateLimiters(rateLimiters.size());
        
        // Calculate usage statistics
        long totalRequests = rateLimiters.values().stream()
            .mapToLong(RateLimiter::getUsedTokens)
            .sum();
        stats.setTotalRequests(totalRequests);
        
        long totalCapacity = rateLimiters.values().stream()
            .mapToLong(RateLimiter::getLimit)
            .sum();
        stats.setTotalCapacity(totalCapacity);
        
        if (totalCapacity > 0) {
            stats.setUtilizationRate((double) totalRequests / totalCapacity);
        }
        
        // Count throttled requests
        long throttledRequests = rateLimiters.values().stream()
            .mapToLong(RateLimiter::getThrottledRequests)
            .sum();
        stats.setThrottledRequests(throttledRequests);
        
        return stats;
    }
    
    private boolean checkGlobalRateLimit(String channel, int count) {
        String globalKey = buildGlobalRateLimitKey(channel);
        int globalLimit = getGlobalRateLimit(channel);
        
        RateLimiter globalLimiter = getRateLimiter(globalKey, globalLimit);
        return globalLimiter.tryAcquire(count);
    }
    
    private boolean checkCategoryRateLimit(String channel, String category, int count) {
        String categoryKey = buildCategoryRateLimitKey(channel, category);
        int categoryLimit = getCategoryRateLimit(channel, category);
        
        RateLimiter categoryLimiter = getRateLimiter(categoryKey, categoryLimit);
        return categoryLimiter.tryAcquire(count);
    }
    
    private boolean checkUserRateLimit(String channel, String userId, int count) {
        String userKey = buildUserRateLimitKey(channel, userId);
        int userLimit = getUserRateLimit(channel);
        
        RateLimiter userLimiter = getRateLimiter(userKey, userLimit);
        return userLimiter.tryAcquire(count);
    }
    
    private void consumeGlobalRateLimit(String channel, int count) {
        String globalKey = buildGlobalRateLimitKey(channel);
        RateLimiter globalLimiter = rateLimiters.get(globalKey);
        if (globalLimiter != null) {
            globalLimiter.consume(count);
        }
    }
    
    private void consumeCategoryRateLimit(String channel, String category, int count) {
        String categoryKey = buildCategoryRateLimitKey(channel, category);
        RateLimiter categoryLimiter = rateLimiters.get(categoryKey);
        if (categoryLimiter != null) {
            categoryLimiter.consume(count);
        }
    }
    
    private void consumeUserRateLimit(String channel, String userId, int count) {
        String userKey = buildUserRateLimitKey(channel, userId);
        RateLimiter userLimiter = rateLimiters.get(userKey);
        if (userLimiter != null) {
            userLimiter.consume(count);
        }
    }
    
    private RateLimiter getRateLimiter(String key, int limit) {
        return rateLimiters.computeIfAbsent(key, k -> new RateLimiter(limit, RATE_LIMIT_WINDOW));
    }
    
    private String buildGlobalRateLimitKey(String channel) {
        return "global:" + channel;
    }
    
    private String buildCategoryRateLimitKey(String channel, String category) {
        return "category:" + channel + ":" + category;
    }
    
    private String buildUserRateLimitKey(String channel, String userId) {
        return "user:" + channel + ":" + userId;
    }
    
    private int getGlobalRateLimit(String channel) {
        switch (channel.toLowerCase()) {
            case "email": return GLOBAL_EMAIL_LIMIT_PER_HOUR;
            case "sms": return GLOBAL_SMS_LIMIT_PER_HOUR;
            case "push": return GLOBAL_PUSH_LIMIT_PER_HOUR;
            default: return 1000;
        }
    }
    
    private int getCategoryRateLimit(String channel, String category) {
        if (!"email".equalsIgnoreCase(channel)) {
            return getGlobalRateLimit(channel) / 10; // 10% of global limit
        }
        
        switch (category.toLowerCase()) {
            case "transactional": return TRANSACTIONAL_EMAIL_LIMIT_PER_MINUTE;
            case "marketing": return MARKETING_EMAIL_LIMIT_PER_MINUTE;
            case "system": return SYSTEM_EMAIL_LIMIT_PER_MINUTE;
            default: return 50;
        }
    }
    
    private int getUserRateLimit(String channel) {
        switch (channel.toLowerCase()) {
            case "email": return USER_EMAIL_LIMIT_PER_HOUR;
            case "sms": return USER_SMS_LIMIT_PER_HOUR;
            case "push": return USER_PUSH_LIMIT_PER_HOUR;
            default: return 10;
        }
    }
    
    private String maskUserId(String userId) {
        if (userId == null || userId.length() < 6) return "****";
        return userId.substring(0, 3) + "****" + userId.substring(userId.length() - 3);
    }
    
    // Inner classes
    
    public static class RateLimiter {
        private final int limit;
        private final Duration window;
        private final AtomicInteger usedTokens = new AtomicInteger(0);
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong throttledRequests = new AtomicLong(0);
        
        public RateLimiter(int limit, Duration window) {
            this.limit = limit;
            this.window = window;
        }
        
        public synchronized boolean tryAcquire(int tokens) {
            resetIfNeeded();
            
            if (usedTokens.get() + tokens <= limit) {
                return true;
            } else {
                throttledRequests.incrementAndGet();
                return false;
            }
        }
        
        public synchronized void consume(int tokens) {
            resetIfNeeded();
            usedTokens.addAndGet(tokens);
        }
        
        public int getLimit() {
            return limit;
        }
        
        public int getRemaining() {
            resetIfNeeded();
            return Math.max(0, limit - usedTokens.get());
        }
        
        public int getUsedTokens() {
            resetIfNeeded();
            return usedTokens.get();
        }
        
        public long getThrottledRequests() {
            return throttledRequests.get();
        }
        
        public LocalDateTime getResetTime() {
            return LocalDateTime.now().plus(getRemainingWindowDuration());
        }
        
        private Duration getRemainingWindowDuration() {
            long elapsed = System.currentTimeMillis() - windowStart.get();
            long remaining = window.toMillis() - elapsed;
            return Duration.ofMillis(Math.max(0, remaining));
        }
        
        private void resetIfNeeded() {
            long now = System.currentTimeMillis();
            long currentWindowStart = windowStart.get();
            
            if (now - currentWindowStart >= window.toMillis()) {
                windowStart.set(now);
                usedTokens.set(0);
            }
        }
    }
    
    @lombok.Data
    public static class RateLimitInfo {
        private String channel;
        private String userId;
        private int limit;
        private int remaining;
        private LocalDateTime resetTime;
        private Duration windowDuration;
        
        public boolean isThrottled() {
            return remaining <= 0;
        }
        
        public double getUtilization() {
            return limit > 0 ? (double) (limit - remaining) / limit : 0.0;
        }
    }
    
    @lombok.Data
    public static class RateLimitStats {
        private int activeRateLimiters;
        private long totalRequests;
        private long totalCapacity;
        private double utilizationRate;
        private long throttledRequests;
        
        public double getThrottleRate() {
            return totalRequests > 0 ? (double) throttledRequests / totalRequests : 0.0;
        }
    }
}