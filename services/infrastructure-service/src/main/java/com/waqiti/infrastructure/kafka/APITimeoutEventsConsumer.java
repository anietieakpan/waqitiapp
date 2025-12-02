package com.waqiti.infrastructure.kafka;

import com.waqiti.common.events.APITimeoutEvent;
import com.waqiti.infrastructure.domain.APITimeoutRecord;
import com.waqiti.infrastructure.repository.APITimeoutRecordRepository;
import com.waqiti.infrastructure.service.APIPerformanceService;
import com.waqiti.infrastructure.service.APIOptimizationService;
import com.waqiti.infrastructure.service.APIRecoveryService;
import com.waqiti.infrastructure.metrics.APIMetricsService;
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
public class APITimeoutEventsConsumer {

    private final APITimeoutRecordRepository timeoutRepository;
    private final APIPerformanceService performanceService;
    private final APIOptimizationService optimizationService;
    private final APIRecoveryService recoveryService;
    private final APIMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // API timeout tracking
    private final AtomicLong timeoutCount = new AtomicLong(0);
    private final AtomicDouble averageTimeoutDuration = new AtomicDouble(0.0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter timeoutCounter;
    private Timer processingTimer;
    private Gauge timeoutCountGauge;
    private Gauge avgTimeoutDurationGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("api_timeout_events_processed_total")
            .description("Total number of successfully processed API timeout events")
            .register(meterRegistry);
        errorCounter = Counter.builder("api_timeout_events_errors_total")
            .description("Total number of API timeout event processing errors")
            .register(meterRegistry);
        timeoutCounter = Counter.builder("api_timeouts_total")
            .description("Total number of API timeouts")
            .register(meterRegistry);
        processingTimer = Timer.builder("api_timeout_events_processing_duration")
            .description("Time taken to process API timeout events")
            .register(meterRegistry);
        timeoutCountGauge = Gauge.builder("api_timeouts_active")
            .description("Number of active API timeouts")
            .register(meterRegistry, timeoutCount, AtomicLong::get);
        avgTimeoutDurationGauge = Gauge.builder("average_api_timeout_duration_ms")
            .description("Average API timeout duration across all endpoints")
            .register(meterRegistry, averageTimeoutDuration, AtomicDouble::get);
    }

