package com.waqiti.user.kafka;

import com.waqiti.common.eventsourcing.FraudDetectedEvent;
import com.waqiti.user.service.UserSecurityService;
import com.waqiti.user.service.UserNotificationService;
import com.waqiti.user.service.UserStatusService;
// CRITICAL P0 FIX: Add idempotency service
import com.waqiti.common.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * Fraud Detected Event Consumer for User Service
 *
 * CRITICAL SECURITY: Handles fraud detection events to protect user accounts
 * and prevent account compromise.
 *
 * Actions:
 * 1. Suspend user account (high-risk fraud)
 * 2. Force password reset
 * 3. Invalidate active sessions
 * 4. Send fraud alert notification
 * 5. Log security event
 * 6. Flag account for review
 *
 * Compliance:
 * - PCI DSS fraud response requirements
 * - Account security best practices
 * - FFIEC authentication guidance
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FraudDetectedEventConsumer {

    private final UserSecurityService userSecurityService;
    private final UserNotificationService userNotificationService;
    private final UserStatusService userStatusService;
    // CRITICAL P0 FIX: Add idempotency service for duplicate prevention
    private final IdempotencyService idempotencyService;

    @KafkaListener(
        topics = "fraud.detected.events",
        groupId = "user-service-fraud-containment",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleFraudDetected(
        @Payload FraudDetectedEvent event,
        @Header(KafkaHeaders.RECEIVED_KEY) String key,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
        @Header(KafkaHeaders.OFFSET) long offset,
        @Header(value = "idempotency-key", required = false) String headerIdempotencyKey,
        Acknowledgment acknowledgment
    ) {
        log.warn("SECURITY ALERT: Fraud detected event received - FraudID: {}, UserID: {}, Type: {}, RiskLevel: {}",
                event.getFraudId(), event.getUserId(), event.getFraudType(), event.getRiskLevel());

        try {
            // CRITICAL P0 FIX: Generate idempotency key to prevent duplicate account suspensions
            // Format: fraud:{fraudId}:{userId}:{action}
            String userId = event.getUserId() != null ? event.getUserId() : "unknown";

            String idempotencyKey = headerIdempotencyKey != null ?
                headerIdempotencyKey :
                String.format("fraud:%s:%s:user-security", event.getFraudId(), userId);

            // CRITICAL: Execute with idempotency protection
            // This ensures user security actions (especially account suspension) execute exactly once
            // even if the event is delivered multiple times (Kafka at-least-once semantics)
            idempotencyService.executeIdempotentWithPersistence(
                "user-service",
                "fraud-user-security",
                idempotencyKey,
                () -> processFraudEvent(event),
                Duration.ofHours(24) // Keep idempotency record for 24 hours
            );

            acknowledgment.acknowledge();
            log.info("SECURITY: Fraud containment actions completed for fraud: {} (idempotency: {})",
                event.getFraudId(), idempotencyKey);

        } catch (Exception e) {
            log.error("SECURITY CRITICAL: Failed to process fraud detected event - FraudID: {}, UserID: {}",
                     event.getFraudId(), event.getUserId(), e);

            // DO NOT acknowledge - allow retry
            // This is critical security functionality that must succeed
            throw new RuntimeException("Failed to process fraud detected event", e);
        }
    }

    /**
     * CRITICAL P0 FIX: Process fraud event with full business logic
     * This method is wrapped in idempotent execution to prevent duplicate account suspensions
     */
    @Transactional
    private Void processFraudEvent(FraudDetectedEvent event) {
        try {
            UUID userId = UUID.fromString(event.getUserId());

            // Step 1: Suspend account for high-risk fraud
            if (isHighRisk(event.getRiskLevel(), event.getRiskScore())) {
                log.warn("SECURITY CRITICAL: Suspending user account {} due to high-risk fraud", userId);

                userStatusService.suspendAccount(
                    userId,
                    "FRAUD_DETECTED",
                    String.format("High-risk fraud detected: %s (ID: %s, Risk: %s, Score: %.2f)",
                        event.getFraudType(), event.getFraudId(), event.getRiskLevel(), event.getRiskScore())
                );

                // Step 2: Force password reset
                userSecurityService.forcePasswordReset(
                    userId,
                    "Fraud detected - password reset required for account security"
                );

                // Step 3: Invalidate all active sessions
                userSecurityService.invalidateAllSessions(
                    userId,
                    "Fraud detected - all sessions terminated"
                );

                log.warn("SECURITY: User {} account suspended, password reset required, sessions invalidated", userId);
            } else {
                // Medium/low risk: flag for review but don't suspend
                log.info("SECURITY: Flagging user {} for fraud review - Risk: {}, Score: {}",
                        userId, event.getRiskLevel(), event.getRiskScore());

                userStatusService.flagAccountForReview(
                    userId,
                    "FRAUD_DETECTED",
                    String.format("Fraud detected: %s (ID: %s)", event.getFraudType(), event.getFraudId())
                );
            }

            // Step 4: Send fraud alert notification to user
            try {
                userNotificationService.sendFraudAlert(
                    userId,
                    event.getFraudType(),
                    event.getTransactionId(),
                    event.getRiskLevel(),
                    isHighRisk(event.getRiskLevel(), event.getRiskScore())
                );
            } catch (Exception notificationError) {
                log.error("Failed to send fraud alert notification to user: {}", userId, notificationError);
                // Continue processing even if notification fails
            }

            // Step 5: Log security event
            userSecurityService.logSecurityEvent(
                userId,
                "FRAUD_DETECTED",
                String.format("FraudType: %s, FraudID: %s, TransactionID: %s, RiskLevel: %s, RiskScore: %.2f, Action: %s",
                    event.getFraudType(), event.getFraudId(), event.getTransactionId(),
                    event.getRiskLevel(), event.getRiskScore(), event.getActionTaken())
            );

            // Step 6: Publish user account suspended event if account was suspended
            if (isHighRisk(event.getRiskLevel(), event.getRiskScore())) {
                userSecurityService.publishAccountSuspendedEvent(userId, event.getFraudId(), event.getFraudType());
            }

            log.info("SECURITY: Idempotent fraud processing completed for fraud: {}", event.getFraudId());
            return null; // Void return for idempotent execution

        } catch (Exception e) {
            log.error("SECURITY CRITICAL: Failed to process fraud event - FraudID: {}, UserID: {}",
                     event.getFraudId(), event.getUserId(), e);
            throw new RuntimeException("Failed to process fraud event", e);
        }
    }

    /**
     * Determine if fraud event is high risk requiring immediate account suspension.
     */
    private boolean isHighRisk(String riskLevel, Double riskScore) {
        if (riskLevel == null && riskScore == null) {
            return false;
        }

        // High risk criteria:
        // - Risk level is "HIGH" or "CRITICAL"
        // - Risk score >= 80
        if (riskLevel != null) {
            String level = riskLevel.toUpperCase();
            if ("HIGH".equals(level) || "CRITICAL".equals(level)) {
                return true;
            }
        }

        if (riskScore != null && riskScore >= 80.0) {
            return true;
        }

        return false;
    }
}
