package com.waqiti.infrastructure.kafka;

import com.waqiti.common.events.CachePerformanceAlertEvent;
import com.waqiti.infrastructure.domain.CachePerformanceRecord;
import com.waqiti.infrastructure.repository.CachePerformanceRecordRepository;
import com.waqiti.infrastructure.service.CacheOptimizationService;
import com.waqiti.infrastructure.service.CacheRecoveryService;
import com.waqiti.infrastructure.service.CacheMonitoringService;
import com.waqiti.infrastructure.metrics.CacheMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicDouble;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class CachePerformanceAlertsConsumer {

    private final CachePerformanceRecordRepository performanceRepository;
    private final CacheOptimizationService optimizationService;
    private final CacheRecoveryService recoveryService;
    private final CacheMonitoringService monitoringService;
    private final CacheMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Cache performance tracking
    private final AtomicLong cacheIssueCount = new AtomicLong(0);
    private final AtomicDouble averageCacheHitRatio = new AtomicDouble(0.0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter cacheIssueCounter;
    private Timer processingTimer;
    private Gauge cacheIssueGauge;
    private Gauge avgHitRatioGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("cache_performance_alerts_processed_total")
            .description("Total number of successfully processed cache performance alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("cache_performance_alerts_errors_total")
            .description("Total number of cache performance alert processing errors")
            .register(meterRegistry);
        cacheIssueCounter = Counter.builder("cache_issues_total")
            .description("Total number of cache performance issues")
            .register(meterRegistry);
        processingTimer = Timer.builder("cache_performance_alerts_processing_duration")
            .description("Time taken to process cache performance alert events")
            .register(meterRegistry);
        cacheIssueGauge = Gauge.builder("cache_issues_active")
            .description("Number of active cache performance issues")
            .register(meterRegistry, cacheIssueCount, AtomicLong::get);
        avgHitRatioGauge = Gauge.builder("average_cache_hit_ratio")
            .description("Average cache hit ratio across all cache instances")
            .register(meterRegistry, averageCacheHitRatio, AtomicDouble::get);
    }

    @KafkaListener(
        topics = {"cache-performance-alerts", "cache-hit-ratio-alerts", "cache-memory-alerts"},
        groupId = "cache-performance-alerts-service-group",
        containerFactory = "criticalInfrastructureKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "cache-performance-alerts", fallbackMethod = "handleCachePerformanceAlertFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCachePerformanceAlertEvent(
            @Payload CachePerformanceAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("cache-perf-%s-p%d-o%d", event.getCacheInstanceId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getCacheInstanceId(), event.getAlertType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing cache performance alert: cacheInstanceId={}, alertType={}, metricValue={}, threshold={}",
                event.getCacheInstanceId(), event.getAlertType(), event.getMetricValue(), event.getThresholdValue());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Cache recovery impact assessment
            assessCacheRecoveryImpact(event, correlationId);

            switch (event.getAlertType()) {
                case LOW_HIT_RATIO:
                    handleLowHitRatio(event, correlationId);
                    break;

                case HIGH_MEMORY_USAGE:
                    handleHighMemoryUsage(event, correlationId);
                    break;

                case HIGH_LATENCY:
                    handleHighLatency(event, correlationId);
                    break;

                case EVICTION_RATE_HIGH:
                    handleHighEvictionRate(event, correlationId);
                    break;

                case CONNECTION_POOL_EXHAUSTED:
                    handleConnectionPoolExhausted(event, correlationId);
                    break;

                case CACHE_UNAVAILABLE:
                    handleCacheUnavailable(event, correlationId);
                    break;

                case SLOW_OPERATIONS:
                    handleSlowOperations(event, correlationId);
                    break;

                case MEMORY_FRAGMENTATION:
                    handleMemoryFragmentation(event, correlationId);
                    break;

                case REPLICATION_LAG:
                    handleReplicationLag(event, correlationId);
                    break;

                case PERSISTENCE_ISSUES:
                    handlePersistenceIssues(event, correlationId);
                    break;

                default:
                    log.warn("Unknown cache performance alert type: {}", event.getAlertType());
                    handleGenericCacheAlert(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logCacheEvent("CACHE_PERFORMANCE_ALERT_PROCESSED", event.getCacheInstanceId(),
                Map.of("alertType", event.getAlertType(), "metricValue", event.getMetricValue(),
                    "thresholdValue", event.getThresholdValue(), "cacheType", event.getCacheType(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process cache performance alert event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("cache-performance-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCachePerformanceAlertFallback(
            CachePerformanceAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("cache-perf-fallback-%s-p%d-o%d", event.getCacheInstanceId(), partition, offset);

        log.error("Circuit breaker fallback triggered for cache performance alert: cacheInstanceId={}, error={}",
            event.getCacheInstanceId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("cache-performance-alerts-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for cache unavailability
        if ("CACHE_UNAVAILABLE".equals(event.getAlertType())) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical Cache Performance Alert - Circuit Breaker Triggered",
                    String.format("Critical cache performance monitoring for %s failed: %s",
                        event.getCacheInstanceId(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCachePerformanceAlertEvent(
            @Payload CachePerformanceAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-cache-perf-%s-%d", event.getCacheInstanceId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Cache performance alert permanently failed: cacheInstanceId={}, topic={}, error={}",
            event.getCacheInstanceId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logCacheEvent("CACHE_PERFORMANCE_ALERT_DLT_EVENT", event.getCacheInstanceId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "alertType", event.getAlertType(), "metricValue", event.getMetricValue(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Cache Performance Alert Dead Letter Event",
                String.format("Cache performance monitoring for %s sent to DLT: %s",
                    event.getCacheInstanceId(), exceptionMessage),
                Map.of("cacheInstanceId", event.getCacheInstanceId(), "topic", topic,
                    "correlationId", correlationId, "alertType", event.getAlertType())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessCacheRecoveryImpact(CachePerformanceAlertEvent event, String correlationId) {
        if ("CACHE_UNAVAILABLE".equals(event.getAlertType()) || event.getMetricValue() < 50.0) {
            cacheIssueCount.incrementAndGet();
            cacheIssueCounter.increment();

            // Alert if too many cache issues
            if (cacheIssueCount.get() > 3) {
                try {
                    notificationService.sendExecutiveAlert(
                        "CRITICAL: Multiple Cache Performance Issues",
                        String.format("Cache issues count: %d. Cache infrastructure review required.",
                            cacheIssueCount.get()),
                        "CRITICAL"
                    );
                } catch (Exception ex) {
                    log.error("Failed to send cache recovery impact alert: {}", ex.getMessage());
                }
            }
        }

        // Update average hit ratio
        if ("LOW_HIT_RATIO".equals(event.getAlertType()) || "HIGH_HIT_RATIO".equals(event.getAlertType())) {
            double currentAvg = averageCacheHitRatio.get();
            double newAvg = (currentAvg + event.getMetricValue()) / 2.0;
            averageCacheHitRatio.set(newAvg);
        }
    }

    private void handleLowHitRatio(CachePerformanceAlertEvent event, String correlationId) {
        createPerformanceRecord(event, "LOW_HIT_RATIO", correlationId);

        // Cache warming
        kafkaTemplate.send("cache-warming-requests", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "warmingType", "LOW_HIT_RATIO_WARMING",
            "currentHitRatio", event.getMetricValue(),
            "targetHitRatio", event.getThresholdValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Cache policy optimization
        kafkaTemplate.send("cache-policy-optimization", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "optimizationType", "HIT_RATIO_IMPROVEMENT",
            "currentPolicy", event.getEvictionPolicy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // TTL analysis
        kafkaTemplate.send("cache-ttl-analysis", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "analysisType", "HIT_RATIO_TTL_OPTIMIZATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Low Cache Hit Ratio",
            String.format("Cache %s hit ratio: %.1f%% (threshold: %.1f%%)",
                event.getCacheInstanceId(), event.getMetricValue(), event.getThresholdValue()),
            "MEDIUM");

        metricsService.recordCacheAlert("LOW_HIT_RATIO", event.getCacheInstanceId());
    }

    private void handleHighMemoryUsage(CachePerformanceAlertEvent event, String correlationId) {
        createPerformanceRecord(event, "HIGH_MEMORY_USAGE", correlationId);

        // Memory optimization
        kafkaTemplate.send("cache-memory-optimization", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "optimizationType", "MEMORY_REDUCTION",
            "currentMemoryUsage", event.getMetricValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Aggressive eviction
        if (event.getMetricValue() > 90.0) {
            kafkaTemplate.send("cache-aggressive-eviction", Map.of(
                "cacheInstanceId", event.getCacheInstanceId(),
                "evictionType", "HIGH_MEMORY_EMERGENCY",
                "targetMemoryUsage", 75.0,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Memory scaling
        kafkaTemplate.send("cache-memory-scaling", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "scalingType", "MEMORY_SCALE_UP",
            "scalingFactor", 1.3,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCacheAlert("HIGH_MEMORY_USAGE", event.getCacheInstanceId());
    }

    private void handleHighLatency(CachePerformanceAlertEvent event, String correlationId) {
        createPerformanceRecord(event, "HIGH_LATENCY", correlationId);

        // Latency analysis
        kafkaTemplate.send("cache-latency-analysis", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "analysisType", "HIGH_LATENCY_INVESTIGATION",
            "currentLatency", event.getMetricValue(),
            "baselineLatency", event.getBaselineValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Connection optimization
        kafkaTemplate.send("cache-connection-optimization", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "optimizationType", "LATENCY_REDUCTION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Cache distribution optimization
        kafkaTemplate.send("cache-distribution-optimization", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "optimizationType", "LATENCY_OPTIMIZATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCacheAlert("HIGH_LATENCY", event.getCacheInstanceId());
    }

    private void handleHighEvictionRate(CachePerformanceAlertEvent event, String correlationId) {
        createPerformanceRecord(event, "HIGH_EVICTION_RATE", correlationId);

        // Eviction policy optimization
        kafkaTemplate.send("eviction-policy-optimization", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "optimizationType", "EVICTION_RATE_REDUCTION",
            "currentEvictionRate", event.getMetricValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Cache sizing analysis
        kafkaTemplate.send("cache-sizing-analysis", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "analysisType", "HIGH_EVICTION_SIZING",
            "evictionRate", event.getMetricValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Increase cache size if needed
        if (event.getMetricValue() > 1000) { // > 1000 evictions per minute
            kafkaTemplate.send("cache-capacity-scaling", Map.of(
                "cacheInstanceId", event.getCacheInstanceId(),
                "scalingType", "CAPACITY_INCREASE",
                "scalingFactor", 1.5,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordCacheAlert("HIGH_EVICTION_RATE", event.getCacheInstanceId());
    }

    private void handleConnectionPoolExhausted(CachePerformanceAlertEvent event, String correlationId) {
        createPerformanceRecord(event, "CONNECTION_POOL_EXHAUSTED", correlationId);

        // Connection pool scaling
        kafkaTemplate.send("cache-connection-pool-scaling", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "scalingType", "POOL_EXHAUSTION_SCALING",
            "currentPoolSize", event.getCurrentConnectionPoolSize(),
            "targetPoolSize", event.getCurrentConnectionPoolSize() * 1.5,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Connection leak detection
        kafkaTemplate.send("cache-connection-leak-detection", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "detectionType", "POOL_EXHAUSTION_ANALYSIS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Cache Connection Pool Exhausted",
            String.format("Cache %s connection pool exhausted",
                event.getCacheInstanceId()),
            "HIGH");

        metricsService.recordCacheAlert("CONNECTION_POOL_EXHAUSTED", event.getCacheInstanceId());
    }

    private void handleCacheUnavailable(CachePerformanceAlertEvent event, String correlationId) {
        createPerformanceRecord(event, "CACHE_UNAVAILABLE", correlationId);

        // Immediate failover
        kafkaTemplate.send("cache-failover-requests", Map.of(
            "primaryCacheInstanceId", event.getCacheInstanceId(),
            "failoverType", "CACHE_UNAVAILABLE",
            "urgency", "IMMEDIATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Cache recovery
        kafkaTemplate.send("cache-recovery-requests", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "recoveryType", "UNAVAILABILITY_RECOVERY",
            "priority", "CRITICAL",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Fallback to database
        kafkaTemplate.send("database-fallback-activation", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "fallbackType", "CACHE_UNAVAILABLE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Cache Unavailable",
            String.format("Cache %s is unavailable, initiating failover",
                event.getCacheInstanceId()),
            "CRITICAL");

        metricsService.recordCacheAlert("CACHE_UNAVAILABLE", event.getCacheInstanceId());
    }

    private void handleSlowOperations(CachePerformanceAlertEvent event, String correlationId) {
        createPerformanceRecord(event, "SLOW_OPERATIONS", correlationId);

        // Operation performance analysis
        kafkaTemplate.send("cache-operation-analysis", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "analysisType", "SLOW_OPERATIONS_INVESTIGATION",
            "averageOperationTime", event.getMetricValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Operation optimization
        kafkaTemplate.send("cache-operation-optimization", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "optimizationType", "OPERATION_SPEED_IMPROVEMENT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCacheAlert("SLOW_OPERATIONS", event.getCacheInstanceId());
    }

    private void handleMemoryFragmentation(CachePerformanceAlertEvent event, String correlationId) {
        createPerformanceRecord(event, "MEMORY_FRAGMENTATION", correlationId);

        // Memory defragmentation
        kafkaTemplate.send("cache-memory-defragmentation", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "defragmentationType", "SCHEDULED_DEFRAG",
            "fragmentationPercent", event.getMetricValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Memory allocation optimization
        kafkaTemplate.send("cache-memory-allocation-optimization", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "optimizationType", "FRAGMENTATION_PREVENTION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCacheAlert("MEMORY_FRAGMENTATION", event.getCacheInstanceId());
    }

    private void handleReplicationLag(CachePerformanceAlertEvent event, String correlationId) {
        createPerformanceRecord(event, "REPLICATION_LAG", correlationId);

        // Replication optimization
        kafkaTemplate.send("cache-replication-optimization", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "optimizationType", "REPLICATION_LAG_REDUCTION",
            "lagMilliseconds", event.getMetricValue(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Network bandwidth check
        kafkaTemplate.send("cache-replication-network-checks", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "checkType", "REPLICATION_BANDWIDTH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCacheAlert("REPLICATION_LAG", event.getCacheInstanceId());
    }

    private void handlePersistenceIssues(CachePerformanceAlertEvent event, String correlationId) {
        createPerformanceRecord(event, "PERSISTENCE_ISSUES", correlationId);

        // Persistence optimization
        kafkaTemplate.send("cache-persistence-optimization", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "optimizationType", "PERSISTENCE_PERFORMANCE",
            "persistenceType", event.getPersistenceType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Backup strategy adjustment
        kafkaTemplate.send("cache-backup-strategy-adjustment", Map.of(
            "cacheInstanceId", event.getCacheInstanceId(),
            "adjustmentType", "PERSISTENCE_ISSUES",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCacheAlert("PERSISTENCE_ISSUES", event.getCacheInstanceId());
    }

    private void handleGenericCacheAlert(CachePerformanceAlertEvent event, String correlationId) {
        createPerformanceRecord(event, "GENERIC", correlationId);

        // Log for investigation
        auditService.logCacheEvent("UNKNOWN_CACHE_ALERT", event.getCacheInstanceId(),
            Map.of("alertType", event.getAlertType(), "metricValue", event.getMetricValue(),
                "thresholdValue", event.getThresholdValue(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Cache Performance Alert",
            String.format("Unknown cache alert for %s: %s",
                event.getCacheInstanceId(), event.getAlertType()),
            "MEDIUM");

        metricsService.recordCacheAlert("GENERIC", event.getCacheInstanceId());
    }

    private void createPerformanceRecord(CachePerformanceAlertEvent event, String alertType, String correlationId) {
        try {
            CachePerformanceRecord record = CachePerformanceRecord.builder()
                .cacheInstanceId(event.getCacheInstanceId())
                .cacheType(event.getCacheType())
                .alertType(alertType)
                .metricValue(event.getMetricValue())
                .thresholdValue(event.getThresholdValue())
                .memoryUsagePercent(event.getMemoryUsagePercent())
                .hitRatio(event.getHitRatio())
                .alertTime(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

            performanceRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to create cache performance record: {}", e.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }
}