    @KafkaListener(
        topics = {"api-timeout-events", "api-performance-issues", "api-circuit-breaker-events"},
        groupId = "api-timeout-events-service-group",
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
    @CircuitBreaker(name = "api-timeout-events", fallbackMethod = "handleAPITimeoutEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAPITimeoutEvent(
            @Payload APITimeoutEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("api-timeout-%s-p%d-o%d", event.getEndpointId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getEndpointId(), event.getTimeoutDurationMs(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing API timeout event: endpointId={}, timeoutDuration={}ms, httpMethod={}, statusCode={}",
                event.getEndpointId(), event.getTimeoutDurationMs(), event.getHttpMethod(), event.getResponseStatusCode());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // API recovery impact assessment
            assessAPIRecoveryImpact(event, correlationId);

            switch (event.getTimeoutType()) {
                case CONNECTION_TIMEOUT:
                    handleConnectionTimeout(event, correlationId);
                    break;

                case READ_TIMEOUT:
                    handleReadTimeout(event, correlationId);
                    break;

                case WRITE_TIMEOUT:
                    handleWriteTimeout(event, correlationId);
                    break;

                case CIRCUIT_BREAKER_TIMEOUT:
                    handleCircuitBreakerTimeout(event, correlationId);
                    break;

                case RATE_LIMIT_TIMEOUT:
                    handleRateLimitTimeout(event, correlationId);
                    break;

                case UPSTREAM_TIMEOUT:
                    handleUpstreamTimeout(event, correlationId);
                    break;

                case DATABASE_TIMEOUT:
                    handleDatabaseTimeout(event, correlationId);
                    break;

                case CACHE_TIMEOUT:
                    handleCacheTimeout(event, correlationId);
                    break;

                case EXTERNAL_SERVICE_TIMEOUT:
                    handleExternalServiceTimeout(event, correlationId);
                    break;

                case PROCESSING_TIMEOUT:
                    handleProcessingTimeout(event, correlationId);
                    break;

                default:
                    log.warn("Unknown API timeout type: {}", event.getTimeoutType());
                    handleGenericAPITimeout(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logAPIEvent("API_TIMEOUT_EVENT_PROCESSED", event.getEndpointId(),
                Map.of("timeoutType", event.getTimeoutType(), "timeoutDurationMs", event.getTimeoutDurationMs(),
                    "httpMethod", event.getHttpMethod(), "responseStatusCode", event.getResponseStatusCode(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process API timeout event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("api-timeout-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAPITimeoutEventFallback(
            APITimeoutEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("api-timeout-fallback-%s-p%d-o%d", event.getEndpointId(), partition, offset);

        log.error("Circuit breaker fallback triggered for API timeout event: endpointId={}, error={}",
            event.getEndpointId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("api-timeout-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send alert for critical API endpoints
        if ("CRITICAL".equals(event.getEndpointCriticality())) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical API Timeout Event - Circuit Breaker Triggered",
                    String.format("Critical API timeout monitoring for %s failed: %s",
                        event.getEndpointId(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAPITimeoutEvent(
            @Payload APITimeoutEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-api-timeout-%s-%d", event.getEndpointId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - API timeout event permanently failed: endpointId={}, topic={}, error={}",
            event.getEndpointId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logAPIEvent("API_TIMEOUT_DLT_EVENT", event.getEndpointId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "timeoutType", event.getTimeoutType(), "timeoutDurationMs", event.getTimeoutDurationMs(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "API Timeout Event Dead Letter Event",
                String.format("API timeout monitoring for %s sent to DLT: %s",
                    event.getEndpointId(), exceptionMessage),
                Map.of("endpointId", event.getEndpointId(), "topic", topic,
                    "correlationId", correlationId, "timeoutType", event.getTimeoutType())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessAPIRecoveryImpact(APITimeoutEvent event, String correlationId) {
        timeoutCount.incrementAndGet();
        timeoutCounter.increment();

        // Update average timeout duration
        double currentAvg = averageTimeoutDuration.get();
        double newAvg = (currentAvg + event.getTimeoutDurationMs()) / 2.0;
        averageTimeoutDuration.set(newAvg);

        // Alert if too many timeouts
        if (timeoutCount.get() > 10) {
            try {
                notificationService.sendExecutiveAlert(
                    "CRITICAL: Multiple API Timeouts",
                    String.format("API timeout count: %d. API performance review required.",
                        timeoutCount.get()),
                    "CRITICAL"
                );
                // Reset counter after alert
                timeoutCount.set(0);
            } catch (Exception ex) {
                log.error("Failed to send API recovery impact alert: {}", ex.getMessage());
            }
        }
    }

    private void handleConnectionTimeout(APITimeoutEvent event, String correlationId) {
        createTimeoutRecord(event, "CONNECTION_TIMEOUT", correlationId);

        // Connection pool optimization
        kafkaTemplate.send("api-connection-pool-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "CONNECTION_TIMEOUT_OPTIMIZATION",
            "timeoutDuration", event.getTimeoutDurationMs(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Increase connection timeout
        kafkaTemplate.send("api-timeout-adjustment", Map.of(
            "endpointId", event.getEndpointId(),
            "timeoutType", "CONNECTION",
            "adjustmentType", "INCREASE",
            "currentTimeout", event.getCurrentTimeoutMs(),
            "proposedTimeout", event.getCurrentTimeoutMs() * 1.5,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Network diagnostics
        kafkaTemplate.send("api-network-diagnostics", Map.of(
            "endpointId", event.getEndpointId(),
            "diagnosticType", "CONNECTION_TIMEOUT_ANALYSIS",
            "targetHost", event.getTargetHost(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordAPITimeout("CONNECTION_TIMEOUT", event.getEndpointId());
    }

    private void handleReadTimeout(APITimeoutEvent event, String correlationId) {
        createTimeoutRecord(event, "READ_TIMEOUT", correlationId);

        // Performance optimization
        kafkaTemplate.send("api-performance-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "READ_TIMEOUT_OPTIMIZATION",
            "timeoutDuration", event.getTimeoutDurationMs(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Query optimization if database-related
        if (event.getUpstreamServices().contains("database")) {
            kafkaTemplate.send("database-query-optimization", Map.of(
                "endpointId", event.getEndpointId(),
                "optimizationType", "READ_TIMEOUT_QUERY_OPTIMIZATION",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Caching strategy enhancement
        kafkaTemplate.send("api-caching-enhancement", Map.of(
            "endpointId", event.getEndpointId(),
            "enhancementType", "READ_TIMEOUT_CACHING",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordAPITimeout("READ_TIMEOUT", event.getEndpointId());
    }

    private void handleWriteTimeout(APITimeoutEvent event, String correlationId) {
        createTimeoutRecord(event, "WRITE_TIMEOUT", correlationId);

        // Write operation optimization
        kafkaTemplate.send("api-write-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "WRITE_TIMEOUT_OPTIMIZATION",
            "timeoutDuration", event.getTimeoutDurationMs(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Batch processing optimization
        kafkaTemplate.send("api-batch-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "WRITE_TIMEOUT_BATCHING",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Asynchronous processing consideration
        kafkaTemplate.send("async-processing-analysis", Map.of(
            "endpointId", event.getEndpointId(),
            "analysisType", "WRITE_TIMEOUT_ASYNC_OPPORTUNITY",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordAPITimeout("WRITE_TIMEOUT", event.getEndpointId());
    }

    private void handleCircuitBreakerTimeout(APITimeoutEvent event, String correlationId) {
        createTimeoutRecord(event, "CIRCUIT_BREAKER_TIMEOUT", correlationId);

        // Circuit breaker configuration optimization
        kafkaTemplate.send("circuit-breaker-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "TIMEOUT_CIRCUIT_BREAKER",
            "currentTimeout", event.getCurrentTimeoutMs(),
            "failureThreshold", event.getCircuitBreakerFailureThreshold(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Fallback mechanism enhancement
        kafkaTemplate.send("fallback-mechanism-enhancement", Map.of(
            "endpointId", event.getEndpointId(),
            "enhancementType", "CIRCUIT_BREAKER_TIMEOUT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("API Circuit Breaker Timeout",
            String.format("Circuit breaker timeout for endpoint %s: %dms",
                event.getEndpointId(), event.getTimeoutDurationMs()),
            "HIGH");

        metricsService.recordAPITimeout("CIRCUIT_BREAKER_TIMEOUT", event.getEndpointId());
    }

    private void handleRateLimitTimeout(APITimeoutEvent event, String correlationId) {
        createTimeoutRecord(event, "RATE_LIMIT_TIMEOUT", correlationId);

        // Rate limiting optimization
        kafkaTemplate.send("rate-limiting-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "RATE_LIMIT_TIMEOUT_OPTIMIZATION",
            "currentRateLimit", event.getCurrentRateLimit(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Queue management optimization
        kafkaTemplate.send("api-queue-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "RATE_LIMIT_QUEUE_MANAGEMENT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Backpressure handling
        kafkaTemplate.send("backpressure-handling-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "RATE_LIMIT_BACKPRESSURE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordAPITimeout("RATE_LIMIT_TIMEOUT", event.getEndpointId());
    }

    private void handleUpstreamTimeout(APITimeoutEvent event, String correlationId) {
        createTimeoutRecord(event, "UPSTREAM_TIMEOUT", correlationId);

        // Upstream service health check
        kafkaTemplate.send("upstream-service-health-checks", Map.of(
            "endpointId", event.getEndpointId(),
            "upstreamServices", event.getUpstreamServices(),
            "checkType", "TIMEOUT_ANALYSIS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Alternative upstream routing
        kafkaTemplate.send("upstream-routing-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "routingType", "UPSTREAM_TIMEOUT_ALTERNATIVE",
            "failedUpstream", event.getFailedUpstreamService(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Timeout propagation optimization
        kafkaTemplate.send("timeout-propagation-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "UPSTREAM_TIMEOUT_PROPAGATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordAPITimeout("UPSTREAM_TIMEOUT", event.getEndpointId());
    }

    private void handleDatabaseTimeout(APITimeoutEvent event, String correlationId) {
        createTimeoutRecord(event, "DATABASE_TIMEOUT", correlationId);

        // Database performance analysis
        kafkaTemplate.send("database-performance-analysis", Map.of(
            "endpointId", event.getEndpointId(),
            "analysisType", "API_DATABASE_TIMEOUT",
            "databaseQuery", event.getDatabaseQuery(),
            "timeoutDuration", event.getTimeoutDurationMs(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Query optimization
        kafkaTemplate.send("database-query-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "API_TIMEOUT_QUERY_OPTIMIZATION",
            "slowQuery", event.getDatabaseQuery(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Connection pool optimization
        kafkaTemplate.send("database-connection-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "API_DATABASE_TIMEOUT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordAPITimeout("DATABASE_TIMEOUT", event.getEndpointId());
    }

    private void handleCacheTimeout(APITimeoutEvent event, String correlationId) {
        createTimeoutRecord(event, "CACHE_TIMEOUT", correlationId);

        // Cache performance optimization
        kafkaTemplate.send("cache-performance-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "API_CACHE_TIMEOUT",
            "cacheInstance", event.getCacheInstance(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Cache fallback strategy
        kafkaTemplate.send("cache-fallback-strategy", Map.of(
            "endpointId", event.getEndpointId(),
            "strategyType", "CACHE_TIMEOUT_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Cache warming
        kafkaTemplate.send("cache-warming-requests", Map.of(
            "endpointId", event.getEndpointId(),
            "warmingType", "CACHE_TIMEOUT_PREVENTION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordAPITimeout("CACHE_TIMEOUT", event.getEndpointId());
    }

    private void handleExternalServiceTimeout(APITimeoutEvent event, String correlationId) {
        createTimeoutRecord(event, "EXTERNAL_SERVICE_TIMEOUT", correlationId);

        // External service health monitoring
        kafkaTemplate.send("external-service-health-monitoring", Map.of(
            "endpointId", event.getEndpointId(),
            "externalService", event.getExternalService(),
            "monitoringType", "TIMEOUT_ANALYSIS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Retry strategy optimization
        kafkaTemplate.send("retry-strategy-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "EXTERNAL_SERVICE_TIMEOUT",
            "externalService", event.getExternalService(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Circuit breaker for external service
        kafkaTemplate.send("external-service-circuit-breaker", Map.of(
            "endpointId", event.getEndpointId(),
            "externalService", event.getExternalService(),
            "circuitBreakerType", "TIMEOUT_PROTECTION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordAPITimeout("EXTERNAL_SERVICE_TIMEOUT", event.getEndpointId());
    }

    private void handleProcessingTimeout(APITimeoutEvent event, String correlationId) {
        createTimeoutRecord(event, "PROCESSING_TIMEOUT", correlationId);

        // Processing optimization
        kafkaTemplate.send("api-processing-optimization", Map.of(
            "endpointId", event.getEndpointId(),
            "optimizationType", "PROCESSING_TIMEOUT_OPTIMIZATION",
            "processingSteps", event.getProcessingSteps(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Algorithm optimization
        kafkaTemplate.send("algorithm-optimization-analysis", Map.of(
            "endpointId", event.getEndpointId(),
            "analysisType", "PROCESSING_TIMEOUT_ALGORITHM",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Parallel processing consideration
        kafkaTemplate.send("parallel-processing-analysis", Map.of(
            "endpointId", event.getEndpointId(),
            "analysisType", "PROCESSING_TIMEOUT_PARALLELIZATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordAPITimeout("PROCESSING_TIMEOUT", event.getEndpointId());
    }

    private void handleGenericAPITimeout(APITimeoutEvent event, String correlationId) {
        createTimeoutRecord(event, "GENERIC_TIMEOUT", correlationId);

        // Log for investigation
        auditService.logAPIEvent("UNKNOWN_API_TIMEOUT", event.getEndpointId(),
            Map.of("timeoutType", event.getTimeoutType(), "timeoutDurationMs", event.getTimeoutDurationMs(),
                "httpMethod", event.getHttpMethod(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown API Timeout",
            String.format("Unknown API timeout for endpoint %s: %s",
                event.getEndpointId(), event.getTimeoutType()),
            "MEDIUM");

        metricsService.recordAPITimeout("GENERIC_TIMEOUT", event.getEndpointId());
    }

    private void createTimeoutRecord(APITimeoutEvent event, String timeoutType, String correlationId) {
        try {
            APITimeoutRecord record = APITimeoutRecord.builder()
                .endpointId(event.getEndpointId())
                .httpMethod(event.getHttpMethod())
                .timeoutType(timeoutType)
                .timeoutDurationMs(event.getTimeoutDurationMs())
                .currentTimeoutMs(event.getCurrentTimeoutMs())
                .responseStatusCode(event.getResponseStatusCode())
                .requestSize(event.getRequestSize())
                .upstreamServices(event.getUpstreamServices())
                .timeoutTime(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

            timeoutRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to create API timeout record: {}", e.getMessage());
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