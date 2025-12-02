package com.waqiti.infrastructure.kafka;

import com.waqiti.common.events.CircuitBreakerMetricsEvent;
import com.waqiti.infrastructure.domain.CircuitBreakerRecord;
import com.waqiti.infrastructure.repository.CircuitBreakerRecordRepository;
import com.waqiti.infrastructure.service.CircuitBreakerService;
import com.waqiti.infrastructure.service.ResilienceService;
import com.waqiti.infrastructure.service.FailureAnalysisService;
import com.waqiti.infrastructure.metrics.ResilienceMetricsService;
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
public class CircuitBreakerMetricsConsumer {

    private final CircuitBreakerRecordRepository circuitBreakerRepository;
    private final CircuitBreakerService circuitBreakerService;
    private final ResilienceService resilienceService;
    private final FailureAnalysisService failureAnalysisService;
    private final ResilienceMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Circuit breaker tracking
    private final AtomicLong openCircuitBreakers = new AtomicLong(0);
    private final AtomicDouble averageFailureRate = new AtomicDouble(0.0);

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter circuitBreakerEventCounter;
    private Timer processingTimer;
    private Gauge openCircuitBreakersGauge;
    private Gauge avgFailureRateGauge;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("circuit_breaker_metrics_processed_total")
            .description("Total number of successfully processed circuit breaker metrics events")
            .register(meterRegistry);
        errorCounter = Counter.builder("circuit_breaker_metrics_errors_total")
            .description("Total number of circuit breaker metrics processing errors")
            .register(meterRegistry);
        circuitBreakerEventCounter = Counter.builder("circuit_breaker_events_total")
            .description("Total number of circuit breaker events")
            .register(meterRegistry);
        processingTimer = Timer.builder("circuit_breaker_metrics_processing_duration")
            .description("Time taken to process circuit breaker metrics events")
            .register(meterRegistry);
        openCircuitBreakersGauge = Gauge.builder("circuit_breakers_open")
            .description("Number of open circuit breakers")
            .register(meterRegistry, openCircuitBreakers, AtomicLong::get);
        avgFailureRateGauge = Gauge.builder("average_circuit_breaker_failure_rate")
            .description("Average failure rate across all circuit breakers")
            .register(meterRegistry, averageFailureRate, AtomicDouble::get);
    }

    @KafkaListener(
        topics = {"circuit-breaker-metrics", "circuit-breaker-state-changes", "resilience-pattern-events"},
        groupId = "circuit-breaker-metrics-service-group",
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
    @CircuitBreaker(name = "circuit-breaker-metrics", fallbackMethod = "handleCircuitBreakerMetricsFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCircuitBreakerMetricsEvent(
            @Payload CircuitBreakerMetricsEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("cb-metrics-%s-p%d-o%d", event.getCircuitBreakerName(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getCircuitBreakerName(), event.getState(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing circuit breaker metrics: name={}, state={}, failureRate={}%, successRate={}%",
                event.getCircuitBreakerName(), event.getState(), event.getFailureRate(), event.getSuccessRate());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // System recovery impact assessment
            assessSystemRecoveryImpact(event, correlationId);

            switch (event.getState()) {
                case CLOSED:
                    handleClosedState(event, correlationId);
                    break;

                case OPEN:
                    handleOpenState(event, correlationId);
                    break;

                case HALF_OPEN:
                    handleHalfOpenState(event, correlationId);
                    break;

                case FORCED_OPEN:
                    handleForcedOpenState(event, correlationId);
                    break;

                case DISABLED:
                    handleDisabledState(event, correlationId);
                    break;

                default:
                    log.warn("Unknown circuit breaker state: {}", event.getState());
                    handleGenericCircuitBreakerEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logCircuitBreakerEvent("CIRCUIT_BREAKER_METRICS_PROCESSED", event.getCircuitBreakerName(),
                Map.of("state", event.getState(), "failureRate", event.getFailureRate(),
                    "successRate", event.getSuccessRate(), "numberOfCalls", event.getNumberOfCalls(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process circuit breaker metrics event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("circuit-breaker-metrics-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCircuitBreakerMetricsFallback(
            CircuitBreakerMetricsEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("cb-metrics-fallback-%s-p%d-o%d", event.getCircuitBreakerName(), partition, offset);

        log.error("Circuit breaker fallback triggered for circuit breaker metrics: name={}, error={}",
            event.getCircuitBreakerName(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("circuit-breaker-metrics-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send alert for critical circuit breakers
        if ("CRITICAL".equals(event.getServiceCriticality())) {
            try {
                notificationService.sendExecutiveAlert(
                    "Critical Circuit Breaker Metrics Processing Failure",
                    String.format("Critical circuit breaker metrics processing for %s failed: %s",
                        event.getCircuitBreakerName(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send executive alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCircuitBreakerMetricsEvent(
            @Payload CircuitBreakerMetricsEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-cb-metrics-%s-%d", event.getCircuitBreakerName(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Circuit breaker metrics permanently failed: name={}, topic={}, error={}",
            event.getCircuitBreakerName(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logCircuitBreakerEvent("CIRCUIT_BREAKER_METRICS_DLT_EVENT", event.getCircuitBreakerName(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "state", event.getState(), "failureRate", event.getFailureRate(),
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Circuit Breaker Metrics Dead Letter Event",
                String.format("Circuit breaker metrics for %s sent to DLT: %s",
                    event.getCircuitBreakerName(), exceptionMessage),
                Map.of("circuitBreakerName", event.getCircuitBreakerName(), "topic", topic,
                    "correlationId", correlationId, "state", event.getState())
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private void assessSystemRecoveryImpact(CircuitBreakerMetricsEvent event, String correlationId) {
        if ("OPEN".equals(event.getState()) || "FORCED_OPEN".equals(event.getState())) {
            openCircuitBreakers.incrementAndGet();
            circuitBreakerEventCounter.increment();

            // Alert if too many open circuit breakers
            if (openCircuitBreakers.get() > 5) {
                try {
                    notificationService.sendExecutiveAlert(
                        "CRITICAL: Multiple Circuit Breakers Open",
                        String.format("Open circuit breakers count: %d. System resilience degraded.",
                            openCircuitBreakers.get()),
                        "CRITICAL"
                    );
                } catch (Exception ex) {
                    log.error("Failed to send system recovery impact alert: {}", ex.getMessage());
                }
            }
        }

        if ("CLOSED".equals(event.getState())) {
            long currentOpen = openCircuitBreakers.get();
            if (currentOpen > 0) {
                openCircuitBreakers.decrementAndGet();
            }
        }

        // Update average failure rate
        double currentAvg = averageFailureRate.get();
        double newAvg = (currentAvg + event.getFailureRate()) / 2.0;
        averageFailureRate.set(newAvg);
    }

    private void handleClosedState(CircuitBreakerMetricsEvent event, String correlationId) {
        createCircuitBreakerRecord(event, "CLOSED", correlationId);

        // Monitor for stability
        kafkaTemplate.send("circuit-breaker-stability-monitoring", Map.of(
            "circuitBreakerName", event.getCircuitBreakerName(),
            "monitoringType", "CLOSED_STATE_STABILITY",
            "failureRate", event.getFailureRate(),
            "successRate", event.getSuccessRate(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Performance optimization if failure rate is elevated
        if (event.getFailureRate() > 10.0) {
            kafkaTemplate.send("performance-optimization-requests", Map.of(
                "serviceName", event.getServiceName(),
                "optimizationType", "ELEVATED_FAILURE_RATE",
                "failureRate", event.getFailureRate(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Clear any recovery actions
        kafkaTemplate.send("recovery-action-completion", Map.of(
            "circuitBreakerName", event.getCircuitBreakerName(),
            "completionType", "CIRCUIT_BREAKER_CLOSED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Circuit breaker {} closed - failure rate: {}%", event.getCircuitBreakerName(), event.getFailureRate());
        metricsService.recordCircuitBreakerStateChange("CLOSED", event.getCircuitBreakerName());
    }

    private void handleOpenState(CircuitBreakerMetricsEvent event, String correlationId) {
        createCircuitBreakerRecord(event, "OPEN", correlationId);

        // Immediate failure analysis
        kafkaTemplate.send("failure-analysis-requests", Map.of(
            "circuitBreakerName", event.getCircuitBreakerName(),
            "analysisType", "CIRCUIT_BREAKER_OPEN",
            "failureRate", event.getFailureRate(),
            "numberOfFailures", event.getNumberOfFailedCalls(),
            "recentExceptions", event.getRecentExceptions(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Activate fallback mechanisms
        kafkaTemplate.send("fallback-activation-requests", Map.of(
            "serviceName", event.getServiceName(),
            "activationType", "CIRCUIT_BREAKER_OPEN",
            "circuitBreakerName", event.getCircuitBreakerName(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Health recovery planning
        kafkaTemplate.send("health-recovery-planning", Map.of(
            "serviceName", event.getServiceName(),
            "planningType", "CIRCUIT_BREAKER_RECOVERY",
            "estimatedRecoveryTime", event.getWaitDurationInOpenState(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Alert based on service criticality
        String alertLevel = "CRITICAL".equals(event.getServiceCriticality()) ? "CRITICAL" : "HIGH";
        notificationService.sendOperationalAlert("Circuit Breaker Opened",
            String.format("Circuit breaker %s opened - failure rate: %.1f%%",
                event.getCircuitBreakerName(), event.getFailureRate()),
            alertLevel);

        metricsService.recordCircuitBreakerStateChange("OPEN", event.getCircuitBreakerName());
    }

    private void handleHalfOpenState(CircuitBreakerMetricsEvent event, String correlationId) {
        createCircuitBreakerRecord(event, "HALF_OPEN", correlationId);

        // Recovery monitoring
        kafkaTemplate.send("recovery-monitoring-requests", Map.of(
            "circuitBreakerName", event.getCircuitBreakerName(),
            "monitoringType", "HALF_OPEN_RECOVERY",
            "permittedCalls", event.getPermittedNumberOfCallsInHalfOpenState(),
            "successThreshold", event.getMinimumNumberOfCalls(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Gradual load increase
        kafkaTemplate.send("load-increase-requests", Map.of(
            "serviceName", event.getServiceName(),
            "increaseType", "HALF_OPEN_GRADUAL",
            "loadPercentage", 25.0, // Start with 25% load
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Success/failure tracking
        kafkaTemplate.send("call-tracking-requests", Map.of(
            "circuitBreakerName", event.getCircuitBreakerName(),
            "trackingType", "HALF_OPEN_CALLS",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Circuit breaker {} in half-open state - testing recovery", event.getCircuitBreakerName());
        metricsService.recordCircuitBreakerStateChange("HALF_OPEN", event.getCircuitBreakerName());
    }

    private void handleForcedOpenState(CircuitBreakerMetricsEvent event, String correlationId) {
        createCircuitBreakerRecord(event, "FORCED_OPEN", correlationId);

        // Investigate forced open reason
        kafkaTemplate.send("forced-open-investigation", Map.of(
            "circuitBreakerName", event.getCircuitBreakerName(),
            "investigationType", "FORCED_OPEN_REASON",
            "forcedOpenReason", event.getForcedOpenReason(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Administrative action tracking
        kafkaTemplate.send("administrative-action-tracking", Map.of(
            "circuitBreakerName", event.getCircuitBreakerName(),
            "actionType", "FORCED_OPEN",
            "actionReason", event.getForcedOpenReason(),
            "actionBy", event.getForcedOpenBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Enhanced fallback activation
        kafkaTemplate.send("enhanced-fallback-activation", Map.of(
            "serviceName", event.getServiceName(),
            "activationType", "FORCED_OPEN_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Circuit Breaker Forced Open",
            String.format("Circuit breaker %s forced open - reason: %s",
                event.getCircuitBreakerName(), event.getForcedOpenReason()),
            "HIGH");

        metricsService.recordCircuitBreakerStateChange("FORCED_OPEN", event.getCircuitBreakerName());
    }

    private void handleDisabledState(CircuitBreakerMetricsEvent event, String correlationId) {
        createCircuitBreakerRecord(event, "DISABLED", correlationId);

        // Track disabled reason
        kafkaTemplate.send("disabled-state-tracking", Map.of(
            "circuitBreakerName", event.getCircuitBreakerName(),
            "disabledReason", event.getDisabledReason(),
            "disabledBy", event.getDisabledBy(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Risk assessment without circuit breaker protection
        kafkaTemplate.send("risk-assessment-requests", Map.of(
            "serviceName", event.getServiceName(),
            "assessmentType", "CIRCUIT_BREAKER_DISABLED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Alternative resilience patterns
        kafkaTemplate.send("alternative-resilience-activation", Map.of(
            "serviceName", event.getServiceName(),
            "activationType", "CIRCUIT_BREAKER_REPLACEMENT",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        notificationService.sendOperationalAlert("Circuit Breaker Disabled",
            String.format("Circuit breaker %s disabled - reason: %s",
                event.getCircuitBreakerName(), event.getDisabledReason()),
            "MEDIUM");

        metricsService.recordCircuitBreakerStateChange("DISABLED", event.getCircuitBreakerName());
    }

    private void handleGenericCircuitBreakerEvent(CircuitBreakerMetricsEvent event, String correlationId) {
        createCircuitBreakerRecord(event, "UNKNOWN", correlationId);

        // Log for investigation
        auditService.logCircuitBreakerEvent("UNKNOWN_CIRCUIT_BREAKER_STATE", event.getCircuitBreakerName(),
            Map.of("state", event.getState(), "failureRate", event.getFailureRate(),
                "successRate", event.getSuccessRate(), "correlationId", correlationId,
                "requiresInvestigation", true, "timestamp", Instant.now()));

        notificationService.sendOperationalAlert("Unknown Circuit Breaker State",
            String.format("Unknown state for circuit breaker %s: %s",
                event.getCircuitBreakerName(), event.getState()),
            "MEDIUM");

        metricsService.recordCircuitBreakerStateChange("UNKNOWN", event.getCircuitBreakerName());
    }

    private void createCircuitBreakerRecord(CircuitBreakerMetricsEvent event, String state, String correlationId) {
        try {
            CircuitBreakerRecord record = CircuitBreakerRecord.builder()
                .circuitBreakerName(event.getCircuitBreakerName())
                .serviceName(event.getServiceName())
                .state(state)
                .failureRate(event.getFailureRate())
                .successRate(event.getSuccessRate())
                .numberOfCalls(event.getNumberOfCalls())
                .numberOfFailedCalls(event.getNumberOfFailedCalls())
                .numberOfSuccessfulCalls(event.getNumberOfSuccessfulCalls())
                .recordTime(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

            circuitBreakerRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to create circuit breaker record: {}", e.getMessage());
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