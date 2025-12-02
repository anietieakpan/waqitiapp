package com.waqiti.webhook.service;

import com.waqiti.webhook.dto.PlatformEvent;
import com.waqiti.webhook.dto.WebhookEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Platform Event Publisher
 * 
 * Publishes platform-wide events that trigger webhook notifications
 * This service provides the missing producer for platform.events topic
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PlatformEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.platform-events:platform.events}")
    private String PLATFORM_EVENTS_TOPIC;

    /**
     * Publishes a user-related platform event
     */
    @Async
    public CompletableFuture<Void> publishUserEvent(
            String userId, String eventType, Map<String, Object> data) {
        
        return publishPlatformEvent(
            WebhookEventType.USER_EVENT,
            userId,
            eventType,
            data,
            "user-service"
        );
    }

    /**
     * Publishes a payment-related platform event
     */
    @Async
    public CompletableFuture<Void> publishPaymentEvent(
            String paymentId, String userId, String eventType, Map<String, Object> data) {
        
        return publishPlatformEvent(
            WebhookEventType.PAYMENT_EVENT,
            userId,
            eventType,
            data,
            "payment-service",
            Map.of("paymentId", paymentId)
        );
    }

    /**
     * Publishes a transaction-related platform event
     */
    @Async
    public CompletableFuture<Void> publishTransactionEvent(
            String transactionId, String userId, String eventType, Map<String, Object> data) {
        
        return publishPlatformEvent(
            WebhookEventType.TRANSACTION_EVENT,
            userId,
            eventType,
            data,
            "transaction-service",
            Map.of("transactionId", transactionId)
        );
    }

    /**
     * Publishes a security-related platform event
     */
    @Async
    public CompletableFuture<Void> publishSecurityEvent(
            String userId, String eventType, Map<String, Object> data) {
        
        return publishPlatformEvent(
            WebhookEventType.SECURITY_EVENT,
            userId,
            eventType,
            data,
            "security-service"
        );
    }

    /**
     * Publishes a compliance-related platform event
     */
    @Async
    public CompletableFuture<Void> publishComplianceEvent(
            String entityId, String eventType, Map<String, Object> data) {
        
        return publishPlatformEvent(
            WebhookEventType.COMPLIANCE_EVENT,
            entityId,
            eventType,
            data,
            "compliance-service"
        );
    }

    /**
     * Generic platform event publisher
     */
    private CompletableFuture<Void> publishPlatformEvent(
            WebhookEventType webhookType,
            String entityId,
            String eventType,
            Map<String, Object> data,
            String source) {
        
        return publishPlatformEvent(webhookType, entityId, eventType, data, source, Map.of());
    }

    /**
     * Generic platform event publisher with additional metadata
     */
    private CompletableFuture<Void> publishPlatformEvent(
            WebhookEventType webhookType,
            String entityId,
            String eventType,
            Map<String, Object> data,
            String source,
            Map<String, Object> additionalMetadata) {
        
        try {
            PlatformEvent event = PlatformEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .webhookType(webhookType)
                .entityId(entityId)
                .source(source)
                .data(data)
                .metadata(additionalMetadata)
                .timestamp(Instant.now())
                .version("1.0")
                .build();

            kafkaTemplate.send(PLATFORM_EVENTS_TOPIC, event.getEventId(), event);
            
            log.debug("Published platform event: {} from service: {} for entity: {}", 
                eventType, source, entityId);
            return CompletableFuture.completedFuture(null);
            
        } catch (Exception e) {
            log.error("Failed to publish platform event: {} from service: {}", eventType, source, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publishes a system health event
     */
    @Async
    public CompletableFuture<Void> publishHealthEvent(
            String serviceName, String healthStatus, Map<String, Object> healthData) {
        
        return publishPlatformEvent(
            WebhookEventType.SYSTEM_EVENT,
            serviceName,
            "HEALTH_STATUS_CHANGED",
            Map.of(
                "status", healthStatus,
                "healthData", healthData
            ),
            "health-monitor"
        );
    }

    /**
     * Publishes a configuration change event
     */
    @Async
    public CompletableFuture<Void> publishConfigurationEvent(
            String serviceName, String configKey, Object oldValue, Object newValue, String changedBy) {
        
        return publishPlatformEvent(
            WebhookEventType.SYSTEM_EVENT,
            serviceName,
            "CONFIGURATION_CHANGED",
            Map.of(
                "configKey", configKey,
                "oldValue", oldValue,
                "newValue", newValue,
                "changedBy", changedBy
            ),
            "configuration-service"
        );
    }
}