package com.waqiti.frauddetection.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade Kafka consumer for authentication anomaly critical failure events
 * Handles critical failures in authentication anomaly detection systems
 *
 * Critical for: Authentication security, system reliability, fraud prevention
 * SLA: Must process critical failures within 5 seconds for immediate response
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuthAnomalyCriticalFailuresConsumer {

    private final AuthAnomalyDetectionService authAnomalyDetectionService;
    private final AuthenticationSecurityService authenticationSecurityService;
    private final IncidentResponseService incidentResponseService;
    private final FraudNotificationService fraudNotificationService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("auth_anomaly_critical_failures_processed_total")
            .description("Total number of successfully processed auth anomaly critical failure events")
            .register(meterRegistry);
        errorCounter = Counter.builder("auth_anomaly_critical_failures_errors_total")
            .description("Total number of auth anomaly critical failure processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("auth_anomaly_critical_failures_processing_duration")
            .description("Time taken to process auth anomaly critical failure events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"auth-anomaly-critical-failures", "authentication-critical-failures"},
        groupId = "fraud-auth-anomaly-critical-failures-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "auth-anomaly-critical-failures", fallbackMethod = "handleAuthAnomalyCriticalFailureEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleAuthAnomalyCriticalFailureEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("auth-anomaly-crit-%s-p%d-o%d", event.getId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing auth anomaly CRITICAL failure: id={}, type={}, userId={}",
                event.getId(), event.getEventType(), event.getData().get("userId"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            String userId = (String) event.getData().get("userId");
            String failureType = (String) event.getData().get("failureType");
            String systemComponent = (String) event.getData().get("systemComponent");

            switch (event.getEventType()) {
                case "DETECTION_SYSTEM_FAILURE":
                    handleDetectionSystemFailure(event, userId, failureType, systemComponent, correlationId);
                    break;

                case "MODEL_INFERENCE_FAILURE":
                    handleModelInferenceFailure(event, userId, failureType, systemComponent, correlationId);
                    break;

                case "DATA_PIPELINE_FAILURE":
                    handleDataPipelineFailure(event, userId, failureType, systemComponent, correlationId);
                    break;

                case "AUTHENTICATION_SERVICE_DOWN":
                    handleAuthenticationServiceDown(event, systemComponent, correlationId);
                    break;

                case "ANOMALY_THRESHOLD_BREACH":
                    handleAnomalyThresholdBreach(event, userId, correlationId);
                    break;

                case "SECURITY_ALERT_SYSTEM_FAILURE":
                    handleSecurityAlertSystemFailure(event, systemComponent, correlationId);
                    break;

                case "EMERGENCY_SHUTDOWN_TRIGGERED":
                    handleEmergencyShutdownTriggered(event, systemComponent, correlationId);
                    break;

                default:
                    log.error("Unknown auth anomaly critical failure type: {}", event.getEventType());
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("AUTH_ANOMALY_CRITICAL_FAILURE_PROCESSED", userId,
                Map.of("eventType", event.getEventType(), "failureType", failureType,
                    "systemComponent", systemComponent, "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process auth anomaly critical failure event: {}", e.getMessage(), e);

            // Send emergency fallback event
            kafkaTemplate.send("auth-anomaly-critical-failures-emergency-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "priority", "EMERGENCY", "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleAuthAnomalyCriticalFailureEventFallback(
            GenericKafkaEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("auth-anomaly-crit-fallback-%s-p%d-o%d", event.getId(), partition, offset);

        log.error("EMERGENCY: Circuit breaker fallback triggered for auth anomaly critical failure: id={}, error={}",
            event.getId(), ex.getMessage());

        // Send to emergency DLQ
        kafkaTemplate.send("auth-anomaly-critical-failures-emergency-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "EMERGENCY: Auth Anomaly Critical Failures Consumer Circuit Breaker",
                String.format("CRITICAL SYSTEM FAILURE: Auth anomaly critical failure event %s failed: %s",
                    event.getId(), ex.getMessage()),
                "EMERGENCY"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send emergency alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltAuthAnomalyCriticalFailureEvent(
            @Payload GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-auth-anomaly-crit-%s-%d", event.getId(), System.currentTimeMillis());

        log.error("EMERGENCY DLT: Auth anomaly critical failure permanently failed: id={}, topic={}, error={}",
            event.getId(), topic, exceptionMessage);

        // This is an emergency situation - critical auth failure processing failed
        auditService.logSecurityEvent("AUTH_ANOMALY_CRITICAL_FAILURE_DLT_EMERGENCY",
            String.valueOf(event.getData().get("userId")),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresImmediateEscalation", true, "emergencyLevel", "CRITICAL",
                "timestamp", Instant.now()));

        // Send emergency alert to all stakeholders
        try {
            notificationService.sendEmergencyAlert(
                "EMERGENCY: Auth Anomaly Critical Failure DLT",
                String.format("SYSTEM EMERGENCY: Critical auth anomaly failure event %s permanently failed: %s",
                    event.getId(), exceptionMessage),
                Map.of("eventId", event.getId(), "topic", topic, "correlationId", correlationId,
                    "emergencyProtocolTriggered", true)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
        }
    }

    private void handleDetectionSystemFailure(GenericKafkaEvent event, String userId, String failureType, String systemComponent, String correlationId) {
        String errorDetails = (String) event.getData().get("errorDetails");
        log.error("Detection system failure: component={}, type={}, details={}", systemComponent, failureType, errorDetails);

        // Initiate emergency response
        incidentResponseService.initiateEmergencyResponse("DETECTION_SYSTEM_FAILURE", systemComponent, errorDetails);

        // Activate backup systems
        authAnomalyDetectionService.activateBackupDetectionSystems(systemComponent);

        // Send critical alert
        fraudNotificationService.sendSystemFailureAlert(systemComponent, failureType, errorDetails, correlationId);

        log.info("Detection system failure response initiated: component={}", systemComponent);
    }

    private void handleModelInferenceFailure(GenericKafkaEvent event, String userId, String failureType, String systemComponent, String correlationId) {
        String modelName = (String) event.getData().get("modelName");
        String errorDetails = (String) event.getData().get("errorDetails");
        log.error("Model inference failure: model={}, component={}, details={}", modelName, systemComponent, errorDetails);

        // Switch to fallback model
        authAnomalyDetectionService.switchToFallbackModel(modelName, systemComponent);

        // Record model failure
        authAnomalyDetectionService.recordModelFailure(modelName, errorDetails);

        // Send model failure alert
        fraudNotificationService.sendModelFailureAlert(modelName, systemComponent, errorDetails, correlationId);

        log.info("Model inference failure handled: model={}", modelName);
    }

    private void handleDataPipelineFailure(GenericKafkaEvent event, String userId, String failureType, String systemComponent, String correlationId) {
        String pipelineName = (String) event.getData().get("pipelineName");
        String errorDetails = (String) event.getData().get("errorDetails");
        log.error("Data pipeline failure: pipeline={}, component={}, details={}", pipelineName, systemComponent, errorDetails);

        // Activate data pipeline failover
        authAnomalyDetectionService.activateDataPipelineFailover(pipelineName, systemComponent);

        // Record pipeline failure
        incidentResponseService.recordPipelineFailure(pipelineName, errorDetails);

        // Send pipeline failure alert
        fraudNotificationService.sendDataPipelineFailureAlert(pipelineName, systemComponent, errorDetails, correlationId);

        log.info("Data pipeline failure handled: pipeline={}", pipelineName);
    }

    private void handleAuthenticationServiceDown(GenericKafkaEvent event, String systemComponent, String correlationId) {
        String serviceEndpoint = (String) event.getData().get("serviceEndpoint");
        log.error("Authentication service down: component={}, endpoint={}", systemComponent, serviceEndpoint);

        // Activate emergency authentication mode
        authenticationSecurityService.activateEmergencyMode(systemComponent, serviceEndpoint);

        // Initiate service recovery
        incidentResponseService.initiateServiceRecovery(systemComponent, serviceEndpoint);

        // Send service down alert
        fraudNotificationService.sendServiceDownAlert(systemComponent, serviceEndpoint, correlationId);

        log.info("Authentication service down handled: component={}", systemComponent);
    }

    private void handleAnomalyThresholdBreach(GenericKafkaEvent event, String userId, String correlationId) {
        Double anomalyScore = (Double) event.getData().get("anomalyScore");
        Double threshold = (Double) event.getData().get("threshold");
        log.error("Critical anomaly threshold breach: userId={}, score={}, threshold={}", userId, anomalyScore, threshold);

        // Trigger immediate security response
        authenticationSecurityService.triggerSecurityResponse(userId, anomalyScore, threshold);

        // Escalate to security team
        incidentResponseService.escalateToSecurityTeam(userId, anomalyScore, correlationId);

        // Send threshold breach alert
        fraudNotificationService.sendAnomalyThresholdBreachAlert(userId, anomalyScore, threshold, correlationId);

        log.info("Anomaly threshold breach handled: userId={}", userId);
    }

    private void handleSecurityAlertSystemFailure(GenericKafkaEvent event, String systemComponent, String correlationId) {
        String errorDetails = (String) event.getData().get("errorDetails");
        log.error("Security alert system failure: component={}, details={}", systemComponent, errorDetails);

        // Activate backup alerting systems
        fraudNotificationService.activateBackupAlertingSystems(systemComponent);

        // Record alert system failure
        incidentResponseService.recordAlertSystemFailure(systemComponent, errorDetails);

        // Use alternative notification channels
        fraudNotificationService.useAlternativeNotificationChannels(systemComponent, errorDetails, correlationId);

        log.info("Security alert system failure handled: component={}", systemComponent);
    }

    private void handleEmergencyShutdownTriggered(GenericKafkaEvent event, String systemComponent, String correlationId) {
        String shutdownReason = (String) event.getData().get("shutdownReason");
        log.error("Emergency shutdown triggered: component={}, reason={}", systemComponent, shutdownReason);

        // Execute emergency shutdown protocol
        incidentResponseService.executeEmergencyShutdownProtocol(systemComponent, shutdownReason);

        // Notify all stakeholders
        fraudNotificationService.sendEmergencyShutdownNotification(systemComponent, shutdownReason, correlationId);

        // Activate disaster recovery
        incidentResponseService.activateDisasterRecovery(systemComponent, shutdownReason);

        log.info("Emergency shutdown handled: component={}", systemComponent);
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
}