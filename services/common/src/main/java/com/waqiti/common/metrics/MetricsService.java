package com.waqiti.common.metrics;

import io.micrometer.core.instrument.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Comprehensive metrics service for business and technical metrics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;
    
    @Value("${metrics.service-name:waqiti-service}")
    private String serviceName;
    
    @Value("${metrics.enabled:true}")
    private boolean metricsEnabled;
    
    // Cache for meters to avoid recreation
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Gauge> gauges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> summaries = new ConcurrentHashMap<>();
    
    // Business metrics tracking
    private final AtomicLong activeTransactions = new AtomicLong(0);
    private final AtomicLong totalUsers = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);

    // Legacy methods - maintained for backward compatibility
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

    public void recordCounter(String name, double increment, String... tags) {
        Counter.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .increment(increment);
    }

    public void recordTimer(String name, Duration duration, String... tags) {
        Timer.builder(name)
                .tags(tags)
                .register(meterRegistry)
                .record(duration);
    }
    
    // Enhanced metrics methods
    
    /**
     * Record API endpoint metrics
     */
    public void recordEndpointMetrics(String endpoint, String method, int statusCode, Duration duration) {
        if (!metricsEnabled) return;
        
        try {
            // Request counter
            getCounter("api_requests_total", 
                "endpoint", endpoint, 
                "method", method, 
                "status", String.valueOf(statusCode),
                "service", serviceName)
                .increment();
            
            // Response time timer
            getTimer("api_response_time", 
                "endpoint", endpoint,
                "method", method,
                "service", serviceName)
                .record(duration);
            
            // Error rate tracking
            if (statusCode >= 400) {
                getCounter("api_errors_total",
                    "endpoint", endpoint,
                    "method", method,
                    "status", String.valueOf(statusCode),
                    "service", serviceName)
                    .increment();
            }
            
        } catch (Exception e) {
            log.error("Failed to record endpoint metrics for {}", endpoint, e);
        }
    }
    
    /**
     * Record business transaction metrics
     */
    public void recordTransactionMetrics(TransactionMetrics transaction) {
        if (!metricsEnabled) return;
        
        try {
            // Transaction counter
            getCounter("business_transactions_total",
                "type", transaction.getTransactionType(),
                "status", transaction.getStatus().name(),
                "currency", transaction.getCurrency(),
                "service", serviceName)
                .increment();
            
            // Transaction amount distribution
            getDistributionSummary("transaction_amount",
                "type", transaction.getTransactionType(),
                "currency", transaction.getCurrency(),
                "service", serviceName)
                .record(transaction.getAmount().doubleValue());
            
            // Transaction processing time
            if (transaction.getProcessingTime() != null) {
                getTimer("transaction_processing_time",
                    "type", transaction.getTransactionType(),
                    "service", serviceName)
                    .record(transaction.getProcessingTime());
            }
            
            // Update active transactions gauge
            if (transaction.getStatus() == TransactionStatus.PENDING) {
                activeTransactions.incrementAndGet();
            } else if (transaction.getStatus() == TransactionStatus.COMPLETED || 
                       transaction.getStatus() == TransactionStatus.FAILED) {
                activeTransactions.decrementAndGet();
            }
            
        } catch (Exception e) {
            log.error("Failed to record transaction metrics", e);
        }
    }
    
    /**
     * Record KYC verification metrics
     */
    public void recordKycMetrics(KycMetrics kyc) {
        if (!metricsEnabled) return;
        
        try {
            getCounter("kyc_verifications_total",
                "provider", kyc.getProvider(),
                "status", kyc.getStatus(),
                "level", kyc.getVerificationLevel(),
                "service", serviceName)
                .increment();
            
            if (kyc.getProcessingTime() != null) {
                getTimer("kyc_processing_time",
                    "provider", kyc.getProvider(),
                    "level", kyc.getVerificationLevel(),
                    "service", serviceName)
                    .record(kyc.getProcessingTime());
            }
            
            if (kyc.getConfidenceScore() > 0) {
                getDistributionSummary("kyc_confidence_score",
                    "provider", kyc.getProvider(),
                    "service", serviceName)
                    .record(kyc.getConfidenceScore());
            }
            
        } catch (Exception e) {
            log.error("Failed to record KYC metrics", e);
        }
    }
    
    /**
     * Record fraud detection metrics
     */
    public void recordFraudMetrics(FraudMetrics fraud) {
        if (!metricsEnabled) return;
        
        try {
            getCounter("fraud_checks_total",
                "result", fraud.getResult(),
                "risk_level", fraud.getRiskLevel(),
                "service", serviceName)
                .increment();
            
            getDistributionSummary("fraud_risk_score",
                "service", serviceName)
                .record(fraud.getRiskScore());
            
            if (fraud.getProcessingTime() != null) {
                getTimer("fraud_check_time",
                    "service", serviceName)
                    .record(fraud.getProcessingTime());
            }
            
            // Track blocked transactions
            if ("BLOCKED".equals(fraud.getResult())) {
                getCounter("transactions_blocked_total",
                    "reason", fraud.getBlockedReason(),
                    "service", serviceName)
                    .increment();
            }
            
        } catch (Exception e) {
            log.error("Failed to record fraud metrics", e);
        }
    }
    
    /**
     * Record cache metrics
     */
    public void recordCacheMetrics(String cacheName, CacheOperation operation, boolean hit) {
        if (!metricsEnabled) return;
        
        try {
            getCounter("cache_operations_total",
                "cache", cacheName,
                "operation", operation.name(),
                "result", hit ? "HIT" : "MISS",
                "service", serviceName)
                .increment();
            
        } catch (Exception e) {
            log.error("Failed to record cache metrics for {}", cacheName, e);
        }
    }
    
    /**
     * Record database metrics
     */
    public void recordDatabaseMetrics(DatabaseMetrics dbMetrics) {
        if (!metricsEnabled) return;
        
        try {
            getCounter("database_queries_total",
                "operation", dbMetrics.getOperation(),
                "table", dbMetrics.getTableName(),
                "status", dbMetrics.getStatus(),
                "service", serviceName)
                .increment();
            
            if (dbMetrics.getQueryTime() != null) {
                getTimer("database_query_time",
                    "operation", dbMetrics.getOperation(),
                    "table", dbMetrics.getTableName(),
                    "service", serviceName)
                    .record(dbMetrics.getQueryTime());
            }
            
            if (dbMetrics.getRowsAffected() > 0) {
                getDistributionSummary("database_rows_affected",
                    "operation", dbMetrics.getOperation(),
                    "table", dbMetrics.getTableName(),
                    "service", serviceName)
                    .record(dbMetrics.getRowsAffected());
            }
            
        } catch (Exception e) {
            log.error("Failed to record database metrics", e);
        }
    }
    
    /**
     * Record external service call metrics
     */
    public void recordExternalServiceMetrics(ExternalServiceMetrics serviceMetrics) {
        if (!metricsEnabled) return;
        
        try {
            getCounter("external_service_calls_total",
                "service", serviceMetrics.getServiceName(),
                "operation", serviceMetrics.getOperation(),
                "status", serviceMetrics.getStatus(),
                "response_code", String.valueOf(serviceMetrics.getResponseCode()))
                .increment();
            
            if (serviceMetrics.getResponseTime() != null) {
                getTimer("external_service_response_time",
                    "service", serviceMetrics.getServiceName(),
                    "operation", serviceMetrics.getOperation())
                    .record(serviceMetrics.getResponseTime());
            }
            
            // Circuit breaker metrics
            if (serviceMetrics.isCircuitBreakerTriggered()) {
                getCounter("circuit_breaker_triggered_total",
                    "service", serviceMetrics.getServiceName(),
                    "operation", serviceMetrics.getOperation())
                    .increment();
            }
            
            // Retry metrics
            if (serviceMetrics.getRetryCount() > 0) {
                getCounter("service_retries_total",
                    "service", serviceMetrics.getServiceName(),
                    "operation", serviceMetrics.getOperation())
                    .increment(serviceMetrics.getRetryCount());
            }
            
        } catch (Exception e) {
            log.error("Failed to record external service metrics", e);
        }
    }
    
    /**
     * Register custom business gauge
     */
    public void registerGauge(String name, String description, Supplier<Number> valueSupplier, String... tags) {
        if (!metricsEnabled) return;
        
        try {
            Gauge.builder(name, valueSupplier)
                .description(description)
                .tags(Tags.of(tags))
                .register(meterRegistry);
                
        } catch (Exception e) {
            log.error("Failed to register gauge: {}", name, e);
        }
    }
    
    /**
     * Initialize default business gauges
     */
    public void initializeBusinessGauges() {
        if (!metricsEnabled) return;
        
        // Active transactions
        registerGauge("active_transactions_count", 
            "Number of currently active transactions",
            activeTransactions::get,
            "service", serviceName);
        
        // Total users
        registerGauge("total_users_count",
            "Total number of registered users",
            totalUsers::get,
            "service", serviceName);
        
        // Failed operations
        registerGauge("failed_operations_count",
            "Number of failed operations",
            failedOperations::get,
            "service", serviceName);
            
        // JVM metrics
        registerGauge("jvm_memory_used_bytes",
            "JVM memory usage in bytes",
            () -> Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
            "service", serviceName);
    }
    
    /**
     * Update user count
     */
    public void updateUserCount(long count) {
        if (metricsEnabled) {
            totalUsers.set(count);
        }
    }
    
    /**
     * Increment counter with tags (Map-based API for convenience)
     */
    public void incrementCounter(String name, Map<String, ?> tags) {
        if (!metricsEnabled) return;

        try {
            String[] tagArray = tags.entrySet().stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getKey(), String.valueOf(e.getValue())))
                .toArray(String[]::new);

            getCounter(name, tagArray).increment();

        } catch (Exception e) {
            log.error("Failed to increment counter: {}", name, e);
        }
    }

    /**
     * Record timer with tags (Map-based API for convenience)
     */
    public void recordTimer(String name, Long durationMs, Map<String, ?> tags) {
        if (!metricsEnabled) return;

        try {
            String[] tagArray = tags.entrySet().stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getKey(), String.valueOf(e.getValue())))
                .toArray(String[]::new);

            getTimer(name, tagArray).record(Duration.ofMillis(durationMs));

        } catch (Exception e) {
            log.error("Failed to record timer: {}", name, e);
        }
    }

    /**
     * Record failed operation
     */
    public void recordFailedOperation(String operation, String reason) {
        if (!metricsEnabled) return;

        failedOperations.incrementAndGet();
        getCounter("operations_failed_total",
            "operation", operation,
            "reason", reason,
            "service", serviceName)
            .increment();
    }
    
    /**
     * Record successful operation
     */
    public void recordSuccessfulOperation(String operation, Duration duration) {
        if (!metricsEnabled) return;
        
        getCounter("operations_successful_total",
            "operation", operation,
            "service", serviceName)
            .increment();
            
        getTimer("operation_duration",
            "operation", operation,
            "service", serviceName)
            .record(duration);
    }
    
    // Helper methods for meter management
    
    private Counter getCounter(String name, String... tags) {
        String key = name + ":" + String.join(":", tags);
        return counters.computeIfAbsent(key, k -> 
            Counter.builder(name)
                .tags(Tags.of(tags))
                .register(meterRegistry)
        );
    }
    
    private Timer getTimer(String name, String... tags) {
        String key = name + ":" + String.join(":", tags);
        return timers.computeIfAbsent(key, k ->
            Timer.builder(name)
                .tags(Tags.of(tags))
                .register(meterRegistry)
        );
    }
    
    private DistributionSummary getDistributionSummary(String name, String... tags) {
        String key = name + ":" + String.join(":", tags);
        return summaries.computeIfAbsent(key, k ->
            DistributionSummary.builder(name)
                .tags(Tags.of(tags))
                .register(meterRegistry)
        );
    }
    
    /**
     * Get metrics summary
     */
    public MetricsSummary getMetricsSummary() {
        return MetricsSummary.builder()
            .enabled(metricsEnabled)
            .serviceName(serviceName)
            .activeTransactions(activeTransactions.get())
            .totalUsers(totalUsers.get())
            .failedOperations(failedOperations.get())
            .countersRegistered(counters.size())
            .timersRegistered(timers.size())
            .gaugesRegistered(gauges.size())
            .summariesRegistered(summaries.size())
            .lastUpdated(Instant.now())
            .build();
    }
}