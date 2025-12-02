package com.waqiti.frauddetection.kafka;

import com.waqiti.frauddetection.events.WalletFreezeRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Producer for wallet.freeze.requested events.
 *
 * Critical fraud prevention event that triggers immediate wallet freeze.
 * Must be processed with minimal latency.
 *
 * @author Waqiti Platform Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WalletFreezeEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "wallet.freeze.requested";

    /**
     * Request immediate wallet freeze due to fraud detection.
     *
     * @param userId User whose wallet should be frozen
     * @param walletId Specific wallet to freeze (null = all wallets)
     * @param fraudCaseId Associated fraud case
     * @param reason Human-readable reason
     * @param severity Severity level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    public void requestWalletFreeze(
        UUID userId,
        UUID walletId,
        UUID fraudCaseId,
        String reason,
        FraudSeverity severity
    ) {
        WalletFreezeRequestedEvent event = WalletFreezeRequestedEvent.builder()
            .userId(userId)
            .walletId(walletId)
            .fraudCaseId(fraudCaseId)
            .reason(reason)
            .severity(severity)
            .requestedAt(Instant.now())
            .eventId(UUID.randomUUID().toString())
            .correlationId(fraudCaseId)
            .build();

        log.warn("FRAUD ALERT: Requesting wallet freeze. UserId: {}, WalletId: {}, " +
                "FraudCase: {}, Severity: {}, Reason: {}",
            userId, walletId, fraudCaseId, severity, reason);

        // Use synchronous send for critical security events
        try {
            var result = kafkaTemplate.send(TOPIC, userId.toString(), event).get();

            log.warn("Wallet freeze request published successfully. " +
                    "UserId: {}, Partition: {}, Offset: {}",
                userId, result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());

        } catch (Exception e) {
            log.error("CRITICAL: Failed to publish wallet freeze request for userId: {}. " +
                    "Fraud case: {}. Error: {}",
                userId, fraudCaseId, e.getMessage(), e);

            // Fallback: Direct API call to wallet service
            try {
                walletServiceClient.freezeWalletDirect(userId, walletId, reason);
                log.warn("Fallback: Wallet frozen via direct API call. UserId: {}", userId);
            } catch (Exception apiEx) {
                log.error("CRITICAL FAILURE: Could not freeze wallet via Kafka or API. " +
                    "UserId: {}, FraudCase: {}", userId, fraudCaseId, apiEx);
                throw new FraudPreventionException(
                    "Failed to freeze wallet for fraud case: " + fraudCaseId, apiEx);
            }
        }
    }

    /**
     * Request wallet unfreeze after fraud investigation cleared.
     */
    public void requestWalletUnfreeze(
        UUID userId,
        UUID walletId,
        UUID fraudCaseId,
        String clearanceReason
    ) {
        WalletUnfreezeRequestedEvent event = WalletUnfreezeRequestedEvent.builder()
            .userId(userId)
            .walletId(walletId)
            .fraudCaseId(fraudCaseId)
            .clearanceReason(clearanceReason)
            .requestedAt(Instant.now())
            .eventId(UUID.randomUUID().toString())
            .build();

        log.info("Requesting wallet unfreeze. UserId: {}, WalletId: {}, Reason: {}",
            userId, walletId, clearanceReason);

        kafkaTemplate.send("wallet.unfreeze.requested", userId.toString(), event);
    }

    public enum FraudSeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    static class FraudPreventionException extends RuntimeException {
        public FraudPreventionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
