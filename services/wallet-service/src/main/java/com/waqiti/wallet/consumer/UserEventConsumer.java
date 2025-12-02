package com.waqiti.wallet.consumer;

import com.waqiti.events.user.UserCreatedEvent;
import com.waqiti.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for user events - breaks circular dependency with user-service
 * Instead of wallet-service calling user-service synchronously, it listens to user events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final WalletService walletService;

    @KafkaListener(
        topics = "user-created-events",
        groupId = "wallet-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserCreated(UserCreatedEvent event, Acknowledgment acknowledgment) {
        try {
            log.info("Received user created event: userId={}, email={}",
                event.getUserId(), event.getEmail());

            // Auto-create default wallet for new user
            // This breaks the circular dependency - no synchronous call to user-service needed
            walletService.createDefaultWalletForUser(
                event.getUserId(),
                event.getEmail(),
                event.getFirstName() + " " + event.getLastName()
            );

            log.info("Successfully created default wallet for user: {}", event.getUserId());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process user created event: userId={}",
                event.getUserId(), e);
            // Don't acknowledge - message will be retried or sent to DLQ
            throw e;
        }
    }
}
