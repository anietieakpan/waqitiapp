package com.waqiti.wallet.kafka;

import com.waqiti.common.eventsourcing.FraudDetectedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.WalletSecurityService;
import com.waqiti.wallet.service.WalletService;
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
 * Fraud Detected Event Consumer for Wallet Service
 *
 * CRITICAL SECURITY: Handles fraud detection events to immediately freeze wallets
 * and prevent further fraudulent transactions.
 *
 * Actions:
 * 1. Freeze affected wallet(s)
 * 2. Cancel pending transactions
 * 3. Block new transactions
 * 4. Log security event
 * 5. Trigger alerts
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
public class FraudDetectedEventConsumer {

    private final WalletService walletService;
    private final WalletSecurityService walletSecurityService;
    // CRITICAL P0 FIX: Add idempotency service for duplicate prevention
    private final IdempotencyService idempotencyService;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(
        topics = "fraud.detected.events",
        groupId = "wallet-service-fraud-containment",
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
        log.warn("SECURITY ALERT: Fraud detected event received - FraudID: {}, TransactionID: {}, RiskLevel: {}, RiskScore: {}",
                event.getFraudId(), event.getTransactionId(), event.getRiskLevel(), event.getRiskScore());

        try {
            // CRITICAL P0 FIX: Generate idempotency key to prevent duplicate wallet freezes
            // Format: fraud:{fraudId}:{walletId}:{action}
            String walletIdStr = event.getWalletId() != null ?
                event.getWalletId().toString() :
                extractWalletFromTransaction(event.getTransactionId()).toString();

            String idempotencyKey = headerIdempotencyKey != null ?
                headerIdempotencyKey :
                String.format("fraud:%s:%s:wallet-freeze", event.getFraudId(), walletIdStr);

            // CRITICAL: Execute with idempotency protection
            // This ensures the wallet freeze operation executes exactly once
            // even if the event is delivered multiple times (Kafka at-least-once semantics)
            idempotencyService.executeIdempotentWithPersistence(
                "wallet-service",
                "fraud-wallet-freeze",
                idempotencyKey,
                () -> processFraudEvent(event),
                Duration.ofHours(24) // Keep idempotency record for 24 hours
            );

            acknowledgment.acknowledge();
            log.info("SECURITY: Fraud containment actions completed for fraud: {} (idempotency: {})",
                event.getFraudId(), idempotencyKey);

        } catch (Exception e) {
            log.error("SECURITY CRITICAL: Failed to process fraud detected event - FraudID: {}, TransactionID: {}, partition={}, offset={}, error={}",
                     event.getFraudId(), event.getTransactionId(), partition, offset, e.getMessage(), e);

            dlqHandler.handleFailedMessage(event, "fraud.detected.events", partition, offset, e)
                .thenAccept(result -> log.info("Fraud detected event sent to DLQ: fraudId={}, destination={}, category={}",
                        event.getFraudId(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for fraud detected event - MESSAGE MAY BE LOST! " +
                            "fraudId={}, partition={}, offset={}, error={}",
                            event.getFraudId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            // DO NOT acknowledge - allow retry
            // This is critical security functionality that must succeed
            throw new RuntimeException("Failed to process fraud detected event", e);
        }
    }

    /**
     * CRITICAL P0 FIX: Process fraud event with full business logic
     * This method is wrapped in idempotent execution to prevent duplicate freezes
     */
    @Transactional
    private Void processFraudEvent(FraudDetectedEvent event) {
        try {
            // Step 1: Extract wallet information from transaction
            UUID walletId = extractWalletFromTransaction(event.getTransactionId());

            if (walletId == null) {
                log.warn("SECURITY: Could not determine wallet from transaction: {}", event.getTransactionId());
                acknowledgment.acknowledge();
                return;
            }

            // Step 2: Freeze wallet based on risk level
            if (isHighRisk(event.getRiskLevel(), event.getRiskScore())) {
                log.warn("SECURITY CRITICAL: Freezing wallet {} due to high-risk fraud detection", walletId);

                walletSecurityService.freezeWallet(
                    walletId,
                    "FRAUD_DETECTED",
                    String.format("Fraud detected: %s (ID: %s, Risk: %s, Score: %.2f)",
                        event.getFraudType(), event.getFraudId(), event.getRiskLevel(), event.getRiskScore())
                );

                // Step 3: Cancel all pending transactions
                walletSecurityService.cancelPendingTransactions(
                    walletId,
                    "Fraud detected - wallet frozen"
                );

                log.warn("SECURITY: Wallet {} frozen and pending transactions cancelled", walletId);
            } else {
                // Medium/low risk: flag for review but don't freeze
                log.info("SECURITY: Flagging wallet {} for fraud review - Risk: {}, Score: {}",
                        walletId, event.getRiskLevel(), event.getRiskScore());

                walletSecurityService.flagWalletForReview(
                    walletId,
                    "FRAUD_DETECTED",
                    String.format("Fraud detected: %s (ID: %s)", event.getFraudType(), event.getFraudId())
                );
            }

            // Step 4: Log security event
            walletSecurityService.logSecurityEvent(
                walletId,
                "FRAUD_DETECTED",
                String.format("FraudType: %s, FraudID: %s, TransactionID: %s, RiskLevel: %s, RiskScore: %.2f, Action: %s",
                    event.getFraudType(), event.getFraudId(), event.getTransactionId(),
                    event.getRiskLevel(), event.getRiskScore(), event.getActionTaken())
            );

            // Step 5: Publish wallet frozen event if wallet was frozen
            if (isHighRisk(event.getRiskLevel(), event.getRiskScore())) {
                walletSecurityService.publishWalletFrozenEvent(walletId, event.getFraudId(), event.getFraudType());
            }

            log.info("SECURITY: Idempotent fraud processing completed for fraud: {}", event.getFraudId());
            return null; // Void return for idempotent execution

        } catch (Exception e) {
            log.error("SECURITY CRITICAL: Failed to process fraud event - FraudID: {}, TransactionID: {}",
                     event.getFraudId(), event.getTransactionId(), e);
            throw new RuntimeException("Failed to process fraud event", e);
        }
    }

    /**
     * Extract wallet ID from transaction.
     */
    private UUID extractWalletFromTransaction(String transactionId) {
        try {
            // Query wallet service to find wallet associated with transaction
            return walletService.getWalletByTransactionId(transactionId);
        } catch (Exception e) {
            log.error("Failed to extract wallet from transaction: {}", transactionId, e);
            return null;
        }
    }

    /**
     * Determine if fraud event is high risk requiring immediate freeze.
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
