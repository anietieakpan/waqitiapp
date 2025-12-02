package com.waqiti.wallet.kafka;

import com.waqiti.common.kafka.annotation.RetryableKafkaListener;
import com.waqiti.fraud.events.WalletFreezeRequestedEvent;
import com.waqiti.wallet.service.WalletFreezeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Consumer for wallet.freeze.requested events from fraud detection.
 *
 * Critical security consumer that must process freeze requests immediately.
 * Uses minimal retries (3) with short backoff for time-sensitive fraud prevention.
 *
 * @author Waqiti Platform Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WalletFreezeConsumer {

    private final WalletFreezeService walletFreezeService;
    private final AlertService alertService;
    private final AuditService auditService;

    @RetryableKafkaListener(
        topics = "wallet.freeze.requested",
        groupId = "wallet-fraud-freeze-processor",
        attempts = 3, // Minimal retries for time-sensitive action
        backoffDelay = 200, // Very short backoff (200ms)
        backoffMultiplier = 1.5
    )
    public void handleWalletFreezeRequest(@Payload WalletFreezeRequestedEvent event) {
        UUID userId = event.getUserId();
        UUID walletId = event.getWalletId();
        UUID fraudCaseId = event.getFraudCaseId();

        log.warn("FRAUD PREVENTION: Processing wallet freeze request. " +
                "UserId: {}, WalletId: {}, FraudCase: {}, Severity: {}, Reason: {}",
            userId, walletId, fraudCaseId, event.getSeverity(), event.getReason());

        try {
            // Freeze wallet(s) immediately
            if (walletId != null) {
                // Freeze specific wallet
                walletFreezeService.freezeWallet(
                    walletId,
                    String.format("FRAUD_PREVENTION: %s (Case: %s)",
                        event.getReason(), fraudCaseId)
                );
                log.warn("Wallet frozen. WalletId: {}, FraudCase: {}", walletId, fraudCaseId);
            } else {
                // Freeze all user wallets
                int frozen = walletFreezeService.freezeAllUserWallets(
                    userId,
                    String.format("FRAUD_PREVENTION: %s (Case: %s)",
                        event.getReason(), fraudCaseId)
                );
                log.warn("All wallets frozen for user. UserId: {}, Count: {}, FraudCase: {}",
                    userId, frozen, fraudCaseId);
            }

            // Audit the action
            auditService.recordWalletFreeze(userId, walletId, fraudCaseId, event.getReason());

            // Notify user (if not too severe - don't tip off fraudster)
            if (event.getSeverity() != FraudSeverity.CRITICAL) {
                notificationService.sendWalletFrozenNotification(userId, walletId);
            }

            // Confirm freeze completed
            walletEventPublisher.publishWalletFrozeCompleted(userId, walletId, fraudCaseId);

        } catch (WalletNotFoundException e) {
            log.error("Wallet not found for freeze request. WalletId: {}, UserId: {}",
                walletId, userId);
            // Don't retry for not found
            throw new NonRetryableException("Wallet not found", e);

        } catch (Exception e) {
            log.error("Failed to freeze wallet. UserId: {}, WalletId: {}, FraudCase: {}. Error: {}",
                userId, walletId, fraudCaseId, e.getMessage(), e);
            throw e; // Will trigger retry
        }
    }

    @DltHandler
    public void handleWalletFreezeDlt(@Payload WalletFreezeRequestedEvent event) {
        UUID userId = event.getUserId();
        UUID fraudCaseId = event.getFraudCaseId();

        log.error("CRITICAL SECURITY FAILURE: Could not freeze wallet after retries. " +
                "UserId: {}, WalletId: {}, FraudCase: {}",
            userId, event.getWalletId(), fraudCaseId);

        // CRITICAL: Immediate escalation to security team
        alertService.sendCriticalSecurityAlert(
            "URGENT: Wallet Freeze Failed",
            String.format("FRAUD PREVENTION FAILURE: Unable to freeze wallet for user %s. " +
                    "Fraud case: %s. IMMEDIATE MANUAL INTERVENTION REQUIRED. " +
                    "Consider blocking user at gateway level.",
                userId, fraudCaseId)
        );

        // Create high-priority manual task
        manualInterventionService.createUrgentTask(
            "WALLET_FREEZE_FAILED",
            String.format("Failed to freeze wallet for fraud case %s after multiple attempts. " +
                    "User: %s. Block user at gateway if necessary.", fraudCaseId, userId),
            event,
            "CRITICAL"
        );

        // Audit the failure
        auditService.recordWalletFreezeFailed(userId, event.getWalletId(),
            fraudCaseId, "DLT reached after retries");
    }

    /**
     * Handle wallet unfreeze after fraud case cleared.
     */
    @RetryableKafkaListener(
        topics = "wallet.unfreeze.requested",
        groupId = "wallet-fraud-unfreeze-processor",
        attempts = 5
    )
    public void handleWalletUnfreezeRequest(@Payload WalletUnfreezeRequestedEvent event) {
        UUID userId = event.getUserId();
        UUID walletId = event.getWalletId();

        log.info("Processing wallet unfreeze request. UserId: {}, WalletId: {}, Reason: {}",
            userId, walletId, event.getClearanceReason());

        try {
            if (walletId != null) {
                walletFreezeService.unfreezeWallet(walletId, event.getClearanceReason());
            } else {
                walletFreezeService.unfreezeAllUserWallets(userId, event.getClearanceReason());
            }

            log.info("Wallet unfrozen successfully. UserId: {}, WalletId: {}", userId, walletId);

            // Notify user
            notificationService.sendWalletUnfrozenNotification(userId, walletId);

        } catch (Exception e) {
            log.error("Failed to unfreeze wallet. UserId: {}, WalletId: {}",
                userId, walletId, e);
            throw e;
        }
    }

    static class NonRetryableException extends RuntimeException {
        public NonRetryableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
