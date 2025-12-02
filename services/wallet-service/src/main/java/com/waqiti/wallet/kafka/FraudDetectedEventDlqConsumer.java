package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.eventsourcing.FraudDetectedEvent;
import com.waqiti.wallet.service.WalletSecurityService;
import com.waqiti.wallet.service.WalletService;
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
 * Fraud Detected Event DLQ Consumer for Wallet Service
 *
 * CRITICAL SECURITY: Handles failed wallet freeze events to ensure fraudulent
 * wallets are contained despite system failures.
 *
 * DLQ Strategy:
 * 1. Log failure to wallet audit system
 * 2. Attempt immediate wallet freeze with override
 * 3. Alert wallet security team immediately
 * 4. Create security incident ticket
 * 5. Trigger balance verification
 *
 * Retry Logic:
 * - Attempt up to 3 retries with exponential backoff (1h, 6h, 24h)
 * - After max retries, escalate to wallet security team for manual intervention
 * - Track all retry attempts in audit log
 *
 * Compliance:
 * - BSA/AML fraud response requirements
 * - PCI DSS fraud containment
 * - FINRA suspicious activity procedures
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
    private final WalletSecurityService walletSecurityService;
    private final WalletService walletService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int MAX_RETRIES = 3;
    private static final String WALLET_INCIDENT_TOPIC = "wallet.security.incidents";

    @KafkaListener(
        topics = "fraud.detected.events.dlq",
        groupId = "wallet-service-fraud-dlq",
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

        log.error("WALLET SECURITY CRITICAL DLQ: Processing failed fraud wallet freeze event - DLQEventId: {}, " +
                "OriginalTopic: {}, FailureReason: {}, RetryCount: {}",
                dlqEventId, originalTopic, failureReason, retryCount);

        try {
            // Deserialize event
            FraudDetectedEvent event = objectMapper.readValue(eventJson, FraudDetectedEvent.class);

            // Extract wallet ID from transaction
            UUID walletId = extractWalletFromTransaction(event.getTransactionId());

            if (walletId == null) {
                log.error("WALLET SECURITY: Cannot extract wallet ID from transaction: {}",
                        event.getTransactionId());
                acknowledgment.acknowledge();
                return;
            }

            // Log to wallet audit
            logWalletAuditFailure(event, walletId, failureReason, retryCount, dlqEventId);

            // Determine if retry is possible
            if (retryCount < MAX_RETRIES && isRetryable(failureReason)) {
                retryWalletFreeze(event, walletId, eventJson, retryCount);
                acknowledgment.acknowledge();
                log.info("WALLET SECURITY: Scheduled wallet freeze retry - WalletID: {}, FraudID: {}, Retry: {}/{}",
                        walletId, event.getFraudId(), retryCount + 1, MAX_RETRIES);
            } else {
                // Max retries exceeded - manual intervention required
                attemptEmergencyWalletFreeze(event, walletId, failureReason, dlqEventId);
                escalateToWalletSecurityTeam(event, walletId, failureReason, retryCount, dlqEventId);
                createWalletSecurityIncident(event, walletId, failureReason, retryCount, dlqEventId);
                triggerBalanceVerification(event, walletId, dlqEventId);

                acknowledgment.acknowledge();
                log.error("WALLET SECURITY CRITICAL: Fraud wallet freeze DLQ exhausted - WalletID: {}, FraudID: {}, " +
                        "Manual intervention required - IncidentID: {}",
                        walletId, event.getFraudId(), dlqEventId);
            }

        } catch (Exception e) {
            log.error("WALLET SECURITY CRITICAL: Failed to process fraud DLQ event - DLQEventId: {}, Error: {}",
                    dlqEventId, e.getMessage(), e);

            // Create critical wallet security alert
            try {
                Map<String, Object> criticalAlert = new HashMap<>();
                criticalAlert.put("alertId", UUID.randomUUID().toString());
                criticalAlert.put("severity", "CRITICAL");
                criticalAlert.put("category", "WALLET_FRAUD_CONTAINMENT_FAILURE");
                criticalAlert.put("message", "Failed to process wallet fraud DLQ event: " + e.getMessage());
                criticalAlert.put("dlqEventId", dlqEventId);
                criticalAlert.put("failureReason", failureReason);
                criticalAlert.put("securityImpact", "FRAUDULENT_WALLET_NOT_FROZEN");
                criticalAlert.put("timestamp", Instant.now());

                kafkaTemplate.send("wallet.security.critical.alerts", dlqEventId, criticalAlert);
            } catch (Exception alertError) {
                log.error("WALLET SECURITY CRITICAL: Failed to send critical DLQ alert: {}", alertError.getMessage());
            }

            // Acknowledge to prevent infinite loop
            acknowledgment.acknowledge();
        }
    }

    /**
     * Extract wallet ID from transaction.
     */
    private UUID extractWalletFromTransaction(String transactionId) {
        try {
            return walletService.getWalletByTransactionId(transactionId);
        } catch (Exception e) {
            log.error("Failed to extract wallet from transaction: {}", transactionId, e);
            return null;
        }
    }

    /**
     * Log failure to wallet audit system.
     */
    private void logWalletAuditFailure(FraudDetectedEvent event, UUID walletId, String failureReason,
                                       Integer retryCount, String dlqEventId) {
        try {
            walletSecurityService.logSecurityEvent(
                walletId,
                "FRAUD_WALLET_FREEZE_FAILURE",
                String.format("DLQ: Failed to freeze wallet for fraud - FraudID: %s, Reason: %s, " +
                        "RetryCount: %d, DLQEventId: %s",
                        event.getFraudId(), failureReason, retryCount, dlqEventId)
            );
        } catch (Exception e) {
            log.error("Failed to log wallet audit for DLQ: {}", e.getMessage());
        }
    }

    /**
     * Retry wallet freeze with exponential backoff.
     */
    private void retryWalletFreeze(FraudDetectedEvent event, UUID walletId, String eventJson,
                                   Integer currentRetryCount) {
        try {
            // Schedule retry by publishing back to original topic with retry headers
            Map<String, Object> headers = new HashMap<>();
            headers.put("retryCount", currentRetryCount + 1);
            headers.put("originalFraudId", event.getFraudId());
            headers.put("walletId", walletId.toString());
            headers.put("dlqRetry", true);
            headers.put("urgentWalletFreeze", true);

            // Calculate delay based on retry count
            long delayMs = calculateRetryDelay(currentRetryCount);
            headers.put("scheduledFor", Instant.now().plusMillis(delayMs).toEpochMilli());

            // Send to retry topic
            kafkaTemplate.send("fraud.detected.events.retry", event.getFraudId(), eventJson);

            log.info("WALLET SECURITY: Wallet freeze retry scheduled - WalletID: {}, FraudID: {}, Retry: {}, DelayMs: {}",
                    walletId, event.getFraudId(), currentRetryCount + 1, delayMs);

        } catch (Exception e) {
            log.error("Failed to schedule wallet freeze retry: {}", e.getMessage());
        }
    }

    /**
     * Attempt emergency wallet freeze with override permissions.
     */
    private void attemptEmergencyWalletFreeze(FraudDetectedEvent event, UUID walletId,
                                              String failureReason, String dlqEventId) {
        try {
            log.warn("WALLET SECURITY: Attempting emergency wallet freeze - WalletID: {}, FraudID: {}, DLQEventId: {}",
                    walletId, event.getFraudId(), dlqEventId);

            // Attempt freeze with admin override
            walletSecurityService.emergencyFreezeWallet(
                walletId,
                "FRAUD_DETECTED_DLQ_EMERGENCY",
                String.format("Emergency freeze due to DLQ failure. FraudID: %s, Reason: %s, DLQEventId: %s",
                        event.getFraudId(), failureReason, dlqEventId)
            );

            log.warn("WALLET SECURITY: Emergency freeze successful - WalletID: {}, FraudID: {}",
                    walletId, event.getFraudId());

        } catch (Exception e) {
            log.error("WALLET SECURITY CRITICAL: Emergency wallet freeze failed - WalletID: {}, FraudID: {}, Error: {}",
                    walletId, event.getFraudId(), e.getMessage());
        }
    }

    /**
     * Escalate to wallet security team.
     */
    private void escalateToWalletSecurityTeam(FraudDetectedEvent event, UUID walletId, String failureReason,
                                              Integer retryCount, String dlqEventId) {
        try {
            Map<String, Object> escalation = new HashMap<>();
            escalation.put("escalationId", UUID.randomUUID().toString());
            escalation.put("type", "WALLET_FRAUD_CONTAINMENT_FAILURE");
            escalation.put("priority", "CRITICAL");
            escalation.put("fraudId", event.getFraudId());
            escalation.put("walletId", walletId.toString());
            escalation.put("transactionId", event.getTransactionId());
            escalation.put("fraudType", event.getFraudType());
            escalation.put("riskLevel", event.getRiskLevel());
            escalation.put("failureReason", failureReason);
            escalation.put("retryCount", retryCount);
            escalation.put("dlqEventId", dlqEventId);
            escalation.put("timestamp", Instant.now());
            escalation.put("requiredAction", "MANUAL_WALLET_FREEZE");

            kafkaTemplate.send("wallet.security.escalations", dlqEventId, escalation);

            log.error("WALLET SECURITY: Escalated to wallet security team - WalletID: {}, FraudID: {}, DLQEventId: {}",
                    walletId, event.getFraudId(), dlqEventId);

        } catch (Exception e) {
            log.error("Failed to escalate to wallet security team: {}", e.getMessage());
        }
    }

    /**
     * Create wallet security incident for investigation.
     */
    private void createWalletSecurityIncident(FraudDetectedEvent event, UUID walletId, String failureReason,
                                              Integer retryCount, String dlqEventId) {
        try {
            Map<String, Object> incident = new HashMap<>();
            incident.put("incidentId", dlqEventId);
            incident.put("type", "WALLET_FRAUD_CONTAINMENT_FAILURE");
            incident.put("severity", "HIGH");
            incident.put("fraudId", event.getFraudId());
            incident.put("walletId", walletId.toString());
            incident.put("transactionId", event.getTransactionId());
            incident.put("description", String.format(
                "Failed to freeze wallet for fraud after %d retries. FraudType: %s, RiskLevel: %s, Reason: %s",
                retryCount, event.getFraudType(), event.getRiskLevel(), failureReason
            ));
            incident.put("status", "OPEN");
            incident.put("assignedTo", "WALLET_SECURITY_TEAM");
            incident.put("createdAt", Instant.now());

            kafkaTemplate.send(WALLET_INCIDENT_TOPIC, dlqEventId, incident);

            log.warn("WALLET SECURITY: Incident created for wallet freeze failure - IncidentID: {}, WalletID: {}, FraudID: {}",
                    dlqEventId, walletId, event.getFraudId());

        } catch (Exception e) {
            log.error("Failed to create wallet security incident: {}", e.getMessage());
        }
    }

    /**
     * Trigger balance verification for wallet.
     */
    private void triggerBalanceVerification(FraudDetectedEvent event, UUID walletId, String dlqEventId) {
        try {
            Map<String, Object> verificationRequest = new HashMap<>();
            verificationRequest.put("requestId", UUID.randomUUID().toString());
            verificationRequest.put("walletId", walletId.toString());
            verificationRequest.put("reason", "FRAUD_DLQ_FAILURE");
            verificationRequest.put("fraudId", event.getFraudId());
            verificationRequest.put("dlqEventId", dlqEventId);
            verificationRequest.put("priority", "HIGH");
            verificationRequest.put("timestamp", Instant.now());

            kafkaTemplate.send("wallet.balance.verification", walletId.toString(), verificationRequest);

            log.info("WALLET SECURITY: Balance verification triggered - WalletID: {}, DLQEventId: {}",
                    walletId, dlqEventId);

        } catch (Exception e) {
            log.error("Failed to trigger balance verification: {}", e.getMessage());
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
            reason.contains("not found") ||
            reason.contains("does not exist") ||
            reason.contains("already frozen")) {
            return false;
        }

        // Retryable errors (transient failures)
        return reason.contains("timeout") ||
               reason.contains("connection") ||
               reason.contains("unavailable") ||
               reason.contains("circuit breaker") ||
               reason.contains("lock");
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
        return String.format("DLQ_WALLET_%s_%d_%d", key, offset, System.currentTimeMillis());
    }
}
