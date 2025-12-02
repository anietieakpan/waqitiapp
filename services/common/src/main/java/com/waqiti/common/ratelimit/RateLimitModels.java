package com.waqiti.common.ratelimit;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.AllArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class RateLimitModels {
    
    public enum RateLimitType {
        TOKEN_BUCKET,
        SLIDING_WINDOW,
        FIXED_WINDOW,
        LEAKY_BUCKET
    }
    
    @Data
    @Builder
    public static class TokenBucketConfig {
        private int capacity;
        private int refillTokens;
        private Duration refillPeriod;
        private int initialTokens;
        private int refillRate;
        private long refillPeriodMs;
        
        public int getRefillRate() {
            return refillRate > 0 ? refillRate : refillTokens;
        }
        
        public long getRefillPeriodMs() {
            return refillPeriodMs > 0 ? refillPeriodMs : 
                (refillPeriod != null ? refillPeriod.toMillis() : 1000);
        }
    }
    
    @Data
    @Builder
    public static class SlidingWindowConfig {
        private int limit;
        private Duration windowSize;
        private int precision; // Number of sub-windows
        private int windowSizeSeconds;
        private int maxRequests;
        
        public int getWindowSizeSeconds() {
            return windowSizeSeconds > 0 ? windowSizeSeconds : 
                (windowSize != null ? (int) windowSize.getSeconds() : 60);
        }
        
        public int getMaxRequests() {
            return maxRequests > 0 ? maxRequests : limit;
        }
    }
    
    @Data
    @Builder
    public static class FixedWindowConfig {
        private int limit;
        private Duration windowSize;
        private Instant windowStart;
        private int windowSizeSeconds;
        private int maxRequests;
        
        public int getWindowSizeSeconds() {
            return windowSizeSeconds > 0 ? windowSizeSeconds : 
                (windowSize != null ? (int) windowSize.getSeconds() : 60);
        }
        
        public int getMaxRequests() {
            return maxRequests > 0 ? maxRequests : limit;
        }
    }
    
    @Data
    @Builder
    public static class RateLimitCheck {
        private boolean allowed;
        private long remainingTokens;
        private Duration resetAfter;
        private String reason;
        private String suffix;
        private RateLimitType type;
        private TokenBucketConfig tokenBucketConfig;
        private SlidingWindowConfig slidingWindowConfig;
        private FixedWindowConfig fixedWindowConfig;
        
        public String getSuffix() {
            return suffix != null ? suffix : "";
        }
        
        public RateLimitType getType() {
            return type != null ? type : RateLimitType.TOKEN_BUCKET;
        }
        
        public TokenBucketConfig getTokenBucketConfig() {
            return tokenBucketConfig;
        }
        
        public SlidingWindowConfig getSlidingWindowConfig() {
            return slidingWindowConfig;
        }
        
        public FixedWindowConfig getFixedWindowConfig() {
            return fixedWindowConfig;
        }
    }
    
    @Data
    @Builder
    public static class RateLimitStatus {
        private String key;
        private String identifier;
        private RateLimitType type;
        private boolean allowed;
        private boolean available; // Add available field
        private long limit;
        private long remaining;
        private Instant resetAt;
        private Duration retryAfter;
        private Map<String, Object> metadata;
        
        private String error;
        private long remainingTokens;
        private long currentRequests;
        private Instant lastRefillTime;
        private Instant windowStart;
        
        public static class RateLimitStatusBuilder {
            public RateLimitStatusBuilder identifier(String identifier) {
                this.identifier = identifier;
                return this;
            }
            
            // Custom builder method that sets both available and allowed
            public RateLimitStatusBuilder available(boolean available) {
                this.allowed = available;
                this.available = available;
                return this;
            }
            
            public RateLimitStatusBuilder error(String error) {
                this.error = error;
                return this;
            }
            
            public RateLimitStatusBuilder remainingTokens(long remainingTokens) {
                this.remainingTokens = remainingTokens;
                return this;
            }
            
            public RateLimitStatusBuilder currentRequests(long currentRequests) {
                this.currentRequests = currentRequests;
                return this;
            }
        }
    }
    
    @Data
    @Builder
    public static class RateLimitStats {
        private String key;
        private long totalRequests;
        private long allowedRequests;
        private long deniedRequests;
        private double allowanceRate;
        private Instant periodStart;
        private Instant periodEnd;
        private Map<String, Long> statusBreakdown;
        private long tokenBucketKeys;
        private long fixedWindowKeys; // Add missing field
        
        public static class RateLimitStatsBuilder {
            private long slidingWindowKeys;
            
            // Custom builder method for fixedWindowKeys
            public RateLimitStatsBuilder fixedWindowKeys(long fixedWindowKeys) {
                this.fixedWindowKeys = fixedWindowKeys;
                return this;
            }
            
            public RateLimitStatsBuilder tokenBucketKeys(long tokenBucketKeys) {
                this.tokenBucketKeys = tokenBucketKeys;
                return this;
            }
            
            public RateLimitStatsBuilder slidingWindowKeys(long slidingWindowKeys) {
                this.slidingWindowKeys = slidingWindowKeys;
                return this;
            }
            
            public RateLimitStatsBuilder totalKeys(long totalKeys) {
                // Calculate total keys from individual key counts
                return this;
            }
        }
    }
    
    @Getter
    @AllArgsConstructor
    public static class RateLimitException extends RuntimeException {
        private final RateLimitStatus status;
        
        public RateLimitException(String message, RateLimitStatus status) {
            super(message);
            this.status = status;
        }
    }
}