package com.waqiti.wallet.kafka;

import com.waqiti.common.kafka.annotation.RetryableKafkaListener;
import com.waqiti.user.events.UserDeletedEvent;
import com.waqiti.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Consumer for user.profile.deleted events.
 *
 * Handles cascade deletion of user wallets when user account is deleted.
 * Implements soft delete to maintain audit trail per GDPR requirements.
 *
 * @author Waqiti Platform Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserDeletionConsumer {

    private final WalletService walletService;

    @RetryableKafkaListener(
        topics = "user.profile.deleted",
        groupId = "wallet-user-deletion-processor",
        attempts = 5,
        backoffDelay = 1000
    )
    public void handleUserDeleted(@Payload UserDeletedEvent event) {
        UUID userId = event.getUserId();

        log.info("Processing user deletion event. UserId: {}, Reason: {}",
            userId, event.getDeletionReason());

        try {
            // Soft delete all user wallets
            int walletsDeleted = walletService.softDeleteUserWallets(
                userId,
                event.getDeletionReason()
            );

            log.info("Successfully soft-deleted {} wallets for user: {}",
                walletsDeleted, userId);

            // Optionally publish wallet deletion event
            walletEventPublisher.publishWalletsDeletionCompleted(userId, walletsDeleted);

        } catch (Exception e) {
            log.error("Failed to process user deletion for userId: {}. Error: {}",
                userId, e.getMessage(), e);
            throw e; // Will trigger retry
        }
    }

    @DltHandler
    public void handleUserDeletionDlt(
        @Payload UserDeletedEvent event,
        @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage,
        @Header("dlq_retry_count") int retryCount
    ) {
        UUID userId = event.getUserId();

        log.error("CRITICAL: User deletion processing failed permanently. " +
                "UserId: {}, Retries: {}, Error: {}",
            userId, retryCount, errorMessage);

        // Create manual intervention task
        manualInterventionService.createTask(
            "USER_WALLET_DELETION_FAILED",
            String.format("Failed to delete wallets for user %s after %d attempts",
                userId, retryCount),
            event,
            "HIGH"
        );

        // Alert operations team
        alertService.sendCriticalAlert(
            "User Wallet Deletion Failed",
            String.format("UserId: %s requires manual wallet deletion. Error: %s",
                userId, errorMessage)
        );
    }

    /**
     * Handle account suspension - freeze all wallets immediately.
     */
    @RetryableKafkaListener(
        topics = "user.account.suspended",
        groupId = "wallet-account-suspension-processor",
        attempts = 3, // Fewer retries for time-sensitive security action
        backoffDelay = 500
    )
    public void handleAccountSuspended(@Payload UserAccountSuspendedEvent event) {
        UUID userId = event.getUserId();

        log.warn("Processing account suspension. UserId: {}, Reason: {}",
            userId, event.getSuspensionReason());

        try {
            // Freeze all user wallets immediately
            int walletsFrozen = walletService.freezeUserWallets(
                userId,
                "ACCOUNT_SUSPENDED: " + event.getSuspensionReason()
            );

            log.warn("Froze {} wallets for suspended user: {}", walletsFrozen, userId);

        } catch (Exception e) {
            log.error("CRITICAL: Failed to freeze wallets for suspended user: {}",
                userId, e);
            throw e;
        }
    }

    @DltHandler
    public void handleSuspensionDlt(@Payload UserAccountSuspendedEvent event) {
        log.error("CRITICAL SECURITY FAILURE: Could not freeze wallets for suspended user: {}",
            event.getUserId());

        // Immediate escalation
        alertService.sendSecurityAlert(
            "CRITICAL: Wallet Freeze Failed",
            String.format("User %s is suspended but wallets could not be frozen. " +
                "Manual intervention required IMMEDIATELY.", event.getUserId())
        );
    }
}
