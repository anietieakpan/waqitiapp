package com.waqiti.common.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Publisher for payment-related events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    
    /**
     * Publish payment completed event
     */
    public void publishPaymentCompleted(UUID paymentId, UUID userId, Double amount, String currency) {
        PaymentEvent event = PaymentEvent.builder()
                .eventType("PAYMENT_COMPLETED")
                .paymentId(paymentId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .timestamp(LocalDateTime.now())
                .build();
        
        publishEvent(event);
    }
    
    /**
     * Publish payment failed event
     */
    public void publishPaymentFailed(UUID paymentId, UUID userId, String reason) {
        PaymentEvent event = PaymentEvent.builder()
                .eventType("PAYMENT_FAILED")
                .paymentId(paymentId)
                .userId(userId)
                .errorMessage(reason)
                .timestamp(LocalDateTime.now())
                .build();
        
        publishEvent(event);
    }
    
    /**
     * Publish refund initiated event
     */
    public void publishRefundInitiated(UUID paymentId, UUID refundId, Double amount) {
        PaymentEvent event = PaymentEvent.builder()
                .eventType("REFUND_INITIATED")
                .paymentId(paymentId)
                .refundId(refundId)
                .amount(amount)
                .timestamp(LocalDateTime.now())
                .build();
        
        publishEvent(event);
    }
    
    /**
     * Publish dispute created event
     */
    public void publishDisputeCreated(UUID paymentId, String disputeId, String reason) {
        PaymentEvent event = PaymentEvent.builder()
                .eventType("DISPUTE_CREATED")
                .paymentId(paymentId)
                .disputeId(disputeId)
                .disputeReason(reason)
                .timestamp(LocalDateTime.now())
                .build();
        
        publishEvent(event);
    }
    
    /**
     * Publish subscription created event
     */
    public void publishSubscriptionCreated(String subscriptionId, UUID userId, String planId) {
        PaymentEvent event = PaymentEvent.builder()
                .eventType("SUBSCRIPTION_CREATED")
                .subscriptionId(subscriptionId)
                .userId(userId)
                .planId(planId)
                .timestamp(LocalDateTime.now())
                .build();
        
        publishEvent(event);
    }
    
    /**
     * Publish subscription cancelled event
     */
    public void publishSubscriptionCancelled(String subscriptionId, UUID userId) {
        PaymentEvent event = PaymentEvent.builder()
                .eventType("SUBSCRIPTION_CANCELLED")
                .subscriptionId(subscriptionId)
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .build();
        
        publishEvent(event);
    }
    
    /**
     * Publish generic payment event
     */
    public void publishEvent(String eventType, Map<String, Object> eventData) {
        PaymentEvent event = PaymentEvent.builder()
                .eventType(eventType)
                .metadata(eventData)
                .timestamp(LocalDateTime.now())
                .build();
        
        publishEvent(event);
    }
    
    private void publishEvent(PaymentEvent event) {
        try {
            kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, event.getPaymentId() != null ? event.getPaymentId().toString() : UUID.randomUUID().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish payment event: {}", event.getEventType(), ex);
                        } else {
                            log.info("Published payment event: {} for payment: {}", event.getEventType(), event.getPaymentId());
                        }
                    });
        } catch (Exception e) {
            log.error("Error publishing payment event: {}", event.getEventType(), e);
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PaymentEvent {
        private String eventType;
        private UUID paymentId;
        private UUID userId;
        private UUID refundId;
        private String subscriptionId;
        private String planId;
        private String disputeId;
        private String disputeReason;
        private Double amount;
        private String currency;
        private String errorMessage;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
    }
}