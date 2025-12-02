package com.waqiti.payment.consumer;

import com.waqiti.events.user.UserCreatedEvent;
import com.waqiti.payment.cache.UserCacheService;
import com.waqiti.payment.client.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer for user events - breaks circular dependency with user-service
 * Populates local cache instead of making synchronous Feign calls
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final UserCacheService userCacheService;

    @KafkaListener(
        topics = "user-created-events",
        groupId = "payment-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserCreated(UserCreatedEvent event, Acknowledgment acknowledgment) {
        try {
            log.info("User created event received: userId={}, email={}",
                event.getUserId(), event.getEmail());

            // Build UserResponse DTO from event
            UserResponse user = UserResponse.builder()
                .userId(UUID.fromString(event.getUserId()))
                .email(event.getEmail())
                .phoneNumber(event.getPhoneNumber())
                .firstName(event.getFirstName())
                .lastName(event.getLastName())
                .kycStatus(event.getKycStatus().toString())
                .build();

            // Cache user data locally - breaks dependency on user-service
            // payment-service no longer needs to call user-service synchronously
            userCacheService.cacheUser(user);

            log.info("Successfully cached user data for userId: {}", event.getUserId());
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process user created event: userId={}",
                event.getUserId(), e);
            // Don't acknowledge - message will be retried or sent to DLQ
            throw e;
        }
    }
}
