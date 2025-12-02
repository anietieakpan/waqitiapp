package com.waqiti.audit.model;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Performance metrics for audit event types
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMetrics {
    
    @Builder.Default
    private AtomicLong totalEvents = new AtomicLong(0);
    
    @Builder.Default
    private AtomicLong totalProcessingTime = new AtomicLong(0);
    
    @Builder.Default
    private AtomicReference<Long> lastEventTime = new AtomicReference<>(0L);
    
    @Builder.Default
    private AtomicReference<Double> averageProcessingTime = new AtomicReference<>(0.0);
    
    @Builder.Default
    private AtomicLong minProcessingTime = new AtomicLong(Long.MAX_VALUE);
    
    @Builder.Default
    private AtomicLong maxProcessingTime = new AtomicLong(0L);
    
    /**
     * Update metrics with new processing time
     */
    public void updateMetrics(long processingTime) {
        long events = totalEvents.incrementAndGet();
        long totalTime = totalProcessingTime.addAndGet(processingTime);
        
        // Update average
        averageProcessingTime.set((double) totalTime / events);
        
        // Update min/max
        minProcessingTime.updateAndGet(current -> Math.min(current, processingTime));
        maxProcessingTime.updateAndGet(current -> Math.max(current, processingTime));
        
        // Update last event time
        lastEventTime.set(System.currentTimeMillis());
    }
    
    /**
     * Get throughput (events per second)
     */
    public double getThroughput() {
        double avgTime = averageProcessingTime.get();
        return avgTime > 0 ? 1000.0 / avgTime : 0.0;
    }
}