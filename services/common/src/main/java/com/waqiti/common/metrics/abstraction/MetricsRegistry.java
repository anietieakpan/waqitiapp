package com.waqiti.common.metrics.abstraction;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Production-Grade Metrics Registry
 * Industrial-strength metrics abstraction with cardinality control,
 * error handling, and performance optimization
 */
@Slf4j
@Component
public class MetricsRegistry {
    
    private final MeterRegistry meterRegistry;
    private final MetricsConfiguration configuration;
    private final CardinalityController cardinalityController;
    private final MetricsCircuitBreaker circuitBreaker;
    
    // Metric caches for performance
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final Map<String, DistributionSummary> summaryCache = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gaugeValues = new ConcurrentHashMap<>();
    
    // Metrics about metrics (meta-metrics)
    private final AtomicLong metricsRecorded = new AtomicLong();
    private final AtomicLong metricsDropped = new AtomicLong();
    private final AtomicLong metricsErrors = new AtomicLong();

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();
    
    public MetricsRegistry(MeterRegistry meterRegistry, 
                          MetricsConfiguration configuration,
                          CardinalityController cardinalityController) {
        this.meterRegistry = meterRegistry;
        this.configuration = configuration;
        this.cardinalityController = cardinalityController;
        this.circuitBreaker = new MetricsCircuitBreaker(configuration);
        
        initializeMetaMetrics();
    }
    
    private void initializeMetaMetrics() {
        Gauge.builder("metrics.system.recorded", metricsRecorded, AtomicLong::get)
            .description("Total metrics recorded")
            .register(meterRegistry);
            
        Gauge.builder("metrics.system.dropped", metricsDropped, AtomicLong::get)
            .description("Metrics dropped due to limits")
            .register(meterRegistry);
            
        Gauge.builder("metrics.system.errors", metricsErrors, AtomicLong::get)
            .description("Errors in metrics recording")
            .register(meterRegistry);
            
        Gauge.builder("metrics.system.cardinality", cardinalityController, CardinalityController::getCurrentCardinality)
            .description("Current metric cardinality")
            .register(meterRegistry);
    }
    
    /**
     * Record a counter metric with controlled tags
     */
    public void incrementCounter(MetricDefinition definition, TagSet tags) {
        if (!canRecord(definition, tags)) {
            return;
        }
        
        try {
            String cacheKey = createCacheKey(definition.getName(), tags);
            Counter counter = counterCache.computeIfAbsent(cacheKey, k -> 
                Counter.builder(definition.getName())
                    .description(definition.getDescription())
                    .tags(tags.toMicrometerTags())
                    .register(meterRegistry)
            );
            
            counter.increment();
            metricsRecorded.incrementAndGet();
            
        } catch (Exception e) {
            handleMetricError("counter", definition.getName(), e);
        }
    }
    
    /**
     * Record a counter metric with a specific value
     */
    public void incrementCounter(MetricDefinition definition, TagSet tags, double amount) {
        if (!canRecord(definition, tags)) {
            return;
        }
        
        try {
            String cacheKey = createCacheKey(definition.getName(), tags);
            Counter counter = counterCache.computeIfAbsent(cacheKey, k -> 
                Counter.builder(definition.getName())
                    .description(definition.getDescription())
                    .tags(tags.toMicrometerTags())
                    .register(meterRegistry)
            );
            
            counter.increment(amount);
            metricsRecorded.incrementAndGet();
            
        } catch (Exception e) {
            handleMetricError("counter", definition.getName(), e);
        }
    }
    
    /**
     * Record a timer metric for latency/duration
     */
    public void recordTime(MetricDefinition definition, TagSet tags, Duration duration) {
        if (!canRecord(definition, tags)) {
            return;
        }
        
        try {
            String cacheKey = createCacheKey(definition.getName(), tags);
            Timer timer = timerCache.computeIfAbsent(cacheKey, k ->
                Timer.builder(definition.getName())
                    .description(definition.getDescription())
                    .tags(tags.toMicrometerTags())
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .publishPercentileHistogram()
                    .serviceLevelObjectives(definition.getSlos().toArray(new Duration[0]))
                    .register(meterRegistry)
            );
            
            timer.record(duration);
            metricsRecorded.incrementAndGet();
            
        } catch (Exception e) {
            handleMetricError("timer", definition.getName(), e);
        }
    }
    
    /**
     * Record a timer metric with a lambda
     */
    public <T> T recordTime(MetricDefinition definition, TagSet tags, Supplier<T> operation) {
        if (!canRecord(definition, tags)) {
            return operation.get();
        }
        
        String cacheKey = createCacheKey(definition.getName(), tags);
        Timer timer = timerCache.computeIfAbsent(cacheKey, k ->
            Timer.builder(definition.getName())
                .description(definition.getDescription())
                .tags(tags.toMicrometerTags())
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
        );
        
        return timer.record(operation);
    }
    
