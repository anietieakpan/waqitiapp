package com.waqiti.payment.webhook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a webhook event to be delivered
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {
    
    private String eventId;
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    private Instant timestamp;
    private Map<String, Object> payload;
    private Map<String, String> headers;
    private int version;
    private String source;
    
    /**
     * Get the event type for routing and filtering
     */
    public String getEventType() {
        return eventType;
    }
    
    /**
     * Get the unique event identifier
     */
    public String getEventId() {
        return eventId;
    }
    
    /**
     * Create a webhook event for payment processing
     */
    public static WebhookEvent createPaymentEvent(String eventType, String paymentId, Map<String, Object> payload) {
        return WebhookEvent.builder()
            .eventId(java.util.UUID.randomUUID().toString())
            .eventType(eventType)
            .aggregateId(paymentId)
            .aggregateType("Payment")
            .timestamp(Instant.now())
            .payload(payload)
            .version(1)
            .source("payment-service")
            .build();
    }
    
    /**
     * Create a webhook event for transaction processing
     */
    public static WebhookEvent createTransactionEvent(String eventType, String transactionId, Map<String, Object> payload) {
        return WebhookEvent.builder()
            .eventId(java.util.UUID.randomUUID().toString())
            .eventType(eventType)
            .aggregateId(transactionId)
            .aggregateType("Transaction")
            .timestamp(Instant.now())
            .payload(payload)
            .version(1)
            .source("payment-service")
            .build();
    }
    
    /**
     * Create a webhook event for user-related actions
     */
    public static WebhookEvent createUserEvent(String eventType, String userId, Map<String, Object> payload) {
        return WebhookEvent.builder()
            .eventId(java.util.UUID.randomUUID().toString())
            .eventType(eventType)
            .aggregateId(userId)
            .aggregateType("User")
            .timestamp(Instant.now())
            .payload(payload)
            .version(1)
            .source("payment-service")
            .build();
    }
}