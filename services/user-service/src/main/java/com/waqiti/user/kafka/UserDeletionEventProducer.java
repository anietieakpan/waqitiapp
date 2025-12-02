package com.waqiti.user.kafka;

import com.waqiti.user.events.UserDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Producer for user.profile.deleted events.
 *
 * Critical event that triggers cascade deletion across all services.
 * Must be consumed by:
 * - wallet-service (soft delete wallets)
 * - payment-service (cancel pending payments)
 * - notification-service (unsubscribe)
 * - session-service (terminate sessions)
 * - audit-service (record deletion)
 *
 * @author Waqiti Platform Team
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserDeletionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "user.profile.deleted";

    /**
     * Publish user deletion event to trigger cascade actions.
     *
     * @param userId User ID being deleted
     * @param deletionReason Reason for deletion (GDPR, account closure, etc.)
     * @param requestedBy User who requested deletion
     */
    public void publishUserDeleted(UUID userId, String deletionReason, UUID requestedBy) {
        UserDeletedEvent event = UserDeletedEvent.builder()
            .userId(userId)
            .deletionReason(deletionReason)
            .requestedBy(requestedBy)
            .deletedAt(Instant.now())
            .eventId(UUID.randomUUID().toString())
            .correlationId(UUID.randomUUID())
            .build();

        log.info("Publishing user deletion event for userId: {}, reason: {}",
            userId, deletionReason);

        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send(TOPIC, userId.toString(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish user deletion event for userId: {}. Error: {}",
                    userId, ex.getMessage(), ex);
            } else {
                log.info("User deletion event published successfully. UserId: {}, Partition: {}, Offset: {}",
                    userId, result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }

    /**
     * Publish account suspension event.
     *
     * Critical security event that must block user access immediately.
     */
    public void publishAccountSuspended(UUID userId, String suspensionReason, UUID suspendedBy) {
        UserAccountSuspendedEvent event = UserAccountSuspendedEvent.builder()
            .userId(userId)
            .suspensionReason(suspensionReason)
            .suspendedBy(suspendedBy)
            .suspendedAt(Instant.now())
            .eventId(UUID.randomUUID().toString())
            .build();

        log.warn("Publishing account suspension event for userId: {}, reason: {}",
            userId, suspensionReason);

        kafkaTemplate.send("user.account.suspended", userId.toString(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("CRITICAL: Failed to publish suspension event for userId: {}",
                        userId, ex);
                } else {
                    log.info("Account suspension event published. UserId: {}", userId);
                }
            });
    }
}
