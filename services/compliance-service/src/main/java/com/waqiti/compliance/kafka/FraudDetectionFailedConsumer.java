package com.waqiti.compliance.kafka;

import com.waqiti.common.events.FraudDetectionFailedEvent;
import com.waqiti.compliance.domain.ComplianceIncident;
import com.waqiti.compliance.repository.ComplianceIncidentRepository;
import com.waqiti.compliance.service.ComplianceService;
import com.waqiti.compliance.service.RegulatoryReportingService;
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
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionFailedConsumer {

    private final ComplianceIncidentRepository incidentRepository;
    private final ComplianceService complianceService;
    private final RegulatoryReportingService reportingService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Counter criticalFailuresCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("fraud_detection_failed_processed_total")
            .description("Total number of successfully processed fraud detection failure events")
            .register(meterRegistry);
        errorCounter = Counter.builder("fraud_detection_failed_errors_total")
            .description("Total number of fraud detection failure processing errors")
            .register(meterRegistry);
        criticalFailuresCounter = Counter.builder("fraud_detection_critical_failures_total")
            .description("Total number of critical fraud detection failures")
            .register(meterRegistry);
        processingTimer = Timer.builder("fraud_detection_failed_processing_duration")
            .description("Time taken to process fraud detection failure events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"compliance.fraud.detection.failed", "fraud-detection-failures", "fraud-system-failures"},
        groupId = "compliance-fraud-detection-failed-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 8000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "fraud-detection-failed", fallbackMethod = "handleFraudDetectionFailedFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 8000))
    public void handleFraudDetectionFailedEvent(
            @Payload FraudDetectionFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("fraud-fail-%s-p%d-o%d", event.getTransactionId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getTransactionId(), event.getFailureType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.error("Processing critical fraud detection failure: transactionId={}, failureType={}, severity={}",
                event.getTransactionId(), event.getFailureType(), event.getSeverity());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getFailureType()) {
                case SYSTEM_UNAVAILABLE:
                    handleSystemUnavailableFailure(event, correlationId);
                    break;

                case MODEL_TIMEOUT:
                    handleModelTimeoutFailure(event, correlationId);
                    break;

                case DATA_CORRUPTION:
                    handleDataCorruptionFailure(event, correlationId);
                    break;

                case INTEGRATION_FAILURE:
                    handleIntegrationFailure(event, correlationId);
                    break;

                case PROCESSING_ERROR:
                    handleProcessingErrorFailure(event, correlationId);
                    break;

                case CONFIGURATION_ERROR:
                    handleConfigurationErrorFailure(event, correlationId);
                    break;

                default:
                    handleUnknownFailure(event, correlationId);
                    break;
            }

            // Create compliance incident for regulatory tracking
            createComplianceIncident(event, correlationId);

            // Check if this requires immediate regulatory notification
            if (isRegulatoryNotificationRequired(event)) {
                reportingService.createImmediateRegulatoryReport(event, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logComplianceEvent("FRAUD_DETECTION_FAILURE_PROCESSED", event.getTransactionId(),
                Map.of("failureType", event.getFailureType(), "severity", event.getSeverity(),
                    "accountId", event.getAccountId(), "correlationId", correlationId,
                    "timestamp", Instant.now(), "requiresRegulatoryReport", isRegulatoryNotificationRequired(event)));

            successCounter.increment();
            if ("CRITICAL".equals(event.getSeverity())) {
                criticalFailuresCounter.increment();
            }
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process fraud detection failure event: {}", e.getMessage(), e);

            // Send executive escalation for critical failures
            if ("CRITICAL".equals(event.getSeverity())) {
                escalateToExecutiveTeam(event, correlationId, e);
            }

            // Send fallback event
            kafkaTemplate.send("fraud-detection-failure-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "requiresExecutiveAttention", "CRITICAL".equals(event.getSeverity())));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleFraudDetectionFailedFallback(
            FraudDetectionFailedEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("fraud-fail-fallback-%s-p%d-o%d", event.getTransactionId(), partition, offset);

        log.error("Circuit breaker fallback triggered for fraud detection failure: transactionId={}, error={}",
            event.getTransactionId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("fraud-detection-failed-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical alert to compliance and executive teams
        try {
            notificationService.sendCriticalAlert(
                "Critical Fraud Detection System Failure",
                String.format("Fraud detection failure processing failed for transaction %s: %s",
                    event.getTransactionId(), ex.getMessage()),
                "CRITICAL"
            );

            // Escalate to executive team for critical failures
            escalateToExecutiveTeam(event, correlationId, ex);

        } catch (Exception notificationEx) {
            log.error("Failed to send critical fraud detection failure alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltFraudDetectionFailedEvent(
            @Payload FraudDetectionFailedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-fraud-fail-%s-%d", event.getTransactionId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Fraud detection failure permanently failed: transactionId={}, topic={}, error={}",
            event.getTransactionId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logComplianceEvent("FRAUD_DETECTION_FAILURE_DLT_EVENT", event.getTransactionId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "failureType", event.getFailureType(), "correlationId", correlationId,
                "requiresImmediateAction", true, "severity", "CRITICAL", "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Emergency: Fraud Detection Failure in DLT",
                String.format("Critical fraud detection failure sent to DLT: Transaction %s, Error: %s",
                    event.getTransactionId(), exceptionMessage),
                Map.of("transactionId", event.getTransactionId(), "topic", topic, "correlationId", correlationId,
                    "severity", "EMERGENCY", "requiresImmediateAction", true)
            );

            // Mandatory executive escalation for DLT events
            escalateToExecutiveTeam(event, correlationId, new Exception(exceptionMessage));

        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
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

    private void handleSystemUnavailableFailure(FraudDetectionFailedEvent event, String correlationId) {
        log.error("Fraud detection system unavailable: transactionId={}, duration={}ms",
            event.getTransactionId(), event.getFailureDurationMs());

        complianceService.markTransactionForManualReview(event.getTransactionId(),
            "FRAUD_SYSTEM_UNAVAILABLE", correlationId);

        // Send immediate alert for system unavailability
        notificationService.sendCriticalAlert(
            "Fraud Detection System Unavailable",
            String.format("Fraud detection system unavailable for transaction %s. Duration: %dms",
                event.getTransactionId(), event.getFailureDurationMs()),
            "HIGH"
        );

        kafkaTemplate.send("fraud-system-health-alerts", Map.of(
            "alertType", "SYSTEM_UNAVAILABLE",
            "transactionId", event.getTransactionId(),
            "duration", event.getFailureDurationMs(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleModelTimeoutFailure(FraudDetectionFailedEvent event, String correlationId) {
        log.warn("Fraud detection model timeout: transactionId={}, modelVersion={}",
            event.getTransactionId(), event.getModelVersion());

        complianceService.markTransactionForManualReview(event.getTransactionId(),
            "MODEL_TIMEOUT", correlationId);

        kafkaTemplate.send("fraud-model-performance-alerts", Map.of(
            "alertType", "MODEL_TIMEOUT",
            "transactionId", event.getTransactionId(),
            "modelVersion", event.getModelVersion(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleDataCorruptionFailure(FraudDetectionFailedEvent event, String correlationId) {
        log.error("Data corruption detected in fraud detection: transactionId={}", event.getTransactionId());

        complianceService.quarantineTransaction(event.getTransactionId(),
            "DATA_CORRUPTION_DETECTED", correlationId);

        // Immediate executive escalation for data corruption
        escalateToExecutiveTeam(event, correlationId,
            new Exception("Data corruption detected in fraud detection system"));

        kafkaTemplate.send("data-integrity-alerts", Map.of(
            "alertType", "FRAUD_DATA_CORRUPTION",
            "transactionId", event.getTransactionId(),
            "accountId", event.getAccountId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleIntegrationFailure(FraudDetectionFailedEvent event, String correlationId) {
        log.error("Integration failure in fraud detection: transactionId={}, service={}",
            event.getTransactionId(), event.getFailedService());

        complianceService.markTransactionForManualReview(event.getTransactionId(),
            "INTEGRATION_FAILURE", correlationId);

        kafkaTemplate.send("integration-health-alerts", Map.of(
            "alertType", "FRAUD_INTEGRATION_FAILURE",
            "transactionId", event.getTransactionId(),
            "failedService", event.getFailedService(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
    }

    private void handleProcessingErrorFailure(FraudDetectionFailedEvent event, String correlationId) {
        log.error("Processing error in fraud detection: transactionId={}, error={}",
            event.getTransactionId(), event.getErrorMessage());

        complianceService.markTransactionForManualReview(event.getTransactionId(),
            "PROCESSING_ERROR", correlationId);
    }

    private void handleConfigurationErrorFailure(FraudDetectionFailedEvent event, String correlationId) {
        log.error("Configuration error in fraud detection: transactionId={}", event.getTransactionId());

        complianceService.quarantineTransaction(event.getTransactionId(),
            "CONFIGURATION_ERROR", correlationId);

        // Configuration errors require immediate attention
        notificationService.sendCriticalAlert(
            "Fraud Detection Configuration Error",
            String.format("Configuration error detected for transaction %s", event.getTransactionId()),
            "HIGH"
        );
    }

    private void handleUnknownFailure(FraudDetectionFailedEvent event, String correlationId) {
        log.error("Unknown fraud detection failure: transactionId={}, type={}",
            event.getTransactionId(), event.getFailureType());

        complianceService.quarantineTransaction(event.getTransactionId(),
            "UNKNOWN_FAILURE", correlationId);
    }

    private void createComplianceIncident(FraudDetectionFailedEvent event, String correlationId) {
        ComplianceIncident incident = ComplianceIncident.builder()
            .incidentId(UUID.randomUUID().toString())
            .type("FRAUD_DETECTION_FAILURE")
            .severity(event.getSeverity())
            .transactionId(event.getTransactionId())
            .accountId(event.getAccountId())
            .description(String.format("Fraud detection failure: %s - %s",
                event.getFailureType(), event.getErrorMessage()))
            .status("OPEN")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .requiresRegulatoryReport(isRegulatoryNotificationRequired(event))
            .build();

        incidentRepository.save(incident);

        log.info("Compliance incident created: incidentId={}, type=FRAUD_DETECTION_FAILURE",
            incident.getIncidentId());
    }

    private boolean isRegulatoryNotificationRequired(FraudDetectionFailedEvent event) {
        return "CRITICAL".equals(event.getSeverity()) ||
               "DATA_CORRUPTION".equals(event.getFailureType()) ||
               "SYSTEM_UNAVAILABLE".equals(event.getFailureType()) ||
               event.getFailureDurationMs() > 300000; // 5 minutes
    }

    private void escalateToExecutiveTeam(FraudDetectionFailedEvent event, String correlationId, Exception error) {
        try {
            notificationService.sendExecutiveEscalation(
                "Critical Fraud Detection System Failure",
                String.format("URGENT: Fraud detection failure requiring executive attention.\n" +
                    "Transaction: %s\n" +
                    "Account: %s\n" +
                    "Failure Type: %s\n" +
                    "Severity: %s\n" +
                    "Error: %s\n" +
                    "Correlation ID: %s\n" +
                    "Time: %s",
                    event.getTransactionId(), event.getAccountId(), event.getFailureType(),
                    event.getSeverity(), error.getMessage(), correlationId, Instant.now()),
                Map.of(
                    "priority", "EMERGENCY",
                    "category", "FRAUD_SYSTEM_FAILURE",
                    "transactionId", event.getTransactionId(),
                    "correlationId", correlationId,
                    "requiresImmediateAction", true
                )
            );
        } catch (Exception ex) {
            log.error("Failed to escalate to executive team: {}", ex.getMessage());
        }
    }
}