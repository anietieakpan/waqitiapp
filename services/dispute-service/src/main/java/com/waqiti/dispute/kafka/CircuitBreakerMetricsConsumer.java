package com.waqiti.dispute.kafka;

import com.waqiti.dispute.service.TransactionDisputeService;
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
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class CircuitBreakerMetricsConsumer {

    private final TransactionDisputeService disputeService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("circuit_breaker_metrics_processed_total")
            .description("Total number of successfully processed circuit breaker metrics events")
            .register(meterRegistry);
        errorCounter = Counter.builder("circuit_breaker_metrics_errors_total")
            .description("Total number of circuit breaker metrics processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("circuit_breaker_metrics_processing_duration")
            .description("Time taken to process circuit breaker metrics events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"circuit-breaker-metrics"},
        groupId = "dispute-circuit-breaker-metrics-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "circuit-breaker-metrics", fallbackMethod = "handleCircuitBreakerMetricsEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCircuitBreakerMetricsEvent(
            @Payload Map<String, Object> metricsEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String circuitBreakerName = (String) metricsEvent.get("circuitBreakerName");
        String correlationId = String.format("cb-metrics-%s-p%d-o%d", circuitBreakerName, partition, offset);
        String eventKey = String.format("%s-%s-%s", circuitBreakerName,
            metricsEvent.get("metricType"), metricsEvent.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing circuit breaker metrics: name={}, metricType={}, value={}",
                circuitBreakerName, metricsEvent.get("metricType"), metricsEvent.get("metricValue"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Process circuit breaker metrics
            processCircuitBreakerMetrics(metricsEvent, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSystemEvent("CIRCUIT_BREAKER_METRICS_PROCESSED", circuitBreakerName,
                Map.of("circuitBreakerName", circuitBreakerName, "metricType", metricsEvent.get("metricType"),
                    "metricValue", metricsEvent.get("metricValue"),
                    "aggregationType", metricsEvent.get("aggregationType"),
                    "timeWindow", metricsEvent.get("timeWindow"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process circuit breaker metrics: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("circuit-breaker-metrics-fallback-events", Map.of(
                "originalEvent", metricsEvent, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCircuitBreakerMetricsEventFallback(
            Map<String, Object> metricsEvent,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String circuitBreakerName = (String) metricsEvent.get("circuitBreakerName");
        String correlationId = String.format("cb-metrics-fallback-%s-p%d-o%d", circuitBreakerName, partition, offset);

        log.error("Circuit breaker fallback triggered for metrics: name={}, error={}",
            circuitBreakerName, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("circuit-breaker-metrics-dlq", Map.of(
            "originalEvent", metricsEvent,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Circuit Breaker Metrics Circuit Breaker Triggered",
                String.format("Circuit breaker %s metrics processing failed: %s", circuitBreakerName, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCircuitBreakerMetricsEvent(
            @Payload Map<String, Object> metricsEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String circuitBreakerName = (String) metricsEvent.get("circuitBreakerName");
        String correlationId = String.format("dlt-cb-metrics-%s-%d", circuitBreakerName, System.currentTimeMillis());

        log.error("Dead letter topic handler - Circuit breaker metrics permanently failed: name={}, topic={}, error={}",
            circuitBreakerName, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSystemEvent("CIRCUIT_BREAKER_METRICS_DLT_EVENT", circuitBreakerName,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "circuitBreakerName", circuitBreakerName, "metricsEvent", metricsEvent,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Circuit Breaker Metrics Dead Letter Event",
                String.format("Circuit breaker %s metrics sent to DLT: %s", circuitBreakerName, exceptionMessage),
                Map.of("circuitBreakerName", circuitBreakerName, "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
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
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processCircuitBreakerMetrics(Map<String, Object> metricsEvent, String correlationId) {
        String circuitBreakerName = (String) metricsEvent.get("circuitBreakerName");
        String metricType = (String) metricsEvent.get("metricType");
        Object metricValue = metricsEvent.get("metricValue");
        String aggregationType = (String) metricsEvent.getOrDefault("aggregationType", "INSTANT");
        String timeWindow = (String) metricsEvent.getOrDefault("timeWindow", "1m");

        log.info("Processing circuit breaker metrics: name={}, type={}, value={}, aggregation={}, window={}",
            circuitBreakerName, metricType, metricValue, aggregationType, timeWindow);

        // Process based on metric type
        switch (metricType) {
            case "FAILURE_RATE":
                processFailureRateMetric(circuitBreakerName, metricValue, metricsEvent, correlationId);
                break;

            case "SUCCESS_RATE":
                processSuccessRateMetric(circuitBreakerName, metricValue, metricsEvent, correlationId);
                break;

            case "SLOW_CALL_RATE":
                processSlowCallRateMetric(circuitBreakerName, metricValue, metricsEvent, correlationId);
                break;

            case "CALL_COUNT":
                processCallCountMetric(circuitBreakerName, metricValue, metricsEvent, correlationId);
                break;

            case "RESPONSE_TIME":
                processResponseTimeMetric(circuitBreakerName, metricValue, metricsEvent, correlationId);
                break;

            case "STATE_DURATION":
                processStateDurationMetric(circuitBreakerName, metricValue, metricsEvent, correlationId);
                break;

            case "THROUGHPUT":
                processThroughputMetric(circuitBreakerName, metricValue, metricsEvent, correlationId);
                break;

            case "ERROR_COUNT":
                processErrorCountMetric(circuitBreakerName, metricValue, metricsEvent, correlationId);
                break;

            default:
                processGenericMetric(circuitBreakerName, metricType, metricValue, metricsEvent, correlationId);
                break;
        }

        // Update internal metrics
        updateInternalMetrics(circuitBreakerName, metricType, metricValue);

        // Check for threshold violations
        checkThresholdViolations(circuitBreakerName, metricType, metricValue, correlationId);

        // Perform metric aggregation if needed
        performMetricAggregation(circuitBreakerName, metricsEvent, correlationId);

        log.info("Circuit breaker metrics processed: name={}, type={}", circuitBreakerName, metricType);
    }

    private void processFailureRateMetric(String circuitBreakerName, Object metricValue,
                                         Map<String, Object> metricsEvent, String correlationId) {
        Double failureRate = convertToDouble(metricValue);
        if (failureRate == null) return;

        log.info("Processing failure rate metric: name={}, rate={}%", circuitBreakerName, failureRate);

        // Record failure rate
        meterRegistry.gauge("circuit_breaker_failure_rate",
            Map.of("circuit_breaker", circuitBreakerName), failureRate);

        // Check critical thresholds
        if (failureRate > 80.0) {
            alertCriticalFailureRate(circuitBreakerName, failureRate, correlationId);
        } else if (failureRate > 50.0) {
            alertHighFailureRate(circuitBreakerName, failureRate, correlationId);
        }

        // Store for trend analysis
        storeMetricForTrendAnalysis(circuitBreakerName, "FAILURE_RATE", failureRate, correlationId);

        // Trigger auto-scaling if supported
        if (shouldTriggerAutoScaling(failureRate)) {
            triggerAutoScaling(circuitBreakerName, "HIGH_FAILURE_RATE", correlationId);
        }
    }

    private void processSuccessRateMetric(String circuitBreakerName, Object metricValue,
                                         Map<String, Object> metricsEvent, String correlationId) {
        Double successRate = convertToDouble(metricValue);
        if (successRate == null) return;

        log.info("Processing success rate metric: name={}, rate={}%", circuitBreakerName, successRate);

        // Record success rate
        meterRegistry.gauge("circuit_breaker_success_rate",
            Map.of("circuit_breaker", circuitBreakerName), successRate);

        // Check for low success rate
        if (successRate < 20.0) {
            alertLowSuccessRate(circuitBreakerName, successRate, correlationId);
        }

        // Check for recovery patterns
        if (successRate > 95.0) {
            checkRecoveryPatterns(circuitBreakerName, successRate, correlationId);
        }
    }

    private void processSlowCallRateMetric(String circuitBreakerName, Object metricValue,
                                          Map<String, Object> metricsEvent, String correlationId) {
        Double slowCallRate = convertToDouble(metricValue);
        if (slowCallRate == null) return;

        log.info("Processing slow call rate metric: name={}, rate={}%", circuitBreakerName, slowCallRate);

        // Record slow call rate
        meterRegistry.gauge("circuit_breaker_slow_call_rate",
            Map.of("circuit_breaker", circuitBreakerName), slowCallRate);

        // Check performance thresholds
        if (slowCallRate > 60.0) {
            alertHighSlowCallRate(circuitBreakerName, slowCallRate, correlationId);
        }

        // Trigger performance analysis
        if (slowCallRate > 40.0) {
            triggerPerformanceAnalysis(circuitBreakerName, slowCallRate, correlationId);
        }
    }

    private void processCallCountMetric(String circuitBreakerName, Object metricValue,
                                       Map<String, Object> metricsEvent, String correlationId) {
        Long callCount = convertToLong(metricValue);
        if (callCount == null) return;

        log.info("Processing call count metric: name={}, count={}", circuitBreakerName, callCount);

        // Record call count
        meterRegistry.gauge("circuit_breaker_call_count",
            Map.of("circuit_breaker", circuitBreakerName), callCount);

        // Check for unusual traffic patterns
        if (isUnusualTrafficPattern(callCount)) {
            alertUnusualTraffic(circuitBreakerName, callCount, correlationId);
        }

        // Update capacity planning metrics
        updateCapacityPlanningMetrics(circuitBreakerName, callCount, correlationId);
    }

    private void processResponseTimeMetric(String circuitBreakerName, Object metricValue,
                                          Map<String, Object> metricsEvent, String correlationId) {
        Double responseTime = convertToDouble(metricValue);
        if (responseTime == null) return;

        String percentile = (String) metricsEvent.getOrDefault("percentile", "avg");
        log.info("Processing response time metric: name={}, time={}ms, percentile={}",
            circuitBreakerName, responseTime, percentile);

        // Record response time by percentile
        meterRegistry.gauge("circuit_breaker_response_time",
            Map.of("circuit_breaker", circuitBreakerName, "percentile", percentile), responseTime);

        // Check SLA thresholds
        if (responseTime > 5000.0) { // 5 seconds
            alertSLAViolation(circuitBreakerName, responseTime, percentile, correlationId);
        }

        // Trigger optimization recommendations
        if (responseTime > 2000.0) {
            generateOptimizationRecommendations(circuitBreakerName, responseTime, correlationId);
        }
    }

    private void processStateDurationMetric(String circuitBreakerName, Object metricValue,
                                           Map<String, Object> metricsEvent, String correlationId) {
        Long duration = convertToLong(metricValue);
        if (duration == null) return;

        String state = (String) metricsEvent.get("state");
        log.info("Processing state duration metric: name={}, state={}, duration={}ms",
            circuitBreakerName, state, duration);

        // Record state duration
        meterRegistry.gauge("circuit_breaker_state_duration",
            Map.of("circuit_breaker", circuitBreakerName, "state", state), duration);

        // Check for prolonged open states
        if ("OPEN".equals(state) && duration > 300000) { // 5 minutes
            alertProlongedOpenState(circuitBreakerName, duration, correlationId);
        }

        // Analyze state transition patterns
        analyzeStateTransitionPatterns(circuitBreakerName, state, duration, correlationId);
    }

    private void processThroughputMetric(String circuitBreakerName, Object metricValue,
                                        Map<String, Object> metricsEvent, String correlationId) {
        Double throughput = convertToDouble(metricValue);
        if (throughput == null) return;

        log.info("Processing throughput metric: name={}, throughput={} req/s", circuitBreakerName, throughput);

        // Record throughput
        meterRegistry.gauge("circuit_breaker_throughput",
            Map.of("circuit_breaker", circuitBreakerName), throughput);

        // Check for throughput degradation
        if (isThroughputDegraded(throughput)) {
            alertThroughputDegradation(circuitBreakerName, throughput, correlationId);
        }

        // Update performance baselines
        updatePerformanceBaselines(circuitBreakerName, throughput, correlationId);
    }

    private void processErrorCountMetric(String circuitBreakerName, Object metricValue,
                                        Map<String, Object> metricsEvent, String correlationId) {
        Long errorCount = convertToLong(metricValue);
        if (errorCount == null) return;

        String errorType = (String) metricsEvent.getOrDefault("errorType", "UNKNOWN");
        log.info("Processing error count metric: name={}, type={}, count={}",
            circuitBreakerName, errorType, errorCount);

        // Record error count by type
        meterRegistry.gauge("circuit_breaker_error_count",
            Map.of("circuit_breaker", circuitBreakerName, "error_type", errorType), errorCount);

        // Check for error spikes
        if (isErrorSpike(errorCount)) {
            alertErrorSpike(circuitBreakerName, errorType, errorCount, correlationId);
        }

        // Categorize and analyze errors
        categorizeAndAnalyzeErrors(circuitBreakerName, errorType, errorCount, correlationId);
    }

    private void processGenericMetric(String circuitBreakerName, String metricType, Object metricValue,
                                     Map<String, Object> metricsEvent, String correlationId) {
        log.info("Processing generic metric: name={}, type={}, value={}",
            circuitBreakerName, metricType, metricValue);

        // Store generic metric
        storeGenericMetric(circuitBreakerName, metricType, metricValue, correlationId);

        // Check for anomalies
        checkForAnomalies(circuitBreakerName, metricType, metricValue, correlationId);
    }

    private void updateInternalMetrics(String circuitBreakerName, String metricType, Object metricValue) {
        meterRegistry.counter("circuit_breaker_metrics_received_total",
            "circuit_breaker", circuitBreakerName,
            "metric_type", metricType).increment();
    }

    private void checkThresholdViolations(String circuitBreakerName, String metricType,
                                         Object metricValue, String correlationId) {
        // Implementation depends on metric type and configured thresholds
        // This is a simplified example
        if ("FAILURE_RATE".equals(metricType)) {
            Double value = convertToDouble(metricValue);
            if (value != null && value > 75.0) {
                reportThresholdViolation(circuitBreakerName, metricType, value, 75.0, correlationId);
            }
        }
    }

    private void performMetricAggregation(String circuitBreakerName, Map<String, Object> metricsEvent, String correlationId) {
        // Send to metric aggregation service
        kafkaTemplate.send("metric-aggregation", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "metricsEvent", metricsEvent,
            "aggregationType", "CIRCUIT_BREAKER",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    // Helper methods for metric processing
    private Double convertToDouble(Object value) {
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                log.warn("Failed to convert to double: {}", value);
                return null;
            }
        }
        return null;
    }

    private Long convertToLong(Object value) {
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                log.warn("Failed to convert to long: {}", value);
                return null;
            }
        }
        return null;
    }

    private void alertCriticalFailureRate(String circuitBreakerName, Double failureRate, String correlationId) {
        notificationService.sendCriticalAlert(
            "Critical Circuit Breaker Failure Rate",
            String.format("Circuit breaker %s has critical failure rate: %.2f%%", circuitBreakerName, failureRate),
            Map.of("circuitBreakerName", circuitBreakerName, "failureRate", failureRate, "severity", "CRITICAL")
        );
    }

    private void alertHighFailureRate(String circuitBreakerName, Double failureRate, String correlationId) {
        notificationService.sendHighPriorityAlert(
            "High Circuit Breaker Failure Rate",
            String.format("Circuit breaker %s has high failure rate: %.2f%%", circuitBreakerName, failureRate),
            Map.of("circuitBreakerName", circuitBreakerName, "failureRate", failureRate, "severity", "HIGH")
        );
    }

    private void storeMetricForTrendAnalysis(String circuitBreakerName, String metricType,
                                           Object metricValue, String correlationId) {
        kafkaTemplate.send("metric-trend-analysis", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "metricType", metricType,
            "metricValue", metricValue,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private boolean shouldTriggerAutoScaling(Double failureRate) {
        return failureRate > 70.0; // Threshold for auto-scaling
    }

    private void triggerAutoScaling(String circuitBreakerName, String reason, String correlationId) {
        kafkaTemplate.send("auto-scaling-triggers", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "reason", reason,
            "action", "SCALE_OUT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    // Additional helper methods would be implemented similarly...
    private void alertLowSuccessRate(String circuitBreakerName, Double successRate, String correlationId) {
        notificationService.sendHighPriorityAlert(
            "Low Circuit Breaker Success Rate",
            String.format("Circuit breaker %s has low success rate: %.2f%%", circuitBreakerName, successRate),
            Map.of("circuitBreakerName", circuitBreakerName, "successRate", successRate)
        );
    }

    private void checkRecoveryPatterns(String circuitBreakerName, Double successRate, String correlationId) {
        // Implementation for recovery pattern analysis
        kafkaTemplate.send("recovery-pattern-analysis", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "successRate", successRate,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void alertHighSlowCallRate(String circuitBreakerName, Double slowCallRate, String correlationId) {
        notificationService.sendOperationalAlert(
            "High Circuit Breaker Slow Call Rate",
            String.format("Circuit breaker %s has high slow call rate: %.2f%%", circuitBreakerName, slowCallRate),
            "MEDIUM"
        );
    }

    private void triggerPerformanceAnalysis(String circuitBreakerName, Double slowCallRate, String correlationId) {
        kafkaTemplate.send("performance-analysis", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "slowCallRate", slowCallRate,
            "analysisType", "SLOW_CALL_INVESTIGATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private boolean isUnusualTrafficPattern(Long callCount) {
        // Simplified logic - in real implementation, this would compare against historical patterns
        return callCount > 10000 || callCount < 10;
    }

    private void alertUnusualTraffic(String circuitBreakerName, Long callCount, String correlationId) {
        notificationService.sendOperationalAlert(
            "Unusual Circuit Breaker Traffic Pattern",
            String.format("Circuit breaker %s has unusual call count: %d", circuitBreakerName, callCount),
            "MEDIUM"
        );
    }

    private void updateCapacityPlanningMetrics(String circuitBreakerName, Long callCount, String correlationId) {
        kafkaTemplate.send("capacity-planning-metrics", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "callCount", callCount,
            "metricType", "TRAFFIC_VOLUME",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void alertSLAViolation(String circuitBreakerName, Double responseTime, String percentile, String correlationId) {
        notificationService.sendHighPriorityAlert(
            "Circuit Breaker SLA Violation",
            String.format("Circuit breaker %s SLA violation: %s response time %.2fms",
                circuitBreakerName, percentile, responseTime),
            Map.of("circuitBreakerName", circuitBreakerName, "responseTime", responseTime, "percentile", percentile)
        );
    }

    private void generateOptimizationRecommendations(String circuitBreakerName, Double responseTime, String correlationId) {
        kafkaTemplate.send("optimization-recommendations", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "responseTime", responseTime,
            "recommendationType", "PERFORMANCE_OPTIMIZATION",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void alertProlongedOpenState(String circuitBreakerName, Long duration, String correlationId) {
        notificationService.sendCriticalAlert(
            "Prolonged Circuit Breaker Open State",
            String.format("Circuit breaker %s has been in OPEN state for %d ms", circuitBreakerName, duration),
            Map.of("circuitBreakerName", circuitBreakerName, "duration", duration, "state", "OPEN")
        );
    }

    private void analyzeStateTransitionPatterns(String circuitBreakerName, String state, Long duration, String correlationId) {
        kafkaTemplate.send("state-transition-analysis", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "state", state,
            "duration", duration,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private boolean isThroughputDegraded(Double throughput) {
        // Simplified logic - in real implementation, compare against baselines
        return throughput < 100.0; // requests per second threshold
    }

    private void alertThroughputDegradation(String circuitBreakerName, Double throughput, String correlationId) {
        notificationService.sendOperationalAlert(
            "Circuit Breaker Throughput Degradation",
            String.format("Circuit breaker %s throughput degraded: %.2f req/s", circuitBreakerName, throughput),
            "MEDIUM"
        );
    }

    private void updatePerformanceBaselines(String circuitBreakerName, Double throughput, String correlationId) {
        kafkaTemplate.send("performance-baseline-updates", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "throughput", throughput,
            "updateType", "THROUGHPUT_BASELINE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private boolean isErrorSpike(Long errorCount) {
        // Simplified logic - in real implementation, compare against historical data
        return errorCount > 100;
    }

    private void alertErrorSpike(String circuitBreakerName, String errorType, Long errorCount, String correlationId) {
        notificationService.sendHighPriorityAlert(
            "Circuit Breaker Error Spike",
            String.format("Circuit breaker %s error spike: %s errors = %d", circuitBreakerName, errorType, errorCount),
            Map.of("circuitBreakerName", circuitBreakerName, "errorType", errorType, "errorCount", errorCount)
        );
    }

    private void categorizeAndAnalyzeErrors(String circuitBreakerName, String errorType, Long errorCount, String correlationId) {
        kafkaTemplate.send("error-categorization", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "errorType", errorType,
            "errorCount", errorCount,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void storeGenericMetric(String circuitBreakerName, String metricType, Object metricValue, String correlationId) {
        kafkaTemplate.send("generic-metric-storage", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "metricType", metricType,
            "metricValue", metricValue,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void checkForAnomalies(String circuitBreakerName, String metricType, Object metricValue, String correlationId) {
        kafkaTemplate.send("anomaly-detection", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "metricType", metricType,
            "metricValue", metricValue,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void reportThresholdViolation(String circuitBreakerName, String metricType,
                                         Double actualValue, Double thresholdValue, String correlationId) {
        notificationService.sendOperationalAlert(
            "Circuit Breaker Threshold Violation",
            String.format("Circuit breaker %s threshold violation: %s = %.2f (threshold: %.2f)",
                circuitBreakerName, metricType, actualValue, thresholdValue),
            "MEDIUM"
        );

        kafkaTemplate.send("threshold-violations", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "metricType", metricType,
            "actualValue", actualValue,
            "thresholdValue", thresholdValue,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }
}