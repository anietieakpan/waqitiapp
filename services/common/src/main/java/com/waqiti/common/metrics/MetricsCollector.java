package com.waqiti.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central metrics collection service for performance monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> gaugeValues = new ConcurrentHashMap<>();
    
    /**
     * Record cache hit
     */
    public void recordCacheHit(String cacheName) {
        Counter.builder("cache.hit")
                .tag("cache", cacheName)
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Record cache miss
     */
    public void recordCacheMiss(String cacheName) {
        Counter.builder("cache.miss")
                .tag("cache", cacheName)
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Record cache eviction
     */
    public void recordCacheEviction(String cacheName) {
        Counter.builder("cache.eviction")
                .tag("cache", cacheName)
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Record cache size
     */
    public void recordCacheSize(String cacheName, long size) {
        String key = "cache.size." + cacheName;
        gaugeValues.computeIfAbsent(key, k -> {
            AtomicLong atomicLong = new AtomicLong(size);
            Gauge.builder("cache.size", atomicLong, AtomicLong::get)
                    .tag("cache", cacheName)
                    .register(meterRegistry);
            return atomicLong;
        }).set(size);
    }
    
    /**
     * Record database query execution time
     */
    public void recordQueryTime(String queryType, Duration duration) {
        Timer.builder("database.query.time")
                .tag("query_type", queryType)
                .register(meterRegistry)
                .record(duration);
    }
    
    /**
     * Record database connection pool metrics
     */
    public void recordConnectionPoolMetrics(String poolName, int active, int idle, int total) {
        recordGauge("database.connection.active", poolName, active);
        recordGauge("database.connection.idle", poolName, idle);
        recordGauge("database.connection.total", poolName, total);
    }
    
    /**
     * Record API endpoint metrics
     */
    public void recordApiCall(String endpoint, String method, String status, Duration duration) {
        Timer.builder("api.request.duration")
                .tag("endpoint", endpoint)
                .tag("method", method)
                .tag("status", status)
                .register(meterRegistry)
                .record(duration);
                
        Counter.builder("api.request.count")
                .tag("endpoint", endpoint)
                .tag("method", method)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Record financial transaction metrics
     */
    public void recordFinancialTransaction(String transactionType, String status, double amount) {
        Counter.builder("financial.transaction.count")
                .tag("type", transactionType)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
                
        Timer.builder("financial.transaction.amount")
                .tag("type", transactionType)
                .tag("status", status)
                .register(meterRegistry)
                .record(Duration.ofMillis((long) amount)); // Using amount as milliseconds for timer
    }
    
    /**
     * Record security event
     */
    public void recordSecurityEvent(String eventType, String severity) {
        Counter.builder("security.event")
                .tag("type", eventType)
                .tag("severity", severity)
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Record rate limiting metrics
     */
    public void recordRateLimit(String endpoint, boolean allowed) {
        Counter.builder("rate.limit")
                .tag("endpoint", endpoint)
                .tag("allowed", String.valueOf(allowed))
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Record gauge value with metric name and tag
     */
    public void recordGauge(String metricName, String tag, Number value) {
        String key = metricName + "." + tag;
        gaugeValues.computeIfAbsent(key, k -> {
            AtomicLong atomicLong = new AtomicLong(value.longValue());
            Gauge.builder(metricName, atomicLong, AtomicLong::get)
                    .tag("name", tag)
                    .register(meterRegistry);
            return atomicLong;
        }).set(value.longValue());
    }
    
    /**
     * Increment counter
     */
    public void incrementCounter(String metricName) {
        Counter.builder(metricName)
                .register(meterRegistry)
                .increment();
    }
    
    /**
     * Increment counter with tags
     */
    public void incrementCounter(String metricName, Map<String, String> tags) {
        Counter.Builder builder = Counter.builder(metricName);
        tags.forEach(builder::tag);
        builder.register(meterRegistry).increment();
    }
    
    /**
     * Increment counter by value
     */
    public void incrementCounter(String metricName, int value) {
        Counter.builder(metricName)
                .register(meterRegistry)
                .increment(value);
    }
    
    /**
     * Record timer
     */
    public void recordTimer(String metricName, long millis) {
        Timer.builder(metricName)
                .register(meterRegistry)
                .record(Duration.ofMillis(millis));
    }

    public void recordHistogram(String s, long accountAgeDays) {

//        TODO - properly implement with business logic, production-ready code, etc. added by aniix october, 28th 2025
    }
}