package com.waqiti.user.events.producers;

import com.waqiti.common.events.user.UserUpdatedEvent;
import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.user.domain.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade event producer for user update events.
 * Publishes user.updated events when user profiles are modified.
 * 
 * Features:
 * - Transactional event publishing
 * - Privacy-compliant data publishing
 * - Change detection and delta publishing
 * - Comprehensive audit trail
 * - Data anonymization for sensitive fields
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserUpdatedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final String TOPIC = "user.updated";

    /**
     * Publishes user updated event after successful transaction commit
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserUpdated(UserUpdateEventData eventData) {
        try {
            log.info("Publishing user updated event for user: {}", eventData.getUserId());

            // Create privacy-compliant event
            UserUpdatedEvent event = createUserUpdatedEvent(eventData);

            // Publish event
            publishEvent(event, eventData);

            // Metrics
            metricsService.incrementCounter("user.updated.event.published",
                Map.of(
                    "update_type", eventData.getUpdateType(),
                    "user_type", eventData.getUserType()
                ));

            log.info("Successfully published user updated event: {} for user: {}", 
                    event.getEventId(), eventData.getUserId());

        } catch (Exception e) {
            log.error("Failed to publish user updated event for user {}: {}", 
                    eventData.getUserId(), e.getMessage(), e);
            
            metricsService.incrementCounter("user.updated.event.publish_failed");
            
            auditLogger.logError("USER_UPDATED_EVENT_PUBLISH_FAILED",
                "system", eventData.getUserId(), e.getMessage(),
                Map.of("userId", eventData.getUserId()));
        }
    }

    /**
     * Manually publish user updated event
     */
    public CompletableFuture<SendResult<String, Object>> publishUserUpdated(User user, String updateType, Map<String, Object> changes) {
        UserUpdateEventData eventData = UserUpdateEventData.builder()
            .userId(user.getId())
            .userType(user.getUserType())
            .updateType(updateType)
            .changes(changes)
            .previousVersion(user.getVersion() - 1)
            .currentVersion(user.getVersion())
            .updatedAt(user.getUpdatedAt())
            .build();

        UserUpdatedEvent event = createUserUpdatedEvent(eventData);
        return publishEvent(event, eventData);
    }

    private UserUpdatedEvent createUserUpdatedEvent(UserUpdateEventData eventData) {
        return UserUpdatedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .userId(eventData.getUserId())
            .userType(eventData.getUserType())
            .updateType(eventData.getUpdateType())
            .changes(sanitizeChanges(eventData.getChanges()))
            .previousVersion(eventData.getPreviousVersion())
            .currentVersion(eventData.getCurrentVersion())
            .changedFields(eventData.getChanges() != null ? eventData.getChanges().keySet() : null)
            .updatedAt(eventData.getUpdatedAt())
            .timestamp(LocalDateTime.now())
            .build();
    }

    private Map<String, Object> sanitizeChanges(Map<String, Object> changes) {
        if (changes == null) {
            return null;
        }

        // Remove sensitive fields from the event
        Map<String, Object> sanitized = new java.util.HashMap<>(changes);
        
        // Remove PII and sensitive data
        sanitized.remove("password");
        sanitized.remove("ssn");
        sanitized.remove("taxId");
        sanitized.remove("bankAccountNumber");
        sanitized.remove("creditCardNumber");
        
        // Anonymize email (keep domain for analytics)
        if (sanitized.containsKey("email")) {
            String email = (String) sanitized.get("email");
            if (email != null && email.contains("@")) {
                String domain = email.substring(email.indexOf("@"));
                sanitized.put("email", "***" + domain);
            }
        }
        
        // Anonymize phone number
        if (sanitized.containsKey("phoneNumber")) {
            String phone = (String) sanitized.get("phoneNumber");
            if (phone != null && phone.length() > 4) {
                sanitized.put("phoneNumber", "***" + phone.substring(phone.length() - 4));
            }
        }
        
        return sanitized;
    }

    private CompletableFuture<SendResult<String, Object>> publishEvent(UserUpdatedEvent event, UserUpdateEventData eventData) {
        try {
            // Use user ID as partition key for ordering
            String partitionKey = eventData.getUserId();

            var producerRecord = new org.apache.kafka.clients.producer.ProducerRecord<>(
                TOPIC, 
                partitionKey, 
                event
            );
            
            // Add headers
            producerRecord.headers().add("event-type", "user-updated".getBytes());
            producerRecord.headers().add("correlation-id", UUID.randomUUID().toString().getBytes());
            producerRecord.headers().add("source-service", "user-service".getBytes());
            producerRecord.headers().add("event-version", "1.0".getBytes());
            producerRecord.headers().add("update-type", eventData.getUpdateType().getBytes());

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(producerRecord);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send user updated event for user {}: {}", 
                            eventData.getUserId(), ex.getMessage(), ex);
                    
                    metricsService.incrementCounter("user.updated.event.send_failed");
                } else {
                    log.debug("User updated event sent successfully: partition={}, offset={}", 
                            result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                    
                    // Create audit trail
                    auditLogger.logUserEvent(
                        "USER_UPDATED_EVENT_PUBLISHED",
                        eventData.getUserId(),
                        event.getEventId(),
                        eventData.getUpdateType(),
                        "user_event_producer",
                        true,
                        Map.of(
                            "userId", eventData.getUserId(),
                            "eventId", event.getEventId(),
                            "updateType", eventData.getUpdateType(),
                            "changedFields", event.getChangedFields() != null ? String.join(",", event.getChangedFields()) : "none",
                            "topic", TOPIC,
                            "partition", String.valueOf(result.getRecordMetadata().partition()),
                            "offset", String.valueOf(result.getRecordMetadata().offset())
                        )
                    );
                }
            });

            return future;

        } catch (Exception e) {
            log.error("Exception while publishing user updated event for user {}: {}", 
                    eventData.getUserId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Data class for user update event information
     */
    @lombok.Data
    @lombok.Builder
    public static class UserUpdateEventData {
        private String userId;
        private String userType;
        private String updateType;
        private Map<String, Object> changes;
        private Long previousVersion;
        private Long currentVersion;
        private LocalDateTime updatedAt;
    }
}