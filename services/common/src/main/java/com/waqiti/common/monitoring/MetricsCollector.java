package com.waqiti.common.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.google.common.util.concurrent.AtomicDouble;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized metrics collection service for monitoring application performance and business metrics.
 * Provides standardized metric collection patterns across the application.
 */
@Slf4j
@Component
public class MetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    private final Map<String, AtomicDouble> gaugeValues = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> longGaugeValues = new ConcurrentHashMap<>();
    
    public MetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Increment a counter metric
     */
    public void incrementCounter(String name, String... tags) {
        getCounter(name, tags).increment();
    }
    
    /**
     * Increment a counter by a specific amount
     */
    public void incrementCounter(String name, double amount, String... tags) {
        getCounter(name, tags).increment(amount);
    }
    
    /**
     * Record a timer measurement
     */
    public void recordTimer(String name, Duration duration, String... tags) {
        getTimer(name, tags).record(duration);
    }
    
    /**
     * Record a timer measurement in milliseconds
     */
    public void recordTimer(String name, long milliseconds, String... tags) {
        getTimer(name, tags).record(Duration.ofMillis(milliseconds));
    }
    
    /**
     * Time a operation and record the duration
     */
    public Timer.Sample startTimer(String name, String... tags) {
        return Timer.start(meterRegistry);
    }
    
    /**
     * Stop timer and record
     */
    public void stopTimer(Timer.Sample sample, String name, String... tags) {
        sample.stop(getTimer(name, tags));
    }
    
    /**
     * Set a gauge value
     */
    public void setGauge(String name, double value, String... tags) {
        String key = createKey(name, tags);
        AtomicDouble gaugeValue = gaugeValues.computeIfAbsent(key, k -> {
            AtomicDouble atomicValue = new AtomicDouble(value);
            Gauge.builder(name, atomicValue, AtomicDouble::get)
                    .tags(createTagArray(tags))
                    .register(meterRegistry);
            return atomicValue;
        });
        gaugeValue.set(value);
    }
    
    /**
     * Set a long gauge value
     */
    public void setLongGauge(String name, long value, String... tags) {
        String key = createKey(name, tags);
        AtomicLong gaugeValue = longGaugeValues.computeIfAbsent(key, k -> {
            AtomicLong atomicValue = new AtomicLong(value);
            Gauge.builder(name, atomicValue, AtomicLong::doubleValue)
                    .tags(createTagArray(tags))
                    .register(meterRegistry);
            return atomicValue;
        });
        gaugeValue.set(value);
    }
    
    /**
     * Record business metrics
     */
    public void recordBusinessMetric(String metricName, double value, String... tags) {
        String name = "business." + metricName;
        incrementCounter(name + ".count", tags);
        setGauge(name + ".value", value, tags);
    }
    
    /**
     * Record API metrics
     */
    public void recordApiCall(String endpoint, String method, String status, long durationMs) {
        String[] tags = {"endpoint", endpoint, "method", method, "status", status};
        incrementCounter("api.requests.total", tags);
        recordTimer("api.request.duration", durationMs, tags);
    }
    
    /**
     * Record database metrics
     */
    public void recordDatabaseOperation(String operation, String table, long durationMs, boolean success) {
        String[] tags = {"operation", operation, "table", table, "success", String.valueOf(success)};
        incrementCounter("database.operations.total", tags);
        recordTimer("database.operation.duration", durationMs, tags);
    }
    
    /**
     * Record cache metrics
     */
    public void recordCacheOperation(String operation, String cacheName, boolean hit) {
        String[] tags = {"operation", operation, "cache", cacheName, "result", hit ? "hit" : "miss"};
        incrementCounter("cache.operations.total", tags);
        
        if ("get".equals(operation)) {
            incrementCounter("cache.hits.total", hit ? 1 : 0, "cache", cacheName);
            incrementCounter("cache.misses.total", hit ? 0 : 1, "cache", cacheName);
        }
    }
    
    /**
     * Record security metrics
     */
    public void recordSecurityEvent(String eventType, String severity, String userId) {
        String[] tags = {"event_type", eventType, "severity", severity, "user_id", userId};
        incrementCounter("security.events.total", tags);
    }
    
    /**
     * Record payment metrics
     */
    public void recordPaymentTransaction(String type, String status, double amount, String currency) {
        String[] tags = {"type", type, "status", status, "currency", currency};
        incrementCounter("payment.transactions.total", tags);
        recordBusinessMetric("payment.amount", amount, tags);
    }
    
    /**
     * Record fraud detection metrics
     */
    public void recordFraudCheck(String checkType, boolean flagged, double riskScore) {
        String[] tags = {"check_type", checkType, "flagged", String.valueOf(flagged)};
        incrementCounter("fraud.checks.total", tags);
        setGauge("fraud.risk_score", riskScore, tags);
    }
    
    /**
     * Record validation metrics
     */
    public void recordValidation(String validationType, boolean passed, long durationMs) {
        String[] tags = {"validation_type", validationType, "result", passed ? "pass" : "fail"};
        incrementCounter("validation.checks.total", tags);
        recordTimer("validation.duration", durationMs, tags);
    }
    
    /**
     * Record external service call metrics
     */
    public void recordExternalServiceCall(String serviceName, String operation, String status, long durationMs) {
        String[] tags = {"service", serviceName, "operation", operation, "status", status};
        incrementCounter("external.service.calls.total", tags);
        recordTimer("external.service.call.duration", durationMs, tags);
    }
    
    /**
     * Get or create a counter
     */
    private Counter getCounter(String name, String... tags) {
        String key = createKey(name, tags);
        return counters.computeIfAbsent(key, k -> 
                Counter.builder(name)
                        .tags(createTagArray(tags))
                        .register(meterRegistry));
    }
    
    /**
     * Get or create a timer
     */
    private Timer getTimer(String name, String... tags) {
        String key = createKey(name, tags);
        return timers.computeIfAbsent(key, k -> 
                Timer.builder(name)
                        .tags(createTagArray(tags))
                        .register(meterRegistry));
    }
    
    /**
     * Create a unique key for metric caching
     */
    private String createKey(String name, String... tags) {
        StringBuilder key = new StringBuilder(name);
        for (int i = 0; i < tags.length; i += 2) {
            if (i + 1 < tags.length) {
                key.append(":").append(tags[i]).append("=").append(tags[i + 1]);
            }
        }
        return key.toString();
    }
    
    /**
     * Convert tag varargs to tag array for Micrometer
     */
    private String[] createTagArray(String... tags) {
        if (tags.length % 2 != 0) {
            throw new IllegalArgumentException("Tags must be provided in key-value pairs");
        }
        return tags;
    }
    
    /**
     * Get current counter value
     */
    public double getCounterValue(String name, String... tags) {
        return getCounter(name, tags).count();
    }
    
    /**
     * Get current gauge value
     */
    public Double getGaugeValue(String name, String... tags) {
        String key = createKey(name, tags);
        AtomicDouble gauge = gaugeValues.get(key);
        return gauge != null ? gauge.get() : null;
    }
    
    /**
     * Clear all cached metrics (useful for testing)
     */
    public void clearCache() {
        counters.clear();
        timers.clear();
        gaugeValues.clear();
        longGaugeValues.clear();
    }
}