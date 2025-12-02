package com.waqiti.common.integration.model;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service metrics collector for integration monitoring
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceMetricsCollector {
    
    private final Map<String, ServiceMetrics> serviceMetricsMap = new ConcurrentHashMap<>();
    
    /**
     * Record a service call
     */
    public void recordServiceCall(String serviceName, String operation, Duration responseTime, boolean success) {
        ServiceMetrics metrics = serviceMetricsMap.computeIfAbsent(serviceName, k -> new ServiceMetrics());

        metrics.totalCalls.incrementAndGet();
        if (success) {
            metrics.successfulCalls.incrementAndGet();
        } else {
            metrics.failedCalls.incrementAndGet();
        }

        metrics.totalResponseTime.addAndGet(responseTime.toMillis());
        metrics.lastCallTime = Instant.now();

        // Update operation-specific metrics
        metrics.operationMetrics.computeIfAbsent(operation, k -> new OperationMetrics())
            .recordCall(responseTime, success);
    }

    /**
     * Record a successful service call
     */
    public void recordSuccess(String serviceName, String operation, long executionTimeMs) {
        recordServiceCall(serviceName, operation, Duration.ofMillis(executionTimeMs), true);
    }

    /**
     * Record a failed service call
     */
    public void recordFailure(String serviceName, String operation, long executionTimeMs, Exception e) {
        recordServiceCall(serviceName, operation, Duration.ofMillis(executionTimeMs), false);
        log.warn("Service call failed: {} - {}: {}", serviceName, operation, e.getMessage());
    }
    
    /**
     * Get metrics for a service
     */
    public ServiceMetrics getMetrics(String serviceName) {
        return serviceMetricsMap.get(serviceName);
    }
    
    /**
     * Get all service metrics
     */
    public Map<String, ServiceMetrics> getAllMetrics() {
        return new ConcurrentHashMap<>(serviceMetricsMap);
    }
    
    /**
     * Reset metrics for a service
     */
    public void resetMetrics(String serviceName) {
        serviceMetricsMap.remove(serviceName);
    }
    
    /**
     * Service metrics data structure
     */
    public static class ServiceMetrics {
        public final AtomicLong totalCalls = new AtomicLong(0);
        public final AtomicLong successfulCalls = new AtomicLong(0);
        public final AtomicLong failedCalls = new AtomicLong(0);
        public final AtomicLong totalResponseTime = new AtomicLong(0);
        public Instant lastCallTime;
        public final Map<String, OperationMetrics> operationMetrics = new ConcurrentHashMap<>();
        
        public double getSuccessRate() {
            long total = totalCalls.get();
            return total > 0 ? (double) successfulCalls.get() / total * 100 : 0.0;
        }
        
        public double getAverageResponseTime() {
            long total = totalCalls.get();
            return total > 0 ? (double) totalResponseTime.get() / total : 0.0;
        }
    }
    
    /**
     * Operation-specific metrics
     */
    public static class OperationMetrics {
        public final AtomicLong calls = new AtomicLong(0);
        public final AtomicLong successes = new AtomicLong(0);
        public final AtomicLong failures = new AtomicLong(0);
        public final AtomicLong totalTime = new AtomicLong(0);
        
        public void recordCall(Duration responseTime, boolean success) {
            calls.incrementAndGet();
            if (success) {
                successes.incrementAndGet();
            } else {
                failures.incrementAndGet();
            }
            totalTime.addAndGet(responseTime.toMillis());
        }
        
        public double getSuccessRate() {
            long total = calls.get();
            return total > 0 ? (double) successes.get() / total * 100 : 0.0;
        }
        
        public double getAverageTime() {
            long total = calls.get();
            return total > 0 ? (double) totalTime.get() / total : 0.0;
        }
    }
}