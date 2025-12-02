package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.frauddetection.domain.FraudAlert;
import com.waqiti.frauddetection.repository.FraudAlertRepository;
import com.waqiti.frauddetection.service.FraudAlertService;
import com.waqiti.frauddetection.service.FraudResponseService;
import com.waqiti.frauddetection.service.AlertTriageService;
import com.waqiti.frauddetection.metrics.FraudMetricsService;
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
public class FraudAlertConsumer {

    private final FraudAlertRepository fraudAlertRepository;
    private final FraudAlertService fraudAlertService;
    private final FraudResponseService fraudResponseService;
    private final AlertTriageService alertTriageService;
    private final FraudMetricsService metricsService;
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
        successCounter = Counter.builder("fraud_alert_processed_total")
            .description("Total number of successfully processed fraud alert events")
            .register(meterRegistry);
        errorCounter = Counter.builder("fraud_alert_errors_total")
            .description("Total number of fraud alert processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("fraud_alert_processing_duration")
            .description("Time taken to process fraud alert events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"fraud-alert"},
        groupId = "fraud-alert-service-group",
        containerFactory = "emergencyFraudKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 2000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "fraud-alert", fallbackMethod = "handleFraudAlertEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 2, backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 1000))
    public void handleFraudAlertEvent(
            @Payload FraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("fraud-alert-%s-p%d-o%d", event.getAlertId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getAlertId(), event.getEventType(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.warn("Processing FRAUD ALERT: alertId={}, eventType={}, severity={}, riskScore={}",
                event.getAlertId(), event.getEventType(), event.getSeverity(), event.getRiskScore());

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (event.getEventType()) {
                case FRAUD_ALERT_TRIGGERED:
                    processFraudAlertTriggered(event, correlationId);
                    break;

                case CRITICAL_FRAUD_ALERT:
                    processCriticalFraudAlert(event, correlationId);
                    break;

                case FRAUD_ALERT_ESCALATED:
                    processFraudAlertEscalated(event, correlationId);
                    break;

                case FRAUD_ALERT_ACKNOWLEDGED:
                    processFraudAlertAcknowledged(event, correlationId);
                    break;

                case FRAUD_ALERT_INVESTIGATED:
                    processFraudAlertInvestigated(event, correlationId);
                    break;

                case FRAUD_ALERT_RESOLVED:
                    processFraudAlertResolved(event, correlationId);
                    break;

                case FRAUD_ALERT_FALSE_POSITIVE:
                    processFraudAlertFalsePositive(event, correlationId);
                    break;

                case FRAUD_ALERT_CONFIRMED:
                    processFraudAlertConfirmed(event, correlationId);
                    break;

                case FRAUD_ALERT_EXPIRED:
                    processFraudAlertExpired(event, correlationId);
                    break;

                default:
                    log.error("Unknown fraud alert event type: {}", event.getEventType());
                    processUnknownFraudAlertEvent(event, correlationId);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logFraudEvent("FRAUD_ALERT_EVENT_PROCESSED", event.getAlertId(),
                Map.of("eventType", event.getEventType(), "severity", event.getSeverity(),
                    "riskScore", event.getRiskScore(), "userId", event.getUserId(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("CRITICAL: Failed to process fraud alert event: {}", e.getMessage(), e);

            // Send emergency fallback event
            kafkaTemplate.send("fraud-alert-emergency-fallback", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "priority", "EMERGENCY", "retryCount", 0, "maxRetries", 2));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleFraudAlertEventFallback(
            FraudAlertEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("fraud-alert-fallback-%s-p%d-o%d", event.getAlertId(), partition, offset);

        log.error("EMERGENCY: Circuit breaker fallback triggered for fraud alert: alertId={}, error={}",
            event.getAlertId(), ex.getMessage());

        // Create emergency incident
        fraudAlertService.createEmergencyIncident(
            "FRAUD_ALERT_CIRCUIT_BREAKER",
            String.format("EMERGENCY: Fraud alert circuit breaker triggered for alert %s", event.getAlertId()),
            "EMERGENCY",
            Map.of("alertId", event.getAlertId(), "eventType", event.getEventType(),
                "severity", event.getSeverity(), "riskScore", event.getRiskScore(),
                "error", ex.getMessage(), "correlationId", correlationId)
        );

        // Send to emergency queue
        kafkaTemplate.send("fraud-alert-emergency-response", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "priority", "EMERGENCY",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "EMERGENCY: Fraud Alert Circuit Breaker",
                String.format("EMERGENCY: Fraud alert processing failed for alert %s (Risk: %d): %s",
                    event.getAlertId(), event.getRiskScore(), ex.getMessage()),
                "EMERGENCY"
            );
        } catch (Exception notificationEx) {
            log.error("CRITICAL: Failed to send emergency alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltFraudAlertEvent(
            @Payload FraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-fraud-alert-%s-%d", event.getAlertId(), System.currentTimeMillis());

        log.error("EMERGENCY: Fraud alert sent to DLT: alertId={}, topic={}, error={}",
            event.getAlertId(), topic, exceptionMessage);

        // Trigger emergency response
        fraudResponseService.triggerEmergencyResponse(
            "FRAUD_ALERT_DLT_EVENT",
            String.format("EMERGENCY: Fraud alert sent to DLT for alert %s", event.getAlertId()),
            Map.of("alertId", event.getAlertId(), "originalTopic", topic,
                "errorMessage", exceptionMessage, "severity", event.getSeverity(),
                "riskScore", event.getRiskScore(), "correlationId", correlationId,
                "requiresImmediateEmergencyAction", true)
        );

        // Save to emergency audit log
        auditService.logEmergencyFraudEvent("FRAUD_ALERT_DLT_EVENT", event.getAlertId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "severity", event.getSeverity(),
                "correlationId", correlationId, "requiresEmergencyIntervention", true,
                "timestamp", Instant.now()));

        // Send emergency escalation
        try {
            notificationService.sendEmergencyEscalation(
                "EMERGENCY: Fraud Alert DLT Event",
                String.format("EMERGENCY: Fraud alert %s sent to DLT (Risk: %d): %s",
                    event.getAlertId(), event.getRiskScore(), exceptionMessage),
                Map.of("alertId", event.getAlertId(), "riskScore", event.getRiskScore(),
                    "topic", topic, "correlationId", correlationId, "priority", "EMERGENCY")
            );
        } catch (Exception ex) {
            log.error("CRITICAL: Failed to send emergency escalation: {}", ex.getMessage());
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

    private void processFraudAlertTriggered(FraudAlertEvent event, String correlationId) {
        // Create fraud alert record
        FraudAlert alert = FraudAlert.builder()
            .alertId(event.getAlertId())
            .userId(event.getUserId())
            .accountId(event.getAccountId())
            .transactionId(event.getTransactionId())
            .alertType(event.getAlertType())
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .description(event.getDescription())
            .detectionRule(event.getDetectionRule())
            .triggeredAt(LocalDateTime.now())
            .status("TRIGGERED")
            .correlationId(correlationId)
            .build();

        fraudAlertRepository.save(alert);

        // Perform immediate triage
        String triageResult = alertTriageService.performTriage(event.getAlertId(),
            event.getAlertType(), event.getRiskScore(), event.getDescription());

        // Implement immediate response based on severity
        switch (event.getSeverity()) {
            case "CRITICAL":
                fraudResponseService.triggerCriticalResponse(event.getAlertId(), event.getUserId(),
                    event.getAccountId(), event.getTransactionId());

                kafkaTemplate.send("fraud-alert", Map.of(
                    "alertId", event.getAlertId(),
                    "eventType", "CRITICAL_FRAUD_ALERT",
                    "severity", event.getSeverity(),
                    "riskScore", event.getRiskScore(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
                break;

            case "HIGH":
                fraudResponseService.triggerHighPriorityResponse(event.getAlertId(), event.getUserId(),
                    event.getAccountId());
                break;

            case "MEDIUM":
                fraudResponseService.triggerStandardResponse(event.getAlertId(), event.getUserId());
                break;

            default:
                // Log for monitoring
                fraudResponseService.logForMonitoring(event.getAlertId(), event.getAlertType());
                break;
        }

        // Auto-assign to analyst if high risk
        if (event.getRiskScore() > 80) {
            alertTriageService.autoAssignToFraudAnalyst(event.getAlertId(), event.getAlertType());
        }

        metricsService.recordFraudAlertTriggered(event.getAlertType(), event.getSeverity(), event.getRiskScore());

        log.warn("Fraud alert triggered: alertId={}, type={}, severity={}, riskScore={}",
            event.getAlertId(), event.getAlertType(), event.getSeverity(), event.getRiskScore());
    }

    private void processCriticalFraudAlert(FraudAlertEvent event, String correlationId) {
        // Update alert status
        FraudAlert alert = fraudAlertRepository.findByAlertId(event.getAlertId())
            .orElse(createEmergencyAlert(event, correlationId));

        alert.setStatus("CRITICAL");
        alert.setCriticalEscalatedAt(LocalDateTime.now());
        alert.setEmergencyProtocol(true);
        fraudAlertRepository.save(alert);

        // Trigger emergency protocols
        fraudResponseService.triggerEmergencyProtocols(event.getAlertId(), event.getUserId(),
            event.getAccountId(), event.getTransactionId());

        // Immediate account freeze for critical alerts
        fraudResponseService.emergencyAccountFreeze(event.getAccountId(), "CRITICAL_FRAUD_ALERT");

        // Block all related transactions
        fraudResponseService.blockRelatedTransactions(event.getTransactionId(), event.getAccountId());

        // Send critical alert to fraud team
        notificationService.sendCriticalFraudAlert(
            "CRITICAL FRAUD ALERT",
            String.format("CRITICAL: Fraud alert %s triggered (Risk: %d, Type: %s)",
                event.getAlertId(), event.getRiskScore(), event.getAlertType()),
            "CRITICAL"
        );

        // Auto-escalate to senior fraud analyst
        alertTriageService.emergencyEscalateToSeniorAnalyst(event.getAlertId(), event.getAlertType());

        // Start immediate investigation
        String investigationId = fraudAlertService.startEmergencyInvestigation(event.getAlertId(),
            event.getAlertType(), event.getDescription());

        metricsService.recordCriticalFraudAlert(event.getAlertType(), event.getRiskScore());

        log.error("CRITICAL FRAUD ALERT: alertId={}, type={}, riskScore={}, investigationId={}",
            event.getAlertId(), event.getAlertType(), event.getRiskScore(), investigationId);
    }

    private void processFraudAlertEscalated(FraudAlertEvent event, String correlationId) {
        // Update alert status
        FraudAlert alert = fraudAlertRepository.findByAlertId(event.getAlertId())
            .orElse(createEmergencyAlert(event, correlationId));

        alert.setStatus("ESCALATED");
        alert.setEscalatedAt(LocalDateTime.now());
        alert.setEscalatedBy(event.getEscalatedBy());
        alert.setEscalationReason(event.getEscalationReason());
        alert.setEscalatedTo(event.getEscalatedTo());
        fraudAlertRepository.save(alert);

        // Create escalation ticket
        String escalationTicketId = fraudAlertService.createEscalationTicket(event.getAlertId(),
            event.getEscalationReason(), event.getEscalatedTo());

        // Send escalation notification
        notificationService.sendFraudEscalationAlert(
            "Fraud Alert Escalated",
            String.format("Fraud alert %s escalated to %s: %s",
                event.getAlertId(), event.getEscalatedTo(), event.getEscalationReason()),
            "HIGH"
        );

        metricsService.recordFraudAlertEscalated(event.getAlertType(), event.getEscalationReason());

        log.warn("Fraud alert escalated: alertId={}, escalatedTo={}, reason={}",
            event.getAlertId(), event.getEscalatedTo(), event.getEscalationReason());
    }

    private void processFraudAlertAcknowledged(FraudAlertEvent event, String correlationId) {
        // Update alert status
        FraudAlert alert = fraudAlertRepository.findByAlertId(event.getAlertId())
            .orElse(createEmergencyAlert(event, correlationId));

        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.setAcknowledgedBy(event.getAcknowledgedBy());
        fraudAlertRepository.save(alert);

        // Start investigation timer
        fraudAlertService.startInvestigationTimer(event.getAlertId(), event.getAlertType());

        metricsService.recordFraudAlertAcknowledged(event.getAlertType());

        log.info("Fraud alert acknowledged: alertId={}, acknowledgedBy={}",
            event.getAlertId(), event.getAcknowledgedBy());
    }

    private void processFraudAlertInvestigated(FraudAlertEvent event, String correlationId) {
        // Update alert status
        FraudAlert alert = fraudAlertRepository.findByAlertId(event.getAlertId())
            .orElse(createEmergencyAlert(event, correlationId));

        alert.setStatus("UNDER_INVESTIGATION");
        alert.setInvestigationStartedAt(LocalDateTime.now());
        alert.setInvestigatedBy(event.getInvestigatedBy());
        alert.setInvestigationNotes(event.getInvestigationNotes());
        fraudAlertRepository.save(alert);

        // Setup investigation workflow
        fraudAlertService.setupInvestigationWorkflow(event.getAlertId(), event.getAlertType(),
            event.getInvestigatedBy());

        metricsService.recordFraudAlertInvestigationStarted(event.getAlertType());

        log.info("Fraud alert investigation started: alertId={}, investigatedBy={}",
            event.getAlertId(), event.getInvestigatedBy());
    }

    private void processFraudAlertResolved(FraudAlertEvent event, String correlationId) {
        // Update alert status
        FraudAlert alert = fraudAlertRepository.findByAlertId(event.getAlertId())
            .orElse(createEmergencyAlert(event, correlationId));

        alert.setStatus("RESOLVED");
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedBy(event.getResolvedBy());
        alert.setResolutionNotes(event.getResolutionNotes());
        alert.setResolutionAction(event.getResolutionAction());
        fraudAlertRepository.save(alert);

        // Execute resolution actions
        fraudResponseService.executeResolutionActions(event.getAlertId(), event.getResolutionAction());

        // Close investigation
        fraudAlertService.closeInvestigation(event.getAlertId(), event.getResolutionNotes());

        metricsService.recordFraudAlertResolved(event.getAlertType(), event.getResolutionAction());

        log.info("Fraud alert resolved: alertId={}, action={}, resolvedBy={}",
            event.getAlertId(), event.getResolutionAction(), event.getResolvedBy());
    }

    private void processFraudAlertFalsePositive(FraudAlertEvent event, String correlationId) {
        // Update alert status
        FraudAlert alert = fraudAlertRepository.findByAlertId(event.getAlertId())
            .orElse(createEmergencyAlert(event, correlationId));

        alert.setStatus("FALSE_POSITIVE");
        alert.setFalsePositiveConfirmedAt(LocalDateTime.now());
        alert.setFalsePositiveReason(event.getFalsePositiveReason());
        fraudAlertRepository.save(alert);

        // Update detection rules to reduce false positives
        fraudAlertService.updateDetectionRules(event.getAlertType(), event.getDescription(),
            event.getFalsePositiveReason());

        // Restore account access if restricted
        fraudResponseService.restoreAccountAccess(event.getAccountId(), "FALSE_POSITIVE_CONFIRMED");

        metricsService.recordFraudAlertFalsePositive(event.getAlertType(), event.getFalsePositiveReason());

        log.info("Fraud alert marked as false positive: alertId={}, reason={}",
            event.getAlertId(), event.getFalsePositiveReason());
    }

    private void processFraudAlertConfirmed(FraudAlertEvent event, String correlationId) {
        // Update alert status
        FraudAlert alert = fraudAlertRepository.findByAlertId(event.getAlertId())
            .orElse(createEmergencyAlert(event, correlationId));

        alert.setStatus("CONFIRMED_FRAUD");
        alert.setFraudConfirmedAt(LocalDateTime.now());
        alert.setFraudType(event.getFraudType());
        alert.setImpactAssessment(event.getImpactAssessment());
        fraudAlertRepository.save(alert);

        // Implement permanent account restrictions
        fraudResponseService.implementPermanentRestrictions(event.getAccountId(), event.getFraudType());

        // Generate fraud case
        String fraudCaseId = fraudAlertService.generateFraudCase(event.getAlertId(),
            event.getFraudType(), event.getImpactAssessment());

        // Send confirmed fraud notification
        notificationService.sendConfirmedFraudAlert(
            "Confirmed Fraud Alert",
            String.format("Fraud confirmed for alert %s: %s", event.getAlertId(), event.getFraudType()),
            "CRITICAL"
        );

        metricsService.recordFraudAlertConfirmed(event.getAlertType(), event.getFraudType());

        log.error("Fraud alert confirmed: alertId={}, fraudType={}, caseId={}",
            event.getAlertId(), event.getFraudType(), fraudCaseId);
    }

    private void processFraudAlertExpired(FraudAlertEvent event, String correlationId) {
        // Update alert status
        FraudAlert alert = fraudAlertRepository.findByAlertId(event.getAlertId())
            .orElse(createEmergencyAlert(event, correlationId));

        alert.setStatus("EXPIRED");
        alert.setExpiredAt(LocalDateTime.now());
        fraudAlertRepository.save(alert);

        // Auto-resolve if not critical
        if (!event.getSeverity().equals("CRITICAL") && !event.getSeverity().equals("HIGH")) {
            fraudAlertService.autoResolveExpiredAlert(event.getAlertId(), "ALERT_EXPIRED");
        } else {
            // Escalate expired critical alerts
            fraudAlertService.escalateExpiredCriticalAlert(event.getAlertId(), event.getAlertType());
        }

        metricsService.recordFraudAlertExpired(event.getAlertType(), event.getSeverity());

        log.warn("Fraud alert expired: alertId={}, severity={}", event.getAlertId(), event.getSeverity());
    }

    private void processUnknownFraudAlertEvent(FraudAlertEvent event, String correlationId) {
        // Create critical incident for unknown fraud alert event
        fraudAlertService.createEmergencyIncident(
            "UNKNOWN_FRAUD_ALERT_EVENT",
            String.format("CRITICAL: Unknown fraud alert event type %s for alert %s",
                event.getEventType(), event.getAlertId()),
            "CRITICAL",
            Map.of("alertId", event.getAlertId(), "unknownEventType", event.getEventType(),
                "alertType", event.getAlertType(), "severity", event.getSeverity(),
                "correlationId", correlationId)
        );

        log.error("CRITICAL: Unknown fraud alert event: alertId={}, eventType={}, alertType={}",
            event.getAlertId(), event.getEventType(), event.getAlertType());
    }

    private FraudAlert createEmergencyAlert(FraudAlertEvent event, String correlationId) {
        return FraudAlert.builder()
            .alertId(event.getAlertId())
            .userId(event.getUserId())
            .accountId(event.getAccountId())
            .transactionId(event.getTransactionId())
            .alertType(event.getAlertType())
            .severity(event.getSeverity())
            .riskScore(event.getRiskScore())
            .description(event.getDescription())
            .triggeredAt(LocalDateTime.now())
            .status("EMERGENCY_CREATED")
            .correlationId(correlationId)
            .build();
    }
}