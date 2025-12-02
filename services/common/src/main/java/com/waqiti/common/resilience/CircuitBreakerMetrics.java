package com.waqiti.common.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Custom metrics for circuit breaker monitoring and analysis
 */
@Data
public class CircuitBreakerMetrics {
    
    // Basic counters
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong callsNotPermitted = new AtomicLong(0);
    
    // State transition counters
    private final AtomicLong openEvents = new AtomicLong(0);
    private final AtomicLong halfOpenEvents = new AtomicLong(0);
    private final AtomicLong recoveryEvents = new AtomicLong(0);
    
    // Timing information
    private LocalDateTime lastFailureTime;
    private LocalDateTime lastSuccessTime;
    private LocalDateTime lastStateTransitionTime;
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // Failure analysis
    private String lastFailureType;
    private String lastFailureMessage;
    
    // Performance metrics
    private long totalResponseTime = 0;
    private long slowCallCount = 0;
    
    public void incrementSuccessCount() {
        successCount.incrementAndGet();
        lastSuccessTime = LocalDateTime.now();
    }
    
    public void incrementFailureCount() {
        failureCount.incrementAndGet();
        lastFailureTime = LocalDateTime.now();
    }
    
    public void incrementCallsNotPermitted() {
        callsNotPermitted.incrementAndGet();
    }
    
    public void incrementOpenEvents() {
        openEvents.incrementAndGet();
        recordStateTransition();
    }
    
    public void incrementHalfOpenEvents() {
        halfOpenEvents.incrementAndGet();
        recordStateTransition();
    }
    
    public void incrementRecoveryEvents() {
        recoveryEvents.incrementAndGet();
        recordStateTransition();
    }
    
    public void recordStateTransition(CircuitBreaker.State fromState, CircuitBreaker.State toState) {
        recordStateTransition();
    }
    
    private void recordStateTransition() {
        lastStateTransitionTime = LocalDateTime.now();
    }
    
    public void recordLastFailure(Throwable throwable) {
        lastFailureType = throwable.getClass().getSimpleName();
        lastFailureMessage = throwable.getMessage();
        lastFailureTime = LocalDateTime.now();
    }
    
    public void recordResponseTime(long responseTime) {
        totalResponseTime += responseTime;
    }
    
    public void incrementSlowCallCount() {
        slowCallCount++;
    }
    
    // Calculated metrics
    
    public double getSuccessRate() {
        long total = getTotalCalls();
        return total > 0 ? (double) successCount.get() / total * 100 : 0;
    }
    
    public double getFailureRate() {
        long total = getTotalCalls();
        return total > 0 ? (double) failureCount.get() / total * 100 : 0;
    }
    
    public long getTotalCalls() {
        return successCount.get() + failureCount.get();
    }
    
    public double getAverageResponseTime() {
        long total = getTotalCalls();
        return total > 0 ? (double) totalResponseTime / total : 0;
    }
    
    public double getSlowCallRate() {
        long total = getTotalCalls();
        return total > 0 ? (double) slowCallCount / total * 100 : 0;
    }
    
    public boolean isHealthy() {
        return getFailureRate() < 10 && slowCallCount < getTotalCalls() * 0.2;
    }
    
    public String getHealthStatus() {
        if (isHealthy()) {
            return "HEALTHY";
        } else if (getFailureRate() > 50) {
            return "CRITICAL";
        } else if (getFailureRate() > 25) {
            return "WARNING";
        } else {
            return "DEGRADED";
        }
    }
}