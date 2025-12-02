package com.waqiti.monitoring.kafka;

import com.waqiti.common.events.PerformanceMonitoringEvent;
import com.waqiti.monitoring.domain.PerformanceRecord;
import com.waqiti.monitoring.repository.PerformanceRecordRepository;
import com.waqiti.monitoring.service.PerformanceAnalysisService;
import com.waqiti.monitoring.service.PerformanceTuningService;
import com.waqiti.monitoring.service.ThresholdService;
import com.waqiti.monitoring.metrics.PerformanceMetricsService;
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
public class PerformanceMonitoringEventsConsumer {

    private final PerformanceRecordRepository performanceRepository;
    private final PerformanceAnalysisService analysisService;
    private final PerformanceTuningService tuningService;
    private final ThresholdService thresholdService;
    private final PerformanceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Performance tracking
    private final AtomicLong performanceViolationCount = new AtomicLong(0);
    private final AtomicDouble averageResponseTime = new AtomicDouble(0.0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter performanceViolationCounter;
    private Timer processingTimer;
    private Gauge performanceViolationGauge;
    private Gauge avgResponseTimeGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("performance_monitoring_events_processed_total")
            .description("Total number of successfully processed performance monitoring events")
            .register(meterRegistry);
        errorCounter = Counter.builder("performance_monitoring_events_errors_total")
            .description("Total number of performance monitoring event processing errors")
            .register(meterRegistry);
        performanceViolationCounter = Counter.builder("performance_violations_total")
            .description("Total number of performance threshold violations")
            .register(meterRegistry);
        processingTimer = Timer.builder("performance_monitoring_events_processing_duration")
            .description("Time taken to process performance monitoring events")
            .register(meterRegistry);
        performanceViolationGauge = Gauge.builder("performance_violations_active")
            .description("Number of active performance violations")
            .register(meterRegistry, performanceViolationCount, AtomicLong::get);
        avgResponseTimeGauge = Gauge.builder("average_response_time_ms")
            .description("Average response time across all monitored components")
            .register(meterRegistry, averageResponseTime, AtomicDouble::get);
    }

