package com.waqiti.common.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Centralized event publisher for the Waqiti platform
 * Handles both internal Spring events and external Kafka events
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventPublisher {

    private final ApplicationEventPublisher springEventPublisher;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publish event both internally (Spring) and externally (Kafka)
     */
    @Async("eventPublisherTaskExecutor")
    public CompletableFuture<Void> publish(DomainEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Publish to Spring's internal event system
                publishInternal(event);
                
                // Publish to Kafka for external consumers
                publishExternal(event);
                
                log.debug("Successfully published event: {}", event.getEventType());
                
            } catch (Exception e) {
                log.error("Failed to publish event: {}", event.getEventType(), e);
                throw new EventPublishingException("Failed to publish event", e);
            }
        });
    }

    /**
     * Publish event only internally (Spring event system)
     */
    public void publishInternal(DomainEvent event) {
        try {
            springEventPublisher.publishEvent(event);
            log.debug("Published internal event: {}", event.getEventType());
        } catch (Exception e) {
            log.error("Failed to publish internal event: {}", event.getEventType(), e);
            throw new EventPublishingException("Failed to publish internal event", e);
        }
    }

    /**
     * Publish event only externally (Kafka)
     */
    @Async("eventPublisherTaskExecutor")
    public CompletableFuture<Void> publishExternal(DomainEvent event) {
        return CompletableFuture.runAsync(() -> {
            try {
                String topic = determineKafkaTopic(event);
                String key = event.getAggregateId() != null ? event.getAggregateId() : event.getEventId();
                
                KafkaEventWrapper wrapper = KafkaEventWrapper.builder()
                    .eventId(event.getEventId())
                    .eventType(event.getEventType())
                    .aggregateId(event.getAggregateId())
                    .aggregateType(event.getAggregateType())
                    .version(event.getVersion())
                    .timestamp(event.getTimestamp())
                    .payload(event)
                    .source("waqiti-platform")
                    .build();
                
                kafkaTemplate.send(topic, key, wrapper);
                
                log.debug("Published external event to topic {}: {}", topic, event.getEventType());
                
            } catch (Exception e) {
                log.error("Failed to publish external event: {}", event.getEventType(), e);
                throw new EventPublishingException("Failed to publish external event", e);
            }
        });
    }

    /**
     * Publish configuration-specific event
     */
    public void publishConfigurationEvent(ConfigurationEvent event) {
        publish(event);
    }

    /**
     * Publish user-specific event
     */
    public void publishUserEvent(UserEvent event) {
        publish(event);
    }

    /**
     * Publish payment-specific event
     */
    public void publishPaymentEvent(PaymentEvent event) {
        publish(event);
    }

    /**
     * Publish wallet-specific event
     */
    public void publishWalletEvent(WalletEvent event) {
        publish(event);
    }

    /**
     * Publish security-specific event
     */
    public void publishSecurityEvent(SecurityEvent event) {
        publish(event);
    }

    /**
     * Publish notification-specific event
     */
    public void publishNotificationEvent(NotificationEvent event) {
        publish(event);
    }

    /**
     * Publish system-wide event
     */
    public void publishSystemEvent(SystemEvent event) {
        publish(event);
    }

    /**
     * Publish batch of events efficiently
     */
    @Async("eventPublisherTaskExecutor")
    public CompletableFuture<Void> publishBatch(java.util.List<DomainEvent> events) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Publishing batch of {} events", events.size());
                
                for (DomainEvent event : events) {
                    try {
                        publishInternal(event);
                        publishExternal(event).join(); // Wait for external publishing
                    } catch (Exception e) {
                        log.error("Failed to publish event in batch: {}", event.getEventType(), e);
                        // Continue with other events
                    }
                }
                
                log.info("Completed batch publishing of {} events", events.size());
                
            } catch (Exception e) {
                log.error("Failed to publish event batch", e);
                throw new EventPublishingException("Failed to publish event batch", e);
            }
        });
    }

    private String determineKafkaTopic(DomainEvent event) {
        String eventType = event.getEventType().toLowerCase();
        
        if (eventType.contains("configuration") || eventType.contains("config")) {
            return "configuration-events";
        } else if (eventType.contains("user")) {
            return "user-events";
        } else if (eventType.contains("payment") || eventType.contains("transaction")) {
            return "payment-events";
        } else if (eventType.contains("wallet")) {
            return "wallet-events";
        } else if (eventType.contains("security") || eventType.contains("auth")) {
            return "security-events";
        } else if (eventType.contains("notification")) {
            return "notification-events";
        } else if (eventType.contains("system")) {
            return "system-events";
        } else {
            return "domain-events"; // Default topic
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class KafkaEventWrapper {
        private String eventId;
        private String eventType;
        private String aggregateId;
        private String aggregateType;
        private Long version;
        private Instant timestamp;
        private Object payload;
        private String source;
    }

    /**
     * Custom exception for event publishing failures
     */
    public static class EventPublishingException extends RuntimeException {
        public EventPublishingException(String message) {
            super(message);
        }

        public EventPublishingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}