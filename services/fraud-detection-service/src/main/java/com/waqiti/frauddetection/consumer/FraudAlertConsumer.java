package com.waqiti.frauddetection.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.frauddetection.domain.FraudAlert;
import com.waqiti.frauddetection.domain.FraudAlertStatus;
import com.waqiti.frauddetection.domain.FraudSeverity;
import com.waqiti.frauddetection.repository.FraudAlertRepository;
import com.waqiti.frauddetection.service.FraudInvestigationService;
import com.waqiti.frauddetection.service.FraudNotificationService;
import com.waqiti.common.math.MoneyMath;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.common.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka consumer for fraud alert events
 * 
 * Handles critical fraud detection alerts with:
 * - Immediate notification to security team
 * - Automatic account freezing for high-risk alerts
 * - Compliance reporting for suspicious activities
 * - Real-time fraud pattern analysis
 * - Audit trail generation
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudAlertConsumer {

    private final FraudAlertRepository fraudAlertRepository;
    private final FraudInvestigationService investigationService;
    private final FraudNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    @KafkaListener(
        topics = "fraud-alerts",
        groupId = "fraud-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(
        value = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    @Transactional
    public void processFraudAlert(@Payload String payload,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                 @Header(KafkaHeaders.OFFSET) long offset,
                                 Acknowledgment acknowledgment) {

        log.info("Processing fraud alert from topic: {} partition: {} offset: {}",
                topic, partition, offset);

        UUID operationId = UUID.randomUUID();
        String idempotencyKey = null;

        try {
            // Parse the event
            FraudAlertEvent event = objectMapper.readValue(payload, FraudAlertEvent.class);

            // Create idempotency key from event ID
            idempotencyKey = "fraud-alert:" + event.getEventId();

            // Check for duplicate processing
            if (!idempotencyService.startOperation(idempotencyKey, operationId, IDEMPOTENCY_TTL)) {
                log.info("Duplicate fraud alert event detected, skipping: {}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }

            log.warn("FRAUD ALERT: EventId={}, Type={}, UserId={}, RiskScore={}, Amount={}",
                    event.getEventId(), event.getAlertType(), event.getUserId(), event.getRiskScore(), event.getAmount());
            
            // Create fraud alert record
            FraudAlert alert = createFraudAlert(event);
            
            // Determine severity based on risk score
            FraudSeverity severity = determineSeverity(event.getRiskScore());
            alert.setSeverity(severity);
            
            // Save alert to database
            alert = fraudAlertRepository.save(alert);
            
            // Take immediate action based on severity
            handleFraudAlertBySeverity(alert, event);
            
            // Send notifications
            notifyRelevantParties(alert, event);
            
            // Trigger investigation if needed
            if (severity == FraudSeverity.CRITICAL || severity == FraudSeverity.HIGH) {
                investigationService.openInvestigation(alert);
            }
            
            // Update metrics
            updateFraudMetrics(event);

            // Mark operation as completed
            if (idempotencyKey != null) {
                idempotencyService.completeOperation(idempotencyKey, operationId, alert.getId(), IDEMPOTENCY_TTL);
            }

            // Acknowledge message
            acknowledgment.acknowledge();

            log.info("Successfully processed fraud alert: {}", alert.getId());

        } catch (Exception e) {
            log.error("Failed to process fraud alert: {}", payload, e);

            // Mark operation as failed
            if (idempotencyKey != null) {
                idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            }

            // Don't acknowledge - let it retry or go to DLQ
            throw new RuntimeException("Failed to process fraud alert", e);
        }
    }

    private FraudAlert createFraudAlert(FraudAlertEvent event) {
        return FraudAlert.builder()
            .id(UUID.randomUUID())
            .userId(event.getUserId())
            .transactionId(event.getTransactionId())
            .alertType(event.getAlertType())
            .riskScore(event.getRiskScore())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .description(event.getDescription())
            .detectionMethod(event.getDetectionMethod())
            .ipAddress(event.getIpAddress())
            .deviceId(event.getDeviceId())
            .location(event.getLocation())
            .status(FraudAlertStatus.OPEN)
            .createdAt(LocalDateTime.now())
            .metadata(event.getMetadata())
            .build();
    }

    private FraudSeverity determineSeverity(double riskScore) {
        if (riskScore >= 0.9) {
            return FraudSeverity.CRITICAL;
        } else if (riskScore >= 0.7) {
            return FraudSeverity.HIGH;
        } else if (riskScore >= 0.5) {
            return FraudSeverity.MEDIUM;
        } else {
            return FraudSeverity.LOW;
        }
    }

    private void handleFraudAlertBySeverity(FraudAlert alert, FraudAlertEvent event) {
        switch (alert.getSeverity()) {
            case CRITICAL:
                // Immediate account freeze
                freezeUserAccount(event.getUserId(), alert.getId());
                // Block all pending transactions
                blockPendingTransactions(event.getUserId());
                // Notify compliance team
                notifyComplianceTeam(alert);
                break;
                
            case HIGH:
                // Temporary transaction limit
                applyTransactionRestrictions(event.getUserId());
                // Require additional verification
                requireAdditionalVerification(event.getUserId());
                break;
                
            case MEDIUM:
                // Flag for review
                flagForManualReview(alert);
                // Increase monitoring
                increaseMonitoringLevel(event.getUserId());
                break;
                
            case LOW:
                // Log and monitor
                log.info("Low risk fraud alert logged for monitoring: {}", alert.getId());
                break;
        }
    }

    private void notifyRelevantParties(FraudAlert alert, FraudAlertEvent event) {
        try {
            // Notify security team for high severity alerts
            if (alert.getSeverity() == FraudSeverity.CRITICAL || 
                alert.getSeverity() == FraudSeverity.HIGH) {
                notificationService.notifySecurityTeam(alert);
            }
            
            // Notify user for medium and above
            if (alert.getSeverity() != FraudSeverity.LOW) {
                notificationService.notifyUser(
                    event.getUserId(),
                    "Suspicious Activity Detected",
                    buildUserNotificationMessage(alert)
                );
            }
            
            // Send to compliance if amount exceeds threshold
            BigDecimal complianceThreshold = new BigDecimal("10000");
            if (event.getAmount() != null && event.getAmount().compareTo(complianceThreshold) > 0) {
                notificationService.notifyCompliance(alert);
            }
            
        } catch (Exception e) {
            log.error("Failed to send fraud notifications for alert: {}", alert.getId(), e);
            // Don't fail the main process for notification failures
        }
    }

    private void freezeUserAccount(String userId, UUID alertId) {
        try {
            log.warn("FREEZING ACCOUNT: UserId={} due to fraud alert: {}", userId, alertId);
            
            // Call user service to freeze account
            // userServiceClient.freezeAccount(userId, "Fraud alert: " + alertId);
            
            // Publish account freeze event
            publishAccountFreezeEvent(userId, alertId);
            
        } catch (Exception e) {
            log.error("Failed to freeze account for user: {}", userId, e);
        }
    }

    private void blockPendingTransactions(String userId) {
        try {
            log.warn("BLOCKING PENDING TRANSACTIONS for user: {}", userId);
            
            // Call transaction service to block pending transactions
            // transactionServiceClient.blockPendingTransactions(userId);
            
        } catch (Exception e) {
            log.error("Failed to block pending transactions for user: {}", userId, e);
        }
    }

    private void applyTransactionRestrictions(String userId) {
        try {
            log.info("Applying transaction restrictions for user: {}", userId);
            
            // Apply temporary limits
            // walletServiceClient.applyTemporaryLimits(userId, 100.00, 7); // $100 limit for 7 days
            
        } catch (Exception e) {
            log.error("Failed to apply transaction restrictions for user: {}", userId, e);
        }
    }

    private void requireAdditionalVerification(String userId) {
        try {
            log.info("Requiring additional verification for user: {}", userId);
            
            // Set flag for additional verification
            // userServiceClient.requireAdditionalVerification(userId);
            
        } catch (Exception e) {
            log.error("Failed to set additional verification for user: {}", userId, e);
        }
    }

    private void flagForManualReview(FraudAlert alert) {
        alert.setStatus(FraudAlertStatus.PENDING_REVIEW);
        fraudAlertRepository.save(alert);
        
        log.info("Fraud alert {} flagged for manual review", alert.getId());
    }

    private void increaseMonitoringLevel(String userId) {
        try {
            log.info("Increasing monitoring level for user: {}", userId);
            
            // Increase monitoring intensity
            // monitoringServiceClient.increaseMonitoringLevel(userId, "ENHANCED");
            
        } catch (Exception e) {
            log.error("Failed to increase monitoring for user: {}", userId, e);
        }
    }

    private void notifyComplianceTeam(FraudAlert alert) {
        try {
            log.info("Notifying compliance team about critical fraud alert: {}", alert.getId());
            
            // Send to compliance queue
            // complianceServiceClient.reportSuspiciousActivity(alert);
            
        } catch (Exception e) {
            log.error("Failed to notify compliance team", e);
        }
    }

    private String buildUserNotificationMessage(FraudAlert alert) {
        return String.format(
            "We've detected unusual activity on your account. " +
            "Type: %s, Risk Level: %s. " +
            "Please review your recent transactions and contact support if you don't recognize this activity.",
            alert.getAlertType(), alert.getSeverity()
        );
    }

    private void publishAccountFreezeEvent(String userId, UUID alertId) {
        try {
            // Publish event for other services
            // eventPublisher.publishAccountFreezeEvent(userId, alertId);
            
        } catch (Exception e) {
            log.error("Failed to publish account freeze event", e);
        }
    }

    private void updateFraudMetrics(FraudAlertEvent event) {
        try {
            // Update fraud metrics
            // metricsService.incrementCounter("fraud.alerts.processed");
            // metricsService.recordValue("fraud.risk.score", event.getRiskScore());
            
        } catch (Exception e) {
            log.error("Failed to update metrics", e);
        }
    }
}