    @KafkaListener(
        topics = {"performance-monitoring-events", "performance-alerts", "latency-threshold-violations"},
        groupId = "performance-monitoring-events-service-group",
        containerFactory = "criticalInfrastructureKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "performance-monitoring-events", fallbackMethod = "handlePerformanceEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handlePerformanceMonitoringEvent(
            @Payload PerformanceMonitoringEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("perf-%s-p%d-o%d", event.getComponentId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getComponentId(), event.getMetricType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing performance monitoring event: componentId={}, metricType={}, value={}, threshold={}",
                event.getComponentId(), event.getMetricType(), event.getMetricValue(), event.getThresholdValue());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Performance impact assessment
            assessPerformanceImpact(event, correlationId);

            switch (event.getMetricType()) {
                case RESPONSE_TIME:
                    handleResponseTimeMetric(event, correlationId);
                    break;

                case THROUGHPUT:
                    handleThroughputMetric(event, correlationId);
                    break;

                case CPU_UTILIZATION:
                    handleCpuUtilizationMetric(event, correlationId);
                    break;

                case MEMORY_UTILIZATION:
                    handleMemoryUtilizationMetric(event, correlationId);
                    break;

                case DISK_IO:
                    handleDiskIOMetric(event, correlationId);
                    break;

                case NETWORK_IO:
                    handleNetworkIOMetric(event, correlationId);
                    break;

                case ERROR_RATE:
                    handleErrorRateMetric(event, correlationId);
                    break;

                case QUEUE_LENGTH:
                    handleQueueLengthMetric(event, correlationId);
                    break;

                case DATABASE_CONNECTIONS:
                    handleDatabaseConnectionsMetric(event, correlationId);
                    break;

                case TRANSACTION_RATE:
                    handleTransactionRateMetric(event, correlationId);
                    break;

                default:
                    log.warn("Unknown performance metric type: {}", event.getMetricType());
                    handleGenericPerformanceMetric(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logPerformanceEvent("PERFORMANCE_MONITORING_EVENT_PROCESSED", event.getComponentId(),
                Map.of("metricType", event.getMetricType(), "metricValue", event.getMetricValue(),
                    "thresholdValue", event.getThresholdValue(), "violationType", event.getViolationType(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process performance monitoring event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("performance-monitoring-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handlePerformanceEventFallback(
            PerformanceMonitoringEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("perf-fallback-%s-p%d-o%d", event.getComponentId(), partition, offset);

        log.error("Circuit breaker fallback triggered for performance event: componentId={}, error={}",
            event.getComponentId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("performance-monitoring-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert for severe performance violations
        if ("CRITICAL".equals(event.getViolationType())) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical Performance Monitoring Failure - Circuit Breaker Triggered",
                    String.format("Critical performance monitoring for %s failed: %s",
                        event.getComponentId(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltPerformanceEvent(
            @Payload PerformanceMonitoringEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-perf-%s-%d", event.getComponentId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Performance event permanently failed: componentId={}, topic={}, error={}",
            event.getComponentId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logPerformanceEvent("PERFORMANCE_MONITORING_DLT_EVENT", event.getComponentId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "metricType", event.getMetricType(), "metricValue", event.getMetricValue(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Performance Monitoring Event Dead Letter Event",
                String.format("Performance monitoring for %s sent to DLT: %s",
                    event.getComponentId(), exceptionMessage),
                Map.of("componentId", event.getComponentId(), "topic", topic,
                    "correlationId", correlationId, "metricType", event.getMetricType())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessPerformanceImpact(PerformanceMonitoringEvent event, String correlationId) {
        if (thresholdService.isViolation(event.getMetricValue(), event.getThresholdValue(), event.getViolationType())) {
            performanceViolationCount.incrementAndGet();
            performanceViolationCounter.increment();

            // Update average response time if applicable
            if ("RESPONSE_TIME".equals(event.getMetricType())) {
                double currentAvg = averageResponseTime.get();
                double newAvg = (currentAvg + event.getMetricValue()) / 2.0;
                averageResponseTime.set(newAvg);
            }

            // Alert if too many violations
            if (performanceViolationCount.get() > 10) {
                try {
                    notificationService.sendExecutiveAlert(
                        "CRITICAL: Multiple Performance Violations",
                        String.format("Performance violations count: %d. System performance degraded.",
                            performanceViolationCount.get()),
                        "CRITICAL"
                    );
                    // Reset counter after alert
                    performanceViolationCount.set(0);
                } catch (Exception ex) {
                    log.error("Failed to send performance impact alert: {}", ex.getMessage());
                }
            }
        }
    }

    private void handleResponseTimeMetric(PerformanceMonitoringEvent event, String correlationId) {
        createPerformanceRecord(event, "RESPONSE_TIME", correlationId);

        if (thresholdService.isViolation(event.getMetricValue(), event.getThresholdValue(), event.getViolationType())) {
            // Analyze response time patterns
            kafkaTemplate.send("response-time-analysis-requests", Map.of(
                "componentId", event.getComponentId(),
                "analysisType", "RESPONSE_TIME_DEGRADATION",
                "currentValue", event.getMetricValue(),
                "threshold", event.getThresholdValue(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Trigger performance tuning
            kafkaTemplate.send("performance-tuning-requests", Map.of(
                "componentId", event.getComponentId(),
                "tuningType", "RESPONSE_TIME_OPTIMIZATION",
                "priority", event.getViolationType(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            if ("CRITICAL".equals(event.getViolationType())) {
                notificationService.sendOperationalAlert("Critical Response Time Violation",
                    String.format("Component %s response time: %.2fms (threshold: %.2fms)",
                        event.getComponentId(), event.getMetricValue(), event.getThresholdValue()),
                    "CRITICAL");
            }
        }

        metricsService.recordPerformanceMetric("RESPONSE_TIME", event.getComponentId(), event.getMetricValue());
    }

    private void handleThroughputMetric(PerformanceMonitoringEvent event, String correlationId) {
        createPerformanceRecord(event, "THROUGHPUT", correlationId);

        if (thresholdService.isViolation(event.getMetricValue(), event.getThresholdValue(), event.getViolationType())) {
            // Analyze throughput patterns
            kafkaTemplate.send("throughput-analysis-requests", Map.of(
                "componentId", event.getComponentId(),
                "analysisType", "THROUGHPUT_DEGRADATION",
                "currentValue", event.getMetricValue(),
                "threshold", event.getThresholdValue(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Scale resources if needed
            kafkaTemplate.send("resource-scaling-requests", Map.of(
                "componentId", event.getComponentId(),
                "scalingType", "THROUGHPUT_SCALING",
                "targetThroughput", event.getThresholdValue(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordPerformanceMetric("THROUGHPUT", event.getComponentId(), event.getMetricValue());
    }

    private void handleCpuUtilizationMetric(PerformanceMonitoringEvent event, String correlationId) {
        createPerformanceRecord(event, "CPU_UTILIZATION", correlationId);

        if (thresholdService.isViolation(event.getMetricValue(), event.getThresholdValue(), event.getViolationType())) {
            // CPU optimization
            kafkaTemplate.send("cpu-optimization-requests", Map.of(
                "componentId", event.getComponentId(),
                "optimizationType", "CPU_LOAD_REDUCTION",
                "currentUtilization", event.getMetricValue(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Scale CPU resources
            if (event.getMetricValue() > 90.0) {
                kafkaTemplate.send("cpu-scaling-requests", Map.of(
                    "componentId", event.getComponentId(),
                    "scalingType", "CPU_SCALE_UP",
                    "urgency", "HIGH",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }
        }

        metricsService.recordPerformanceMetric("CPU_UTILIZATION", event.getComponentId(), event.getMetricValue());
    }

    private void handleMemoryUtilizationMetric(PerformanceMonitoringEvent event, String correlationId) {
        createPerformanceRecord(event, "MEMORY_UTILIZATION", correlationId);

        if (thresholdService.isViolation(event.getMetricValue(), event.getThresholdValue(), event.getViolationType())) {
            // Memory optimization
            kafkaTemplate.send("memory-optimization-requests", Map.of(
                "componentId", event.getComponentId(),
                "optimizationType", "MEMORY_CLEANUP",
                "currentUtilization", event.getMetricValue(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Check for memory leaks
            if (event.getMetricValue() > 95.0) {
                kafkaTemplate.send("memory-leak-detection", Map.of(
                    "componentId", event.getComponentId(),
                    "detectionType", "EMERGENCY_SCAN",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }
        }

        metricsService.recordPerformanceMetric("MEMORY_UTILIZATION", event.getComponentId(), event.getMetricValue());
    }

    private void handleDiskIOMetric(PerformanceMonitoringEvent event, String correlationId) {
        createPerformanceRecord(event, "DISK_IO", correlationId);

        if (thresholdService.isViolation(event.getMetricValue(), event.getThresholdValue(), event.getViolationType())) {
            // Disk I/O optimization
            kafkaTemplate.send("disk-io-optimization-requests", Map.of(
                "componentId", event.getComponentId(),
                "optimizationType", "DISK_IO_OPTIMIZATION",
                "currentIOPS", event.getMetricValue(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Check disk health
            kafkaTemplate.send("disk-health-checks", Map.of(
                "componentId", event.getComponentId(),
                "checkType", "DISK_PERFORMANCE",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordPerformanceMetric("DISK_IO", event.getComponentId(), event.getMetricValue());
    }

    private void handleNetworkIOMetric(PerformanceMonitoringEvent event, String correlationId) {
        createPerformanceRecord(event, "NETWORK_IO", correlationId);

        if (thresholdService.isViolation(event.getMetricValue(), event.getThresholdValue(), event.getViolationType())) {
            // Network optimization
            kafkaTemplate.send("network-optimization-requests", Map.of(
                "componentId", event.getComponentId(),
                "optimizationType", "NETWORK_BANDWIDTH_OPTIMIZATION",
                "currentBandwidth", event.getMetricValue(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Check network connectivity
            kafkaTemplate.send("network-connectivity-checks", Map.of(
                "componentId", event.getComponentId(),
                "checkType", "BANDWIDTH_ANALYSIS",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordPerformanceMetric("NETWORK_IO", event.getComponentId(), event.getMetricValue());
    }

    private void handleErrorRateMetric(PerformanceMonitoringEvent event, String correlationId) {
        createPerformanceRecord(event, "ERROR_RATE", correlationId);

        if (thresholdService.isViolation(event.getMetricValue(), event.getThresholdValue(), event.getViolationType())) {
            // Error analysis
            kafkaTemplate.send("error-analysis-requests", Map.of(
                "componentId", event.getComponentId(),
                "analysisType", "ERROR_RATE_SPIKE",
                "currentErrorRate", event.getMetricValue(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Circuit breaker activation
            if (event.getMetricValue() > 50.0) {
                kafkaTemplate.send("circuit-breaker-activation", Map.of(
                    "componentId", event.getComponentId(),
                    "activationType", "ERROR_RATE_PROTECTION",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }
        }

        metricsService.recordPerformanceMetric("ERROR_RATE", event.getComponentId(), event.getMetricValue());
    }

    private void handleQueueLengthMetric(PerformanceMonitoringEvent event, String correlationId) {
        createPerformanceRecord(event, "QUEUE_LENGTH", correlationId);

        if (thresholdService.isViolation(event.getMetricValue(), event.getThresholdValue(), event.getViolationType())) {
            // Queue optimization
            kafkaTemplate.send("queue-optimization-requests", Map.of(
                "componentId", event.getComponentId(),
                "optimizationType", "QUEUE_PROCESSING_ACCELERATION",
                "currentQueueLength", event.getMetricValue(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Add processing capacity
            kafkaTemplate.send("processing-capacity-scaling", Map.of(
                "componentId", event.getComponentId(),
                "scalingType", "QUEUE_PROCESSING",
                "targetReduction", 50.0,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordPerformanceMetric("QUEUE_LENGTH", event.getComponentId(), event.getMetricValue());
    }

    private void handleDatabaseConnectionsMetric(PerformanceMonitoringEvent event, String correlationId) {
        createPerformanceRecord(event, "DATABASE_CONNECTIONS", correlationId);

        if (thresholdService.isViolation(event.getMetricValue(), event.getThresholdValue(), event.getViolationType())) {
            // Connection pool optimization
            kafkaTemplate.send("connection-pool-optimization", Map.of(
                "componentId", event.getComponentId(),
                "optimizationType", "CONNECTION_POOL_TUNING",
                "currentConnections", event.getMetricValue(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));

            // Database health check
            kafkaTemplate.send("database-health-checks", Map.of(
                "componentId", event.getComponentId(),
                "checkType", "CONNECTION_ANALYSIS",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordPerformanceMetric("DATABASE_CONNECTIONS", event.getComponentId(), event.getMetricValue());
    }

    private void handleTransactionRateMetric(PerformanceMonitoringEvent event, String correlationId) {
        createPerformanceRecord(event, "TRANSACTION_RATE", correlationId);

        if (thresholdService.isViolation(event.getMetricValue(), event.getThresholdValue(), event.getViolationType())) {
            // Transaction processing optimization
            kafkaTemplate.send("transaction-optimization-requests", Map.of(
                "componentId", event.getComponentId(),
                "optimizationType", "TRANSACTION_RATE_IMPROVEMENT",
                "currentRate", event.getMetricValue(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordPerformanceMetric("TRANSACTION_RATE", event.getComponentId(), event.getMetricValue());
    }

    private void handleGenericPerformanceMetric(PerformanceMonitoringEvent event, String correlationId) {
        createPerformanceRecord(event, "GENERIC", correlationId);

        // Log for investigation
        auditService.logPerformanceEvent("UNKNOWN_PERFORMANCE_METRIC", event.getComponentId(),
            Map.of("metricType", event.getMetricType(), "metricValue", event.getMetricValue(),
                "thresholdValue", event.getThresholdValue(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        metricsService.recordPerformanceMetric("GENERIC", event.getComponentId(), event.getMetricValue());
    }

    private void createPerformanceRecord(PerformanceMonitoringEvent event, String metricType, String correlationId) {
        try {
            PerformanceRecord record = PerformanceRecord.builder()
                .componentId(event.getComponentId())
                .serviceName(event.getServiceName())
                .metricType(metricType)
                .metricValue(event.getMetricValue())
                .thresholdValue(event.getThresholdValue())
                .violationType(event.getViolationType())
                .measurementTime(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

            performanceRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to create performance record: {}", e.getMessage());
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