    /**
     * Record a distribution summary (for sizes, amounts, etc)
     */
    public void recordDistribution(MetricDefinition definition, TagSet tags, double value) {
        if (!canRecord(definition, tags)) {
            return;
        }
        
        try {
            String cacheKey = createCacheKey(definition.getName(), tags);
            DistributionSummary summary = summaryCache.computeIfAbsent(cacheKey, k ->
                DistributionSummary.builder(definition.getName())
                    .description(definition.getDescription())
                    .tags(tags.toMicrometerTags())
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .scale(definition.getScale())
                    .register(meterRegistry)
            );
            
            summary.record(value);
            metricsRecorded.incrementAndGet();
            
        } catch (Exception e) {
            handleMetricError("distribution", definition.getName(), e);
        }
    }
    
    /**
     * Update a gauge value
     */
    public void updateGauge(MetricDefinition definition, TagSet tags, double value) {
        if (!canRecord(definition, tags)) {
            return;
        }
        
        try {
            String cacheKey = createCacheKey(definition.getName(), tags);
            AtomicLong gaugeValue = gaugeValues.computeIfAbsent(cacheKey, k -> {
                AtomicLong val = new AtomicLong();
                Gauge.builder(definition.getName(), val, AtomicLong::doubleValue)
                    .description(definition.getDescription())
                    .tags(tags.toMicrometerTags())
                    .register(meterRegistry);
                return val;
            });
            
            gaugeValue.set(Double.doubleToLongBits(value));
            metricsRecorded.incrementAndGet();
            
        } catch (Exception e) {
            handleMetricError("gauge", definition.getName(), e);
        }
    }
    
    /**
     * Register a gauge with a supplier
     */
    public <T> void registerGauge(MetricDefinition definition, TagSet tags, T obj, java.util.function.ToDoubleFunction<T> valueFunction) {
        if (!canRecord(definition, tags)) {
            return;
        }
        
        try {
            Gauge.builder(definition.getName(), obj, valueFunction)
                .description(definition.getDescription())
                .tags(tags.toMicrometerTags())
                .register(meterRegistry);
                
            metricsRecorded.incrementAndGet();
            
        } catch (Exception e) {
            handleMetricError("gauge", definition.getName(), e);
        }
    }
    
    /**
     * Check if we can record this metric
     */
    private boolean canRecord(MetricDefinition definition, TagSet tags) {
        // Check circuit breaker
        if (!circuitBreaker.allowRequest()) {
            metricsDropped.incrementAndGet();
            if (log.isDebugEnabled()) {
                log.debug("Metrics circuit breaker open, dropping metric: {}", definition.getName());
            }
            return false;
        }
        
        // Check if metrics are enabled
        if (!configuration.isEnabled()) {
            return false;
        }
        
        // Check specific metric enable/disable
        if (!definition.isEnabled()) {
            return false;
        }
        
        // Check cardinality limits
        if (!cardinalityController.allowMetric(definition.getName(), tags)) {
            metricsDropped.incrementAndGet();
            if (log.isWarnEnabled() && metricsDropped.get() % 1000 == 0) {
                log.warn("Cardinality limit exceeded, dropped {} metrics so far", metricsDropped.get());
            }
            return false;
        }
        
        // Check sampling rate
        if (definition.getSampleRate() < 1.0) {
            // SECURITY FIX: Use SecureRandom instead of Math.random()
            if (secureRandom.nextDouble() > definition.getSampleRate()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Create cache key for metric
     */
    private String createCacheKey(String name, TagSet tags) {
        return name + ":" + tags.toCacheKey();
    }
    
    /**
     * Handle metric recording errors
     */
    private void handleMetricError(String type, String name, Exception e) {
        metricsErrors.incrementAndGet();
        circuitBreaker.recordFailure();
        
        if (log.isErrorEnabled() && metricsErrors.get() % 100 == 0) {
            log.error("Error recording {} metric '{}', {} errors total", type, name, metricsErrors.get(), e);
        }
    }
    
    /**
     * Get current metrics statistics
     */
    public MetricsStats getStats() {
        return MetricsStats.builder()
            .totalRecorded(metricsRecorded.get())
            .totalDropped(metricsDropped.get())
            .totalErrors(metricsErrors.get())
            .currentCardinality(cardinalityController.getCurrentCardinality())
            .maxCardinality(configuration.getMaxCardinality())
            .circuitBreakerOpen(!circuitBreaker.allowRequest())
            .cacheSize(counterCache.size() + timerCache.size() + summaryCache.size())
            .build();
    }
    
    /**
     * Clear metric caches (for testing or emergency)
     */
    public void clearCaches() {
        log.warn("Clearing all metric caches");
        counterCache.clear();
        timerCache.clear();
        summaryCache.clear();
        gaugeValues.clear();
        cardinalityController.reset();
    }
}