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
public class CircuitBreakerEvaluationsConsumer {

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
        successCounter = Counter.builder("circuit_breaker_evaluations_processed_total")
            .description("Total number of successfully processed circuit breaker evaluation events")
            .register(meterRegistry);
        errorCounter = Counter.builder("circuit_breaker_evaluations_errors_total")
            .description("Total number of circuit breaker evaluation processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("circuit_breaker_evaluations_processing_duration")
            .description("Time taken to process circuit breaker evaluation events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"circuit-breaker-evaluations"},
        groupId = "dispute-circuit-breaker-evaluations-group",
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
    @CircuitBreaker(name = "circuit-breaker-evaluations", fallbackMethod = "handleCircuitBreakerEvaluationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCircuitBreakerEvaluationEvent(
            @Payload Map<String, Object> evaluationEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String circuitBreakerName = (String) evaluationEvent.get("circuitBreakerName");
        String correlationId = String.format("cb-eval-%s-p%d-o%d", circuitBreakerName, partition, offset);
        String eventKey = String.format("%s-%s-%s", circuitBreakerName,
            evaluationEvent.get("evaluationType"), evaluationEvent.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing circuit breaker evaluation: name={}, state={}, evaluationType={}",
                circuitBreakerName, evaluationEvent.get("currentState"), evaluationEvent.get("evaluationType"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Process circuit breaker evaluation
            processCircuitBreakerEvaluation(evaluationEvent, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSystemEvent("CIRCUIT_BREAKER_EVALUATION_PROCESSED", circuitBreakerName,
                Map.of("circuitBreakerName", circuitBreakerName, "currentState", evaluationEvent.get("currentState"),
                    "evaluationType", evaluationEvent.get("evaluationType"),
                    "failureRate", evaluationEvent.get("failureRate"),
                    "slowCallRate", evaluationEvent.get("slowCallRate"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process circuit breaker evaluation: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("circuit-breaker-evaluation-fallback-events", Map.of(
                "originalEvent", evaluationEvent, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCircuitBreakerEvaluationEventFallback(
            Map<String, Object> evaluationEvent,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String circuitBreakerName = (String) evaluationEvent.get("circuitBreakerName");
        String correlationId = String.format("cb-eval-fallback-%s-p%d-o%d", circuitBreakerName, partition, offset);

        log.error("Circuit breaker fallback triggered for evaluation: name={}, error={}",
            circuitBreakerName, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("circuit-breaker-evaluations-dlq", Map.of(
            "originalEvent", evaluationEvent,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Circuit Breaker Evaluation Circuit Breaker Triggered",
                String.format("Circuit breaker %s evaluation processing failed: %s", circuitBreakerName, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCircuitBreakerEvaluationEvent(
            @Payload Map<String, Object> evaluationEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String circuitBreakerName = (String) evaluationEvent.get("circuitBreakerName");
        String correlationId = String.format("dlt-cb-eval-%s-%d", circuitBreakerName, System.currentTimeMillis());

        log.error("Dead letter topic handler - Circuit breaker evaluation permanently failed: name={}, topic={}, error={}",
            circuitBreakerName, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logSystemEvent("CIRCUIT_BREAKER_EVALUATION_DLT_EVENT", circuitBreakerName,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "circuitBreakerName", circuitBreakerName, "evaluationEvent", evaluationEvent,
                "correlationId", correlationId, "requiresManualIntervention", true,
                "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Circuit Breaker Evaluation Dead Letter Event",
                String.format("Circuit breaker %s evaluation sent to DLT: %s", circuitBreakerName, exceptionMessage),
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

    private void processCircuitBreakerEvaluation(Map<String, Object> evaluationEvent, String correlationId) {
        String circuitBreakerName = (String) evaluationEvent.get("circuitBreakerName");
        String currentState = (String) evaluationEvent.get("currentState");
        String evaluationType = (String) evaluationEvent.getOrDefault("evaluationType", "STANDARD");
        Double failureRate = (Double) evaluationEvent.get("failureRate");
        Double slowCallRate = (Double) evaluationEvent.get("slowCallRate");
        Integer failedCalls = (Integer) evaluationEvent.get("failedCalls");
        Integer slowCalls = (Integer) evaluationEvent.get("slowCalls");

        log.info("Processing circuit breaker evaluation: name={}, state={}, failureRate={}, slowCallRate={}",
            circuitBreakerName, currentState, failureRate, slowCallRate);

        // Process based on evaluation type
        switch (evaluationType) {
            case "HEALTH_CHECK":
                processHealthCheckEvaluation(evaluationEvent, correlationId);
                break;

            case "PERFORMANCE_ANALYSIS":
                processPerformanceAnalysis(evaluationEvent, correlationId);
                break;

            case "THRESHOLD_ANALYSIS":
                processThresholdAnalysis(evaluationEvent, correlationId);
                break;

            case "STATE_TRANSITION":
                processStateTransitionEvaluation(evaluationEvent, correlationId);
                break;

            case "TREND_ANALYSIS":
                processTrendAnalysis(evaluationEvent, correlationId);
                break;

            default:
                processStandardEvaluation(evaluationEvent, correlationId);
                break;
        }

        // Handle critical states
        handleCriticalStates(evaluationEvent, correlationId);

        // Generate recommendations if needed
        generateRecommendations(evaluationEvent, correlationId);

        // Update circuit breaker metrics
        updateCircuitBreakerMetrics(circuitBreakerName, currentState, failureRate, slowCallRate);

        log.info("Circuit breaker evaluation processed: name={}, state={}", circuitBreakerName, currentState);
    }

    private void processHealthCheckEvaluation(Map<String, Object> evaluationEvent, String correlationId) {
        String circuitBreakerName = (String) evaluationEvent.get("circuitBreakerName");
        String currentState = (String) evaluationEvent.get("currentState");

        log.info("Processing health check evaluation: name={}, state={}", circuitBreakerName, currentState);

        // Evaluate circuit breaker health
        evaluateCircuitBreakerHealth(circuitBreakerName, evaluationEvent, correlationId);

        // Check if intervention is needed
        if (isInterventionNeeded(evaluationEvent)) {
            triggerIntervention(circuitBreakerName, "HEALTH_DEGRADATION", correlationId);
        }

        // Update health status
        updateHealthStatus(circuitBreakerName, evaluationEvent, correlationId);
    }

    private void processPerformanceAnalysis(Map<String, Object> evaluationEvent, String correlationId) {
        String circuitBreakerName = (String) evaluationEvent.get("circuitBreakerName");
        Double avgResponseTime = (Double) evaluationEvent.get("averageResponseTime");
        Double p95ResponseTime = (Double) evaluationEvent.get("p95ResponseTime");

        log.info("Processing performance analysis: name={}, avgResponseTime={}, p95ResponseTime={}",
            circuitBreakerName, avgResponseTime, p95ResponseTime);

        // Analyze performance trends
        analyzePerformanceTrends(circuitBreakerName, evaluationEvent, correlationId);

        // Check for performance degradation
        if (isPerformanceDegraded(evaluationEvent)) {
            alertPerformanceDegradation(circuitBreakerName, evaluationEvent, correlationId);
        }

        // Generate performance recommendations
        generatePerformanceRecommendations(circuitBreakerName, evaluationEvent, correlationId);
    }

    private void processThresholdAnalysis(Map<String, Object> evaluationEvent, String correlationId) {
        String circuitBreakerName = (String) evaluationEvent.get("circuitBreakerName");
        Double failureThreshold = (Double) evaluationEvent.get("failureThreshold");
        Double slowCallThreshold = (Double) evaluationEvent.get("slowCallThreshold");

        log.info("Processing threshold analysis: name={}, failureThreshold={}, slowCallThreshold={}",
            circuitBreakerName, failureThreshold, slowCallThreshold);

        // Analyze threshold effectiveness
        analyzeThresholdEffectiveness(circuitBreakerName, evaluationEvent, correlationId);

        // Recommend threshold adjustments
        recommendThresholdAdjustments(circuitBreakerName, evaluationEvent, correlationId);

        // Update threshold configurations if needed
        if (shouldUpdateThresholds(evaluationEvent)) {
            updateThresholdConfigurations(circuitBreakerName, evaluationEvent, correlationId);
        }
    }

    private void processStateTransitionEvaluation(Map<String, Object> evaluationEvent, String correlationId) {
        String circuitBreakerName = (String) evaluationEvent.get("circuitBreakerName");
        String previousState = (String) evaluationEvent.get("previousState");
        String currentState = (String) evaluationEvent.get("currentState");

        log.info("Processing state transition evaluation: name={}, transition={}->{}",
            circuitBreakerName, previousState, currentState);

        // Analyze state transition patterns
        analyzeStateTransitionPatterns(circuitBreakerName, evaluationEvent, correlationId);

        // Handle state-specific actions
        handleStateSpecificActions(circuitBreakerName, currentState, correlationId);

        // Log state transition
        logStateTransition(circuitBreakerName, previousState, currentState, correlationId);
    }

    private void processTrendAnalysis(Map<String, Object> evaluationEvent, String correlationId) {
        String circuitBreakerName = (String) evaluationEvent.get("circuitBreakerName");

        log.info("Processing trend analysis: name={}", circuitBreakerName);

        // Analyze failure trends
        analyzeFailureTrends(circuitBreakerName, evaluationEvent, correlationId);

        // Analyze call volume trends
        analyzeCallVolumeTrends(circuitBreakerName, evaluationEvent, correlationId);

        // Predict future behavior
        predictFutureBehavior(circuitBreakerName, evaluationEvent, correlationId);

        // Generate trend-based recommendations
        generateTrendBasedRecommendations(circuitBreakerName, evaluationEvent, correlationId);
    }

    private void processStandardEvaluation(Map<String, Object> evaluationEvent, String correlationId) {
        String circuitBreakerName = (String) evaluationEvent.get("circuitBreakerName");

        log.info("Processing standard evaluation: name={}", circuitBreakerName);

        // Basic evaluation processing
        performBasicEvaluation(circuitBreakerName, evaluationEvent, correlationId);

        // Standard health checks
        performStandardHealthChecks(circuitBreakerName, evaluationEvent, correlationId);
    }

    private void handleCriticalStates(Map<String, Object> evaluationEvent, String correlationId) {
        String circuitBreakerName = (String) evaluationEvent.get("circuitBreakerName");
        String currentState = (String) evaluationEvent.get("currentState");
        Double failureRate = (Double) evaluationEvent.get("failureRate");

        if ("OPEN".equals(currentState)) {
            handleOpenState(circuitBreakerName, evaluationEvent, correlationId);
        }

        if (failureRate != null && failureRate > 80.0) {
            handleHighFailureRate(circuitBreakerName, failureRate, correlationId);
        }

        // Check for consecutive failures
        Integer consecutiveFailures = (Integer) evaluationEvent.get("consecutiveFailures");
        if (consecutiveFailures != null && consecutiveFailures > 10) {
            handleConsecutiveFailures(circuitBreakerName, consecutiveFailures, correlationId);
        }
    }

    private void handleOpenState(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        log.warn("Circuit breaker is in OPEN state: {}", circuitBreakerName);

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Circuit Breaker in OPEN State",
            String.format("Circuit breaker %s is in OPEN state, blocking all calls", circuitBreakerName),
            Map.of("circuitBreakerName", circuitBreakerName, "state", "OPEN")
        );

        // Trigger recovery procedures
        triggerRecoveryProcedures(circuitBreakerName, correlationId);

        // Escalate to operations team
        escalateToOperationsTeam(circuitBreakerName, "OPEN_STATE", correlationId);
    }

    private void handleHighFailureRate(String circuitBreakerName, Double failureRate, String correlationId) {
        log.warn("High failure rate detected: name={}, rate={}%", circuitBreakerName, failureRate);

        // Send high priority alert
        notificationService.sendHighPriorityAlert(
            "High Circuit Breaker Failure Rate",
            String.format("Circuit breaker %s has high failure rate: %.2f%%", circuitBreakerName, failureRate),
            Map.of("circuitBreakerName", circuitBreakerName, "failureRate", failureRate)
        );

        // Trigger failure analysis
        triggerFailureAnalysis(circuitBreakerName, failureRate, correlationId);
    }

    private void generateRecommendations(Map<String, Object> evaluationEvent, String correlationId) {
        String circuitBreakerName = (String) evaluationEvent.get("circuitBreakerName");

        kafkaTemplate.send("circuit-breaker-recommendations", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "evaluationEvent", evaluationEvent,
            "recommendationType", "EVALUATION_BASED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void updateCircuitBreakerMetrics(String circuitBreakerName, String currentState,
                                            Double failureRate, Double slowCallRate) {
        meterRegistry.counter("circuit_breaker_evaluations_total",
            "circuit_breaker", circuitBreakerName,
            "state", currentState).increment();

        if (failureRate != null) {
            meterRegistry.gauge("circuit_breaker_failure_rate",
                Map.of("circuit_breaker", circuitBreakerName), failureRate);
        }

        if (slowCallRate != null) {
            meterRegistry.gauge("circuit_breaker_slow_call_rate",
                Map.of("circuit_breaker", circuitBreakerName), slowCallRate);
        }
    }

    // Helper methods for various evaluation processes
    private void evaluateCircuitBreakerHealth(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        kafkaTemplate.send("circuit-breaker-health-evaluation", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "evaluationEvent", evaluationEvent,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private boolean isInterventionNeeded(Map<String, Object> evaluationEvent) {
        Double failureRate = (Double) evaluationEvent.get("failureRate");
        String currentState = (String) evaluationEvent.get("currentState");

        return (failureRate != null && failureRate > 75.0) || "OPEN".equals(currentState);
    }

    private void triggerIntervention(String circuitBreakerName, String reason, String correlationId) {
        kafkaTemplate.send("circuit-breaker-interventions", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "reason", reason,
            "priority", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void updateHealthStatus(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        kafkaTemplate.send("circuit-breaker-health-updates", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "healthStatus", calculateHealthStatus(evaluationEvent),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private String calculateHealthStatus(Map<String, Object> evaluationEvent) {
        Double failureRate = (Double) evaluationEvent.get("failureRate");
        String currentState = (String) evaluationEvent.get("currentState");

        if ("OPEN".equals(currentState)) return "CRITICAL";
        if (failureRate != null && failureRate > 50.0) return "DEGRADED";
        if (failureRate != null && failureRate > 25.0) return "WARNING";
        return "HEALTHY";
    }

    // Additional helper methods would be implemented similarly...
    private void analyzePerformanceTrends(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        // Implementation for performance trend analysis
    }

    private boolean isPerformanceDegraded(Map<String, Object> evaluationEvent) {
        Double avgResponseTime = (Double) evaluationEvent.get("averageResponseTime");
        return avgResponseTime != null && avgResponseTime > 5000; // 5 seconds threshold
    }

    private void alertPerformanceDegradation(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        notificationService.sendOperationalAlert(
            "Circuit Breaker Performance Degradation",
            String.format("Performance degradation detected for circuit breaker %s", circuitBreakerName),
            "MEDIUM"
        );
    }

    private void generatePerformanceRecommendations(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        // Implementation for performance recommendations
    }

    private void analyzeThresholdEffectiveness(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        // Implementation for threshold analysis
    }

    private void recommendThresholdAdjustments(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        // Implementation for threshold recommendations
    }

    private boolean shouldUpdateThresholds(Map<String, Object> evaluationEvent) {
        // Logic to determine if thresholds should be updated
        return false; // Placeholder
    }

    private void updateThresholdConfigurations(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        // Implementation for threshold updates
    }

    private void analyzeStateTransitionPatterns(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        // Implementation for state transition analysis
    }

    private void handleStateSpecificActions(String circuitBreakerName, String currentState, String correlationId) {
        // Implementation for state-specific actions
    }

    private void logStateTransition(String circuitBreakerName, String previousState, String currentState, String correlationId) {
        auditService.logSystemEvent("CIRCUIT_BREAKER_STATE_TRANSITION", circuitBreakerName,
            Map.of("circuitBreakerName", circuitBreakerName, "previousState", previousState,
                "currentState", currentState, "correlationId", correlationId, "timestamp", Instant.now()));
    }

    private void analyzeFailureTrends(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        // Implementation for failure trend analysis
    }

    private void analyzeCallVolumeTrends(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        // Implementation for call volume analysis
    }

    private void predictFutureBehavior(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        // Implementation for behavior prediction
    }

    private void generateTrendBasedRecommendations(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        // Implementation for trend-based recommendations
    }

    private void performBasicEvaluation(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        // Implementation for basic evaluation
    }

    private void performStandardHealthChecks(String circuitBreakerName, Map<String, Object> evaluationEvent, String correlationId) {
        // Implementation for standard health checks
    }

    private void handleConsecutiveFailures(String circuitBreakerName, Integer consecutiveFailures, String correlationId) {
        log.warn("High consecutive failures detected: name={}, failures={}", circuitBreakerName, consecutiveFailures);

        notificationService.sendHighPriorityAlert(
            "High Consecutive Circuit Breaker Failures",
            String.format("Circuit breaker %s has %d consecutive failures", circuitBreakerName, consecutiveFailures),
            Map.of("circuitBreakerName", circuitBreakerName, "consecutiveFailures", consecutiveFailures)
        );
    }

    private void triggerRecoveryProcedures(String circuitBreakerName, String correlationId) {
        kafkaTemplate.send("circuit-breaker-recovery", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "recoveryType", "AUTOMATIC",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void escalateToOperationsTeam(String circuitBreakerName, String reason, String correlationId) {
        notificationService.sendOperationalAlert(
            "Circuit Breaker Escalation",
            String.format("Circuit breaker %s requires operations team intervention: %s", circuitBreakerName, reason),
            "HIGH"
        );
    }

    private void triggerFailureAnalysis(String circuitBreakerName, Double failureRate, String correlationId) {
        kafkaTemplate.send("failure-analysis", Map.of(
            "circuitBreakerName", circuitBreakerName,
            "failureRate", failureRate,
            "analysisType", "HIGH_FAILURE_RATE",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }
}