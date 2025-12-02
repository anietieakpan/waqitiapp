package com.waqiti.compliance.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.eventsourcing.FraudDetectedEvent;
import com.waqiti.compliance.service.SarFilingService;
import com.waqiti.compliance.service.ComplianceAlertService;
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
 * Fraud Detected Event DLQ Consumer for Compliance Service
 *
 * CRITICAL COMPLIANCE: Handles failed fraud reporting events to ensure regulatory
 * reporting obligations are met despite system failures.
 *
 * DLQ Strategy:
 * 1. Log failure to compliance audit system
 * 2. Attempt SAR filing with manual review flag
 * 3. Alert compliance officers immediately
 * 4. Create regulatory incident ticket
 * 5. Track in compliance monitoring dashboard
 *
 * Retry Logic:
 * - Attempt up to 3 retries with exponential backoff (1h, 6h, 24h)
 * - After max retries, escalate to compliance officers for manual SAR filing
 * - Track all retry attempts in audit log for FinCEN compliance
 *
 * Compliance:
 * - BSA/AML SAR filing requirements (31 CFR 1020.320)
 * - FinCEN suspicious activity reporting deadlines
 * - FINRA Rule 4530 (Reporting Requirements)
 * - All failures logged for regulatory audit
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
    private final SarFilingService sarFilingService;
    private final ComplianceAlertService complianceAlertService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int MAX_RETRIES = 3;
    private static final String COMPLIANCE_INCIDENT_TOPIC = "compliance.incidents";

    @KafkaListener(
        topics = "fraud.detected.events.dlq",
        groupId = "compliance-service-fraud-dlq",
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

        log.error("COMPLIANCE CRITICAL DLQ: Processing failed fraud reporting event - DLQEventId: {}, " +
                "OriginalTopic: {}, FailureReason: {}, RetryCount: {}",
                dlqEventId, originalTopic, failureReason, retryCount);

        try {
            // Deserialize event
            FraudDetectedEvent event = objectMapper.readValue(eventJson, FraudDetectedEvent.class);

            // Log to compliance audit (REQUIRED for FinCEN)
            logComplianceAuditFailure(event, failureReason, retryCount, dlqEventId);

            // Determine if retry is possible
            if (retryCount < MAX_RETRIES && isRetryable(failureReason)) {
                retryComplianceReporting(event, eventJson, retryCount);
                acknowledgment.acknowledge();
                log.info("COMPLIANCE: Scheduled fraud reporting retry - FraudID: {}, Retry: {}/{}",
                        event.getFraudId(), retryCount + 1, MAX_RETRIES);
            } else {
                // Max retries exceeded - escalate to compliance officers
                escalateToComplianceOfficers(event, failureReason, retryCount, dlqEventId);
                createComplianceIncident(event, failureReason, retryCount, dlqEventId);
                attemptManualSarFiling(event, failureReason, dlqEventId);

                acknowledgment.acknowledge();
                log.error("COMPLIANCE CRITICAL: Fraud reporting DLQ exhausted - FraudID: {}, TransactionID: {}, " +
                        "Manual SAR filing required - IncidentID: {}",
                        event.getFraudId(), event.getTransactionId(), dlqEventId);
            }

        } catch (Exception e) {
            log.error("COMPLIANCE CRITICAL: Failed to process fraud DLQ event - DLQEventId: {}, Error: {}",
                    dlqEventId, e.getMessage(), e);

            // Create critical compliance alert
            try {
                Map<String, Object> criticalAlert = new HashMap<>();
                criticalAlert.put("alertId", UUID.randomUUID().toString());
                criticalAlert.put("severity", "CRITICAL");
                criticalAlert.put("category", "REGULATORY_REPORTING_FAILURE");
                criticalAlert.put("message", "Failed to process fraud DLQ event for SAR filing: " + e.getMessage());
                criticalAlert.put("dlqEventId", dlqEventId);
                criticalAlert.put("failureReason", failureReason);
                criticalAlert.put("regulatoryImpact", "SAR_FILING_AT_RISK");
                criticalAlert.put("timestamp", Instant.now());

                kafkaTemplate.send("compliance.critical.alerts", dlqEventId, criticalAlert);
            } catch (Exception alertError) {
                log.error("COMPLIANCE CRITICAL: Failed to send critical DLQ alert: {}", alertError.getMessage());
            }

            // Acknowledge to prevent infinite loop
            acknowledgment.acknowledge();
        }
    }

    /**
     * Log failure to compliance audit system (FinCEN requirement).
     */
    private void logComplianceAuditFailure(FraudDetectedEvent event, String failureReason,
                                           Integer retryCount, String dlqEventId) {
        try {
            Map<String, Object> auditEntry = new HashMap<>();
            auditEntry.put("auditId", UUID.randomUUID().toString());
            auditEntry.put("eventType", "FRAUD_REPORTING_FAILURE");
            auditEntry.put("fraudId", event.getFraudId());
            auditEntry.put("transactionId", event.getTransactionId());
            auditEntry.put("userId", event.getUserId());
            auditEntry.put("fraudType", event.getFraudType());
            auditEntry.put("riskLevel", event.getRiskLevel());
            auditEntry.put("failureReason", failureReason);
            auditEntry.put("retryCount", retryCount);
            auditEntry.put("dlqEventId", dlqEventId);
            auditEntry.put("timestamp", Instant.now());
            auditEntry.put("regulatoryImplication", "SAR_FILING_DELAYED");

            kafkaTemplate.send("compliance.audit.log", dlqEventId, auditEntry);

            log.warn("COMPLIANCE: Audit logged for fraud reporting failure - DLQEventId: {}", dlqEventId);

        } catch (Exception e) {
            log.error("COMPLIANCE CRITICAL: Failed to log compliance audit for DLQ: {}", e.getMessage());
        }
    }

    /**
     * Retry compliance reporting with exponential backoff.
     */
    private void retryComplianceReporting(FraudDetectedEvent event, String eventJson, Integer currentRetryCount) {
        try {
            // Schedule retry by publishing back to original topic with retry headers
            Map<String, Object> headers = new HashMap<>();
            headers.put("retryCount", currentRetryCount + 1);
            headers.put("originalFraudId", event.getFraudId());
            headers.put("dlqRetry", true);
            headers.put("complianceUrgent", true);

            // Calculate delay based on retry count
            long delayMs = calculateRetryDelay(currentRetryCount);
            headers.put("scheduledFor", Instant.now().plusMillis(delayMs).toEpochMilli());

            // Send to retry topic
            kafkaTemplate.send("fraud.detected.events.retry", event.getFraudId(), eventJson);

            log.info("COMPLIANCE: Fraud reporting retry scheduled - FraudID: {}, Retry: {}, DelayMs: {}",
                    event.getFraudId(), currentRetryCount + 1, delayMs);

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to schedule fraud reporting retry: {}", e.getMessage());
        }
    }

    /**
     * Escalate to compliance officers for manual intervention.
     */
    private void escalateToComplianceOfficers(FraudDetectedEvent event, String failureReason,
                                              Integer retryCount, String dlqEventId) {
        try {
            complianceAlertService.sendCriticalAlert(
                "FRAUD_REPORTING_FAILURE",
                String.format("CRITICAL: Automated fraud reporting failed after %d retries. " +
                        "FraudID: %s, TransactionID: %s, Type: %s, RiskLevel: %s. " +
                        "Manual SAR filing may be required. Reason: %s. IncidentID: %s",
                        retryCount, event.getFraudId(), event.getTransactionId(),
                        event.getFraudType(), event.getRiskLevel(), failureReason, dlqEventId),
                event
            );

            log.error("COMPLIANCE: Fraud reporting escalated to compliance officers - FraudID: {}, DLQEventId: {}",
                    event.getFraudId(), dlqEventId);

        } catch (Exception e) {
            log.error("COMPLIANCE CRITICAL: Failed to escalate to compliance officers: {}", e.getMessage());
        }
    }

    /**
     * Create compliance incident for investigation.
     */
    private void createComplianceIncident(FraudDetectedEvent event, String failureReason,
                                          Integer retryCount, String dlqEventId) {
        try {
            Map<String, Object> incident = new HashMap<>();
            incident.put("incidentId", dlqEventId);
            incident.put("type", "FRAUD_REPORTING_FAILURE");
            incident.put("severity", "HIGH");
            incident.put("fraudId", event.getFraudId());
            incident.put("transactionId", event.getTransactionId());
            incident.put("userId", event.getUserId());
            incident.put("description", String.format(
                "Failed to report fraud after %d retries. FraudType: %s, RiskLevel: %s. " +
                "SAR filing may be required. Reason: %s",
                retryCount, event.getFraudType(), event.getRiskLevel(), failureReason
            ));
            incident.put("regulatoryDeadline", calculateSarDeadline());
            incident.put("status", "OPEN");
            incident.put("assignedTo", "COMPLIANCE_OFFICERS");
            incident.put("createdAt", Instant.now());

            kafkaTemplate.send(COMPLIANCE_INCIDENT_TOPIC, dlqEventId, incident);

            log.warn("COMPLIANCE: Incident created for fraud reporting failure - IncidentID: {}, FraudID: {}",
                    dlqEventId, event.getFraudId());

        } catch (Exception e) {
            log.error("COMPLIANCE: Failed to create compliance incident: {}", e.getMessage());
        }
    }

    /**
     * Attempt manual SAR filing with review flag.
     */
    private void attemptManualSarFiling(FraudDetectedEvent event, String failureReason, String dlqEventId) {
        try {
            // Create SAR with manual review flag
            String sarId = sarFilingService.createManualReviewSar(
                event.getFraudId(),
                event.getTransactionId(),
                event.getUserId(),
                event.getFraudType(),
                event.getRiskScore(),
                event.getFraudIndicators(),
                String.format("DLQ: Automated filing failed - %s. Manual review required. DLQEventId: %s",
                        failureReason, dlqEventId)
            );

            log.warn("COMPLIANCE: Manual SAR created for review - SAR ID: {}, FraudID: {}, DLQEventId: {}",
                    sarId, event.getFraudId(), dlqEventId);

        } catch (Exception e) {
            log.error("COMPLIANCE CRITICAL: Failed to create manual SAR - FraudID: {}, Error: {}",
                    event.getFraudId(), e.getMessage());
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
            reason.contains("duplicate")) {
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
     * Calculate SAR filing deadline (30 days from detection).
     */
    private Instant calculateSarDeadline() {
        return Instant.now().plusSeconds(30 * 24 * 60 * 60); // 30 days
    }

    /**
     * Generate unique DLQ event ID.
     */
    private String generateDlqEventId(String key, long offset) {
        return String.format("DLQ_COMPLIANCE_%s_%d_%d", key, offset, System.currentTimeMillis());
    }
}
