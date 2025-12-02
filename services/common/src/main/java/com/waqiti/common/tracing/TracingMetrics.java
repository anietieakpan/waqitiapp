package com.waqiti.common.tracing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Collects and exposes metrics for traced operations.
 * Integrates with Micrometer for metrics collection and export.
 */
@Component
@Slf4j
public class TracingMetrics {
    
    private static final String METRIC_PREFIX = "tracing.operation";
    
    private final MeterRegistry meterRegistry;
    private final Map<String, Timer> operationTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> successCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    
    public TracingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("TracingMetrics initialized with MeterRegistry");
    }
    
    // Constructor for when MeterRegistry is not available
    public TracingMetrics() {
        this.meterRegistry = null;
        log.info("TracingMetrics initialized without MeterRegistry (metrics disabled)");
    }
    
    /**
     * Records metrics for a traced operation.
     *
     * @param operationName The name of the operation
     * @param priority The priority of the operation
     * @param success Whether the operation succeeded
     * @param duration The duration of the operation
     */
    public void recordOperation(String operationName, Traced.TracingPriority priority, 
                               boolean success, Duration duration) {
        if (meterRegistry == null) {
            return; // Metrics disabled
        }
        
        try {
            // Record timing
            Timer timer = operationTimers.computeIfAbsent(operationName, name ->
                Timer.builder(METRIC_PREFIX + ".duration")
                    .description("Duration of traced operations")
                    .tag("operation", name)
                    .tag("priority", priority.name())
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .publishPercentileHistogram()
                    .register(meterRegistry)
            );
            timer.record(duration);
            
            // Record success/failure counts
            if (success) {
                Counter successCounter = successCounters.computeIfAbsent(operationName, name ->
                    Counter.builder(METRIC_PREFIX + ".success")
                        .description("Count of successful traced operations")
                        .tag("operation", name)
                        .tag("priority", priority.name())
                        .register(meterRegistry)
                );
                successCounter.increment();
            } else {
                Counter errorCounter = errorCounters.computeIfAbsent(operationName, name ->
                    Counter.builder(METRIC_PREFIX + ".error")
                        .description("Count of failed traced operations")
                        .tag("operation", name)
                        .tag("priority", priority.name())
                        .register(meterRegistry)
                );
                errorCounter.increment();
            }
            
            // Record priority-specific metrics
            recordPriorityMetrics(priority, success, duration);
            
        } catch (Exception e) {
            log.warn("Failed to record metrics for operation {}: {}", operationName, e.getMessage());
        }
    }
    
    /**
     * Records metrics specific to operation priority.
     */
    private void recordPriorityMetrics(Traced.TracingPriority priority, boolean success, Duration duration) {
        if (priority == Traced.TracingPriority.CRITICAL) {
            // Special handling for critical operations
            Counter criticalOpsCounter = Counter.builder(METRIC_PREFIX + ".critical")
                .description("Count of critical operations")
                .tag("status", success ? "success" : "failure")
                .register(meterRegistry);
            criticalOpsCounter.increment();
            
            // Alert if critical operation takes too long
            if (duration.toMillis() > 5000) {
                Counter slowCriticalOpsCounter = Counter.builder(METRIC_PREFIX + ".critical.slow")
                    .description("Count of slow critical operations")
                    .register(meterRegistry);
                slowCriticalOpsCounter.increment();
                
                log.warn("Critical operation took {}ms (threshold: 5000ms)", duration.toMillis());
            }
        }
    }
    
    /**
     * Records a custom metric for a specific operation.
     *
     * @param operationName The name of the operation
     * @param metricName The name of the custom metric
     * @param value The value to record
     */
    public void recordCustomMetric(String operationName, String metricName, double value) {
        if (meterRegistry == null) {
            return;
        }
        
        try {
            meterRegistry.gauge(
                METRIC_PREFIX + ".custom." + metricName,
                value
            );
        } catch (Exception e) {
            log.warn("Failed to record custom metric {} for operation {}: {}", 
                    metricName, operationName, e.getMessage());
        }
    }
    
    /**
     * Gets the total count of successful operations.
     *
     * @param operationName The name of the operation
     * @return The count of successful operations
     */
    public long getSuccessCount(String operationName) {
        Counter counter = successCounters.get(operationName);
        return counter != null ? (long) counter.count() : 0;
    }
    
    /**
     * Gets the total count of failed operations.
     *
     * @param operationName The name of the operation
     * @return The count of failed operations
     */
    public long getErrorCount(String operationName) {
        Counter counter = errorCounters.get(operationName);
        return counter != null ? (long) counter.count() : 0;
    }
    
    /**
     * Gets the average duration of an operation.
     *
     * @param operationName The name of the operation
     * @return The average duration in milliseconds
     */
    public double getAverageDuration(String operationName) {
        Timer timer = operationTimers.get(operationName);
        return timer != null ? timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0;
    }
    
    /**
     * Resets all metrics for testing purposes.
     */
    public void resetMetrics() {
        operationTimers.clear();
        successCounters.clear();
        errorCounters.clear();
        log.info("All tracing metrics have been reset");
    }
}