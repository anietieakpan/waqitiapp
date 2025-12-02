package com.waqiti.common.metrics.service;

import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Production-grade metrics service with comprehensive business and technical metrics
 * Provides thread-safe, high-performance metrics collection with meter caching
 * and structured tagging for observability and monitoring systems.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductionMetricsService implements MetricsService {

    private final MeterRegistry meterRegistry;
    
    @Value("${metrics.service-name:waqiti-service}")
    private String serviceName;
    
    @Value("${metrics.enabled:true}")
    private boolean metricsEnabled;
    
    @Value("${metrics.cache-size:10000}")
    private int meterCacheSize;
    
    // Thread-safe meter caches for performance optimization
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Gauge> gaugeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> summaryCache = new ConcurrentHashMap<>();
    
    // Business metrics state tracking
    private final AtomicLong activeTransactions = new AtomicLong(0);
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    /**
     * Increment counter with tags map - primary interface for most services
     */
    @Override
    public void incrementCounter(String name, Map<String, String> tags) {
        if (!metricsEnabled) return;
        
        try {
            String[] tagArray = mapToArray(tags);
            getCachedCounter(name, tagArray).increment();
            
        } catch (Exception e) {
            log.error("Failed to increment counter {}: {}", name, e.getMessage());
        }
    }

    /**
     * Increment counter by specific amount with tags
     */
    @Override
    public void incrementCounter(String name, double amount, Map<String, String> tags) {
        if (!metricsEnabled) return;
        
        try {
            String[] tagArray = mapToArray(tags);
            getCachedCounter(name, tagArray).increment(amount);
            
        } catch (Exception e) {
            log.error("Failed to increment counter {} by {}: {}", name, amount, e.getMessage());
        }
    }

    /**
     * Record timer with millisecond duration and tags
     */
    @Override
    public void recordTimer(String name, long durationMs, Map<String, String> tags) {
        if (!metricsEnabled) return;
        
        try {
            String[] tagArray = mapToArray(tags);
            getCachedTimer(name, tagArray).record(durationMs, TimeUnit.MILLISECONDS);
            
        } catch (Exception e) {
            log.error("Failed to record timer {} with duration {}ms: {}", name, durationMs, e.getMessage());
        }
    }

    /**
     * Record timer with Duration and tags
     */
    @Override
    public void recordTimer(String name, Duration duration, Map<String, String> tags) {
        if (!metricsEnabled) return;
        
        try {
            String[] tagArray = mapToArray(tags);
            getCachedTimer(name, tagArray).record(duration);
            
        } catch (Exception e) {
            log.error("Failed to record timer {} with duration {}: {}", name, duration, e.getMessage());
        }
    }

    /**
     * Record distribution summary (e.g., amounts, sizes)
     */
    @Override
    public void recordDistribution(String name, double value, Map<String, String> tags) {
        if (!metricsEnabled) return;
        
        try {
            String[] tagArray = mapToArray(tags);
            getCachedDistributionSummary(name, tagArray).record(value);
            
        } catch (Exception e) {
            log.error("Failed to record distribution {} with value {}: {}", name, value, e.getMessage());
        }
    }

    /**
     * Register gauge with supplier function
     */
    @Override
    public void registerGauge(String name, Supplier<Number> valueSupplier, Map<String, String> tags) {
        if (!metricsEnabled) return;
        
        try {
            String[] tagArray = mapToArray(tags);
            String cacheKey = buildCacheKey(name, tagArray);
            
            gaugeCache.computeIfAbsent(cacheKey, k -> 
                Gauge.builder(name, valueSupplier)
                    .tags(tagArray)
                    .register(meterRegistry)
            );
            
        } catch (Exception e) {
            log.error("Failed to register gauge {}: {}", name, e.getMessage());
        }
    }

    /**
     * Record business transaction metrics with comprehensive tracking
     */
    @Override
    public void recordTransactionMetrics(TransactionMetrics transaction) {
        if (!metricsEnabled) return;
        
        try {
            Map<String, String> baseTags = Map.of(
                "type", transaction.getTransactionType(),
                "status", transaction.getStatus().name(),
                "currency", transaction.getCurrency(),
                "service", serviceName
            );
            
            // Transaction counter
            incrementCounter("business.transactions.total", baseTags);
            
            // Transaction amount distribution
            recordDistribution("transaction.amount", transaction.getAmount().doubleValue(), baseTags);
            
            // Processing time
            if (transaction.getProcessingTime() != null) {
                recordTimer("transaction.processing.time", transaction.getProcessingTime(), baseTags);
            }
            
            // Update active transaction gauge
            updateActiveTransactionCount(transaction.getStatus());
            
            totalOperations.incrementAndGet();
            
            log.debug("Recorded transaction metrics for {} {} {}",
                transaction.getTransactionType(), transaction.getAmount(), transaction.getCurrency());
            
        } catch (Exception e) {
            log.error("Failed to record transaction metrics: {}", e.getMessage());
            failedOperations.incrementAndGet();
        }
    }

    /**
     * Record KYC verification metrics
     */
    @Override
    public void recordKycMetrics(KycMetrics kyc) {
        if (!metricsEnabled) return;
        
        try {
            Map<String, String> baseTags = Map.of(
                "provider", kyc.getProvider(),
                "status", kyc.getStatus(),
                "level", kyc.getVerificationLevel(),
                "service", serviceName
            );
            
            incrementCounter("kyc.verifications.total", baseTags);
            
            if (kyc.getProcessingTime() != null) {
                recordTimer("kyc.processing.time", kyc.getProcessingTime(), baseTags);
            }
            
            if (kyc.getConfidenceScore() > 0) {
                recordDistribution("kyc.confidence.score", kyc.getConfidenceScore(), 
                    Map.of("provider", kyc.getProvider(), "service", serviceName));
            }
            
        } catch (Exception e) {
            log.error("Failed to record KYC metrics: {}", e.getMessage());
        }
    }

    /**
     * Record fraud detection metrics
     */
    @Override
    public void recordFraudMetrics(FraudMetrics fraud) {
        if (!metricsEnabled) return;
        
        try {
            Map<String, String> baseTags = Map.of(
                "result", fraud.getResult(),
                "risk_level", fraud.getRiskLevel(),
                "service", serviceName
            );
            
            incrementCounter("fraud.checks.total", baseTags);
            recordDistribution("fraud.risk.score", fraud.getRiskScore(), 
                Map.of("service", serviceName));
            
            if (fraud.getProcessingTime() != null) {
                recordTimer("fraud.check.time", fraud.getProcessingTime(), baseTags);
            }
            
            if ("BLOCKED".equals(fraud.getResult())) {
                incrementCounter("transactions.blocked.total",
                    Map.of("reason", fraud.getBlockedReason(), "service", serviceName));
            }
            
        } catch (Exception e) {
            log.error("Failed to record fraud metrics: {}", e.getMessage());
        }
    }

    /**
     * Record API endpoint metrics
     */
    @Override
    public void recordEndpointMetrics(String endpoint, String method, int statusCode, Duration duration) {
        if (!metricsEnabled) return;
        
        try {
            Map<String, String> baseTags = Map.of(
                "endpoint", endpoint,
                "method", method,
                "status", String.valueOf(statusCode),
                "service", serviceName
            );
            
            incrementCounter("api.requests.total", baseTags);
            recordTimer("api.response.time", duration, baseTags);
            
            if (statusCode >= 400) {
                incrementCounter("api.errors.total", baseTags);
            }
            
        } catch (Exception e) {
            log.error("Failed to record endpoint metrics for {}: {}", endpoint, e.getMessage());
        }
    }

    /**
     * Record external service call metrics
     */
    @Override
    public void recordExternalServiceMetrics(ExternalServiceMetrics serviceMetrics) {
        if (!metricsEnabled) return;
        
        try {
            Map<String, String> baseTags = Map.of(
                "service", serviceMetrics.getServiceName(),
                "operation", serviceMetrics.getOperation(),
                "status", serviceMetrics.getStatus(),
                "response_code", String.valueOf(serviceMetrics.getResponseCode())
            );
            
            incrementCounter("external.service.calls.total", baseTags);
            
            if (serviceMetrics.getResponseTime() != null) {
                recordTimer("external.service.response.time", serviceMetrics.getResponseTime(), baseTags);
            }
            
            if (serviceMetrics.isCircuitBreakerTriggered()) {
                incrementCounter("circuit.breaker.triggered.total",
                    Map.of("service", serviceMetrics.getServiceName(), "operation", serviceMetrics.getOperation()));
            }
            
            if (serviceMetrics.getRetryCount() > 0) {
                incrementCounter("service.retries.total", serviceMetrics.getRetryCount(),
                    Map.of("service", serviceMetrics.getServiceName(), "operation", serviceMetrics.getOperation()));
            }
            
        } catch (Exception e) {
            log.error("Failed to record external service metrics: {}", e.getMessage());
        }
    }

    /**
     * Record failed operation with reason
     */
    @Override
    public void recordFailedOperation(String operation, String reason) {
        if (!metricsEnabled) return;
        
        try {
            failedOperations.incrementAndGet();
            incrementCounter("operations.failed.total",
                Map.of("operation", operation, "reason", reason, "service", serviceName));
            
        } catch (Exception e) {
            log.error("Failed to record failed operation {}: {}", operation, e.getMessage());
        }
    }

    /**
     * Record successful operation with duration
     */
    @Override
    public void recordSuccessfulOperation(String operation, Duration duration) {
        if (!metricsEnabled) return;
        
        try {
            Map<String, String> tags = Map.of("operation", operation, "service", serviceName);
            incrementCounter("operations.successful.total", tags);
            recordTimer("operation.duration", duration, tags);
            
        } catch (Exception e) {
            log.error("Failed to record successful operation {}: {}", operation, e.getMessage());
        }
    }

    /**
     * Initialize default business gauges
     */
    public void initializeBusinessGauges() {
        if (!metricsEnabled) return;
        
        try {
            registerGauge("active.transactions.count", activeTransactions::get, 
                Map.of("service", serviceName));
            registerGauge("total.operations.count", totalOperations::get, 
                Map.of("service", serviceName));
            registerGauge("failed.operations.count", failedOperations::get, 
                Map.of("service", serviceName));
            registerGauge("meter.cache.hits", cacheHits::get, 
                Map.of("service", serviceName));
            registerGauge("meter.cache.misses", cacheMisses::get, 
                Map.of("service", serviceName));
            registerGauge("jvm.memory.used.bytes", 
                () -> Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                Map.of("service", serviceName));
            
        } catch (Exception e) {
            log.error("Failed to initialize business gauges: {}", e.getMessage());
        }
    }

    /**
     * Get current metrics summary
     */
    @Override
    public MetricsSummary getMetricsSummary() {
        return MetricsSummary.builder()
            .enabled(metricsEnabled)
            .serviceName(serviceName)
            .activeTransactions(activeTransactions.get())
            .totalOperations(totalOperations.get())
            .failedOperations(failedOperations.get())
            .countersRegistered(counterCache.size())
            .timersRegistered(timerCache.size())
            .gaugesRegistered(gaugeCache.size())
            .summariesRegistered(summaryCache.size())
            .cacheHitRate(calculateCacheHitRate())
            .lastUpdated(Instant.now())
            .build();
    }

    // Private helper methods

    private Counter getCachedCounter(String name, String... tags) {
        String cacheKey = buildCacheKey(name, tags);
        
        Counter counter = counterCache.get(cacheKey);
        if (counter != null) {
            cacheHits.incrementAndGet();
            return counter;
        }
        
        cacheMisses.incrementAndGet();
        
        // Create and cache new counter
        counter = Counter.builder(name)
            .tags(tags)
            .register(meterRegistry);
            
        // Implement cache size limit
        if (counterCache.size() < meterCacheSize) {
            counterCache.put(cacheKey, counter);
        }
        
        return counter;
    }

    private Timer getCachedTimer(String name, String... tags) {
        String cacheKey = buildCacheKey(name, tags);
        
        Timer timer = timerCache.get(cacheKey);
        if (timer != null) {
            cacheHits.incrementAndGet();
            return timer;
        }
        
        cacheMisses.incrementAndGet();
        
        timer = Timer.builder(name)
            .tags(tags)
            .register(meterRegistry);
            
        if (timerCache.size() < meterCacheSize) {
            timerCache.put(cacheKey, timer);
        }
        
        return timer;
    }

    private DistributionSummary getCachedDistributionSummary(String name, String... tags) {
        String cacheKey = buildCacheKey(name, tags);
        
        DistributionSummary summary = summaryCache.get(cacheKey);
        if (summary != null) {
            cacheHits.incrementAndGet();
            return summary;
        }
        
        cacheMisses.incrementAndGet();
        
        summary = DistributionSummary.builder(name)
            .tags(tags)
            .register(meterRegistry);
            
        if (summaryCache.size() < meterCacheSize) {
            summaryCache.put(cacheKey, summary);
        }
        
        return summary;
    }

    private String buildCacheKey(String name, String... tags) {
        StringBuilder keyBuilder = new StringBuilder(name);
        for (int i = 0; i < tags.length; i += 2) {
            if (i + 1 < tags.length) {
                keyBuilder.append("|").append(tags[i]).append("=").append(tags[i + 1]);
            }
        }
        return keyBuilder.toString();
    }

    private String[] mapToArray(Map<String, String> tagMap) {
        if (tagMap == null || tagMap.isEmpty()) {
            return new String[0];
        }
        
        String[] tags = new String[tagMap.size() * 2];
        int index = 0;
        for (Map.Entry<String, String> entry : tagMap.entrySet()) {
            tags[index++] = entry.getKey();
            tags[index++] = entry.getValue() != null ? entry.getValue() : "null";
        }
        return tags;
    }

    private void updateActiveTransactionCount(TransactionStatus status) {
        switch (status) {
            case PENDING, PROCESSING -> activeTransactions.incrementAndGet();
            case COMPLETED, FAILED, CANCELLED -> {
                long current = activeTransactions.get();
                if (current > 0) {
                    activeTransactions.decrementAndGet();
                }
            }
        }
    }

    private double calculateCacheHitRate() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        
        return total > 0 ? (double) hits / total : 0.0;
    }

    // Legacy methods for backward compatibility
    public Counter createCounter(String name, String description, String... tags) {
        return Counter.builder(name)
            .description(description)
            .tags(tags)
            .register(meterRegistry);
    }

    public Timer createTimer(String name, String description, String... tags) {
        return Timer.builder(name)
            .description(description)
            .tags(tags)
            .register(meterRegistry);
    }
}