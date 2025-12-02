package com.waqiti.notification.tracker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter for controlling notification sending rates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimiter {
    
    private String limiterId;
    private String scope; // user, global, provider, channel
    private String scopeId; // userId, providerId, etc.
    
    // Rate limit configuration
    private int maxRequestsPerSecond;
    private int maxRequestsPerMinute;
    private int maxRequestsPerHour;
    private int maxRequestsPerDay;
    
    // Current counters
    private AtomicInteger currentSecondCount;
    private AtomicInteger currentMinuteCount;
    private AtomicInteger currentHourCount;
    private AtomicInteger currentDayCount;
    
    // Timestamps
    private AtomicLong lastSecondReset;
    private AtomicLong lastMinuteReset;
    private AtomicLong lastHourReset;
    private AtomicLong lastDayReset;
    
    // Burst configuration
    private int burstSize;
    private int currentBurstTokens;
    private LocalDateTime lastTokenRefill;
    
    // Throttling
    private boolean throttled;
    private LocalDateTime throttledUntil;
    private String throttleReason;
    
    // Statistics
    private long totalRequests;
    private long totalThrottled;
    private long totalAllowed;
    private double averageRequestRate;
    
    // Metadata
    private Map<String, Object> metadata;
    
    /**
     * Check if a request is allowed
     */
    public boolean isAllowed() {
        // This would contain the actual rate limiting logic
        return !throttled && currentSecondCount.get() < maxRequestsPerSecond;
    }
    
    /**
     * Record a request
     */
    public void recordRequest() {
        currentSecondCount.incrementAndGet();
        currentMinuteCount.incrementAndGet();
        currentHourCount.incrementAndGet();
        currentDayCount.incrementAndGet();
        totalRequests++;
    }
    
    /**
     * Reset counters based on time windows
     */
    public void resetCounters(LocalDateTime now) {
        // Reset logic would go here
    }
}