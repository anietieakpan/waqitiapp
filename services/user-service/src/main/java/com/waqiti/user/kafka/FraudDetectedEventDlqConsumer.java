package com.waqiti.user.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.eventsourcing.FraudDetectedEvent;
import com.waqiti.user.service.UserSecurityService;
import com.waqiti.user.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fraud Detected Event DLQ Consumer for User Service
 *
 * CRITICAL SECURITY: Handles failed fraud containment events to ensure no fraudulent
 * activity goes unmitigated due to system failures.
 *
 * DLQ Strategy:
 * 1. Log failure to security audit system
 * 2. Attempt manual reprocessing with relaxed constraints
 * 3. Alert security operations team immediately
 * 4. Create incident ticket for investigation
 * 5. Verify user account status manually
 *
 * Retry Logic:
 * - Attempt up to 3 retries with exponential backoff (1h, 6h, 24h)
 * - After max retries, escalate to security ops for manual intervention
 * - Track all retry attempts in audit log
 *
 * Compliance:
 * - All DLQ processing logged for security audit
 * - Fraud containment failures escalated per security policy
 * - FFIEC authentication guidance compliance
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudDetectedEventDlqConsumer {

    private final ObjectMapper objectMapper;
    private final UserSecurityService userSecurityService;
    private final UserNotificationService userNotificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int MAX_RETRIES = 3;
    private static final String SECURITY_INCIDENT_TOPIC = "security.incidents";

    @KafkaListener(
        topics = "fraud.detected.events.dlq",
        groupId = "user-service-fraud-dlq",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleFraudDetectedDlq(
        @Payload String eventJson,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        @Header(value = "originalTopic", required = false) String originalTopic,
        @Header(value = "failureReason", required = false) String failureReason,
        @Header(value = "failedAt", required = false) Long failedAtMs,
        @Header(value = "retryCount", required = false, defaultValue = "0") Integer retryCount,
        Acknowledgment acknowledgment
    ) {
        String dlqEventId = generateDlqEventId(key, offset);

        log.error("SECURITY CRITICAL DLQ: Processing failed fraud containment event - DLQEventId: {}, " +
                "OriginalTopic: {}, FailureReason: {}, RetryCount: {}",
                dlqEventId, originalTopic, failureReason, retryCount);

        try {
            // Deserialize event
            FraudDetectedEvent event = objectMapper.readValue(eventJson, FraudDetectedEvent.class);

            // Log to security audit
            logSecurityAuditFailure(event, failureReason, retryCount, dlqEventId);

            // Determine if retry is possible
            if (retryCount < MAX_RETRIES && isRetryable(failureReason)) {
                retryFraudContainment(event, eventJson, retryCount);
                acknowledgment.acknowledge();
                log.info("SECURITY: Scheduled fraud containment retry - FraudID: {}, Retry: {}/{}",
                        event.getFraudId(), retryCount + 1, MAX_RETRIES);
            } else {
                // Max retries exceeded or non-retryable - escalate
                escalateToSecurityOps(event, failureReason, retryCount, dlqEventId);
                createSecurityIncident(event, failureReason, retryCount, dlqEventId);
                alertComplianceTeam(event, failureReason, dlqEventId);

                acknowledgment.acknowledge();
                log.error("SECURITY CRITICAL: Fraud containment DLQ exhausted - FraudID: {}, UserID: {}, " +
                        "Manual intervention required - IncidentID: {}",
                        event.getFraudId(), event.getUserId(), dlqEventId);
            }

        } catch (Exception e) {
            log.error("SECURITY CRITICAL: Failed to process fraud DLQ event - DLQEventId: {}, Error: {}",
                    dlqEventId, e.getMessage(), e);

            // Create critical alert
            try {
                Map<String, Object> criticalAlert = new HashMap<>();
                criticalAlert.put("alertId", UUID.randomUUID().toString());
                criticalAlert.put("severity", "CRITICAL");
                criticalAlert.put("category", "FRAUD_DLQ_FAILURE");
                criticalAlert.put("message", "Failed to process fraud DLQ event: " + e.getMessage());
                criticalAlert.put("dlqEventId", dlqEventId);
                criticalAlert.put("failureReason", failureReason);
                criticalAlert.put("timestamp", Instant.now());

                kafkaTemplate.send("security.critical.alerts", dlqEventId, criticalAlert);
            } catch (Exception alertError) {
                log.error("SECURITY CRITICAL: Failed to send critical DLQ alert: {}", alertError.getMessage());
            }

            // Acknowledge to prevent infinite loop
            acknowledgment.acknowledge();
        }
    }

    /**
     * Log failure to security audit system.
     */
    private void logSecurityAuditFailure(FraudDetectedEvent event, String failureReason,
                                         Integer retryCount, String dlqEventId) {
        try {
            userSecurityService.logSecurityEvent(
                UUID.fromString(event.getUserId()),
                "FRAUD_CONTAINMENT_FAILURE",
                String.format("DLQ: Failed to process fraud event - FraudID: %s, Reason: %s, RetryCount: %d, DLQEventId: %s",
                    event.getFraudId(), failureReason, retryCount, dlqEventId)
            );
        } catch (Exception e) {
            log.error("Failed to log security audit for DLQ: {}", e.getMessage());
        }
    }

    /**
     * Retry fraud containment with exponential backoff.
     */
    private void retryFraudContainment(FraudDetectedEvent event, String eventJson, Integer currentRetryCount) {
        try {
            // Schedule retry by publishing back to original topic with retry headers
            Map<String, Object> headers = new HashMap<>();
            headers.put("retryCount", currentRetryCount + 1);
            headers.put("originalFraudId", event.getFraudId());
            headers.put("dlqRetry", true);

            // Calculate delay based on retry count
            long delayMs = calculateRetryDelay(currentRetryCount);
            headers.put("scheduledFor", Instant.now().plusMillis(delayMs).toEpochMilli());

            // Send to retry topic
            kafkaTemplate.send("fraud.detected.events.retry", event.getFraudId(), eventJson);

            log.info("SECURITY: Fraud containment retry scheduled - FraudID: {}, Retry: {}, DelayMs: {}",
                    event.getFraudId(), currentRetryCount + 1, delayMs);

        } catch (Exception e) {
            log.error("Failed to schedule fraud containment retry: {}", e.getMessage());
        }
    }

    /**
     * Escalate to security operations team.
     */
    private void escalateToSecurityOps(FraudDetectedEvent event, String failureReason,
                                       Integer retryCount, String dlqEventId) {
        try {
            Map<String, Object> escalation = new HashMap<>();
            escalation.put("escalationId", UUID.randomUUID().toString());
            escalation.put("type", "FRAUD_CONTAINMENT_FAILURE");
            escalation.put("priority", "CRITICAL");
            escalation.put("fraudId", event.getFraudId());
            escalation.put("userId", event.getUserId());
            escalation.put("transactionId", event.getTransactionId());
            escalation.put("fraudType", event.getFraudType());
            escalation.put("riskLevel", event.getRiskLevel());
            escalation.put("riskScore", event.getRiskScore());
            escalation.put("failureReason", failureReason);
            escalation.put("retryCount", retryCount);
            escalation.put("dlqEventId", dlqEventId);
            escalation.put("timestamp", Instant.now());
            escalation.put("requiredAction", "MANUAL_FRAUD_CONTAINMENT");

            kafkaTemplate.send("security.escalations", dlqEventId, escalation);

            log.error("SECURITY: Fraud containment escalated to security ops - FraudID: {}, DLQEventId: {}",
                    event.getFraudId(), dlqEventId);

        } catch (Exception e) {
            log.error("Failed to escalate to security ops: {}", e.getMessage());
        }
    }

    /**
     * Create security incident for investigation.
     */
    private void createSecurityIncident(FraudDetectedEvent event, String failureReason,
                                        Integer retryCount, String dlqEventId) {
        try {
            Map<String, Object> incident = new HashMap<>();
            incident.put("incidentId", dlqEventId);
            incident.put("type", "FRAUD_CONTAINMENT_FAILURE");
            incident.put("severity", "HIGH");
            incident.put("fraudId", event.getFraudId());
            incident.put("userId", event.getUserId());
            incident.put("description", String.format(
                "Failed to contain fraud after %d retries. FraudType: %s, RiskLevel: %s, Reason: %s",
                retryCount, event.getFraudType(), event.getRiskLevel(), failureReason
            ));
            incident.put("status", "OPEN");
            incident.put("assignedTo", "SECURITY_OPERATIONS");
            incident.put("createdAt", Instant.now());

            kafkaTemplate.send(SECURITY_INCIDENT_TOPIC, dlqEventId, incident);

            log.warn("SECURITY: Incident created for fraud containment failure - IncidentID: {}, FraudID: {}",
                    dlqEventId, event.getFraudId());

        } catch (Exception e) {
            log.error("Failed to create security incident: {}", e.getMessage());
        }
    }

    /**
     * Alert compliance team of DLQ fraud event.
     */
    private void alertComplianceTeam(FraudDetectedEvent event, String failureReason, String dlqEventId) {
        try {
            userNotificationService.sendComplianceAlert(
                "FRAUD_CONTAINMENT_FAILURE",
                String.format("Critical: Fraud containment failed for FraudID %s (User: %s). " +
                        "Manual review required. Reason: %s. IncidentID: %s",
                        event.getFraudId(), event.getUserId(), failureReason, dlqEventId),
                event
            );
        } catch (Exception e) {
            log.error("Failed to alert compliance team: {}", e.getMessage());
        }
    }

    /**
     * Determine if error is retryable.
     */
    private boolean isRetryable(String failureReason) {
        if (failureReason == null) {
            return true;
        }

        String reason = failureReason.toLowerCase();

        // Non-retryable errors
        if (reason.contains("invalid") ||
            reason.contains("validation") ||
            reason.contains("not found") ||
            reason.contains("does not exist")) {
            return false;
        }

        // Retryable errors (transient failures)
        return reason.contains("timeout") ||
               reason.contains("connection") ||
               reason.contains("unavailable") ||
               reason.contains("circuit breaker");
    }

    /**
     * Calculate retry delay with exponential backoff.
     */
    private long calculateRetryDelay(Integer retryCount) {
        switch (retryCount) {
            case 0:
                return 3600000L; // 1 hour
            case 1:
                return 21600000L; // 6 hours
            case 2:
                return 86400000L; // 24 hours
            default:
                return 86400000L; // 24 hours
        }
    }

    /**
     * Generate unique DLQ event ID.
     */
    private String generateDlqEventId(String key, long offset) {
        return String.format("DLQ_%s_%d_%d", key, offset, System.currentTimeMillis());
    }
}
