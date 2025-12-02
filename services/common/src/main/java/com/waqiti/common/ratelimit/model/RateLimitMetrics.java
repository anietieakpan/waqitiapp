package com.waqiti.common.ratelimit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limit metrics model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitMetrics {
    private String endpoint;
    private AtomicLong totalRequests;
    private AtomicLong allowedRequests;
    private AtomicLong blockedRequests;
    private AtomicLong averageResponseTime;
    private long lastUpdated;
    
    public RateLimitMetrics(String endpoint) {
        this.endpoint = endpoint;
        this.totalRequests = new AtomicLong(0);
        this.allowedRequests = new AtomicLong(0);
        this.blockedRequests = new AtomicLong(0);
        this.averageResponseTime = new AtomicLong(0);
        this.lastUpdated = System.currentTimeMillis();
    }
    
    public void incrementTotal() {
        totalRequests.incrementAndGet();
    }
    
    public void incrementAllowed() {
        allowedRequests.incrementAndGet();
    }
    
    public void incrementBlocked() {
        blockedRequests.incrementAndGet();
    }
    
    public void updateResponseTime(long responseTime) {
        averageResponseTime.set((averageResponseTime.get() + responseTime) / 2);
        lastUpdated = System.currentTimeMillis();
    }
}