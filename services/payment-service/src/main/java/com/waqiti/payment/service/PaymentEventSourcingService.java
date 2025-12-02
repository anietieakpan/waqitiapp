package com.waqiti.payment.service;

import com.waqiti.payment.core.model.PaymentResult;
import com.waqiti.payment.core.model.UnifiedPaymentRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service that handles event sourcing for payment operations
 * Records all payment events for audit and replay capabilities
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEventSourcingService {
    
    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Record a payment created event
     */
    public void recordPaymentCreated(UnifiedPaymentRequest request, PaymentResult result) {
        PaymentEvent event = PaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PAYMENT_CREATED")
                .paymentId(result.getPaymentId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .timestamp(LocalDateTime.now())
                .payload(createPayload(request, result))
                .build();
        
        publishEvent(event);
    }
    
    /**
     * Record a payment completed event
     */
    public void recordPaymentCompleted(String paymentId, PaymentResult result) {
        PaymentEvent event = PaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PAYMENT_COMPLETED")
                .paymentId(paymentId)
                .amount(result.getAmount())
                .currency(result.getCurrency())
                .timestamp(LocalDateTime.now())
                .payload(createPayload(null, result))
                .build();
        
        publishEvent(event);
    }
    
    /**
     * Record a payment failed event
     */
    public void recordPaymentFailed(String paymentId, String reason, String errorCode) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        payload.put("errorCode", errorCode);
        
        PaymentEvent event = PaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PAYMENT_FAILED")
                .paymentId(paymentId)
                .timestamp(LocalDateTime.now())
                .payload(payload)
                .build();
        
        publishEvent(event);
    }
    
    /**
     * Record a payment cancelled event
     */
    public void recordPaymentCancelled(String paymentId, String userId, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("reason", reason);
        payload.put("cancelledBy", userId);
        
        PaymentEvent event = PaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PAYMENT_CANCELLED")
                .paymentId(paymentId)
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .payload(payload)
                .build();
        
        publishEvent(event);
    }
    
    /**
     * Record a payment refunded event
     */
    public void recordPaymentRefunded(String paymentId, Double refundAmount, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("refundAmount", refundAmount);
        payload.put("reason", reason);
        
        PaymentEvent event = PaymentEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PAYMENT_REFUNDED")
                .paymentId(paymentId)
                .amount(refundAmount)
                .timestamp(LocalDateTime.now())
                .payload(payload)
                .build();
        
        publishEvent(event);
    }
    
    private void publishEvent(PaymentEvent event) {
        try {
            kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, event.getPaymentId(), event);
            log.debug("Published payment event: {} for payment: {}", event.getEventType(), event.getPaymentId());
        } catch (Exception e) {
            log.error("Failed to publish payment event: {}", event, e);
            // Consider dead letter queue or retry mechanism
        }
    }
    
    private Map<String, Object> createPayload(UnifiedPaymentRequest request, PaymentResult result) {
        Map<String, Object> payload = new HashMap<>();
        
        if (request != null) {
            payload.put("requestId", request.getRequestId());
            payload.put("paymentType", request.getPaymentType());
            payload.put("paymentMethod", request.getPaymentMethod());
            payload.put("provider", request.getProvider());
            payload.put("description", request.getDescription());
            payload.put("recipientId", request.getRecipientId());
        }
        
        if (result != null) {
            payload.put("status", result.getStatus());
            payload.put("transactionId", result.getTransactionId());
            payload.put("provider", result.getProvider());
            payload.put("fee", result.getFee());
            payload.put("netAmount", result.getNetAmount());
            payload.put("confirmationNumber", result.getConfirmationNumber());
        }
        
        return payload;
    }
    
    /**
     * Payment event model
     *
     * CRITICAL FIX (November 17, 2025 - Production Readiness):
     * Changed amount from Double to BigDecimal to prevent precision loss
     * in financial event sourcing. Event sourcing requires exact decimal
     * precision for audit trail and replay capability.
     *
     * Event sourcing is immutable, so historical data integrity is paramount.
     * Using Double for money would cause:
     * 1. Precision loss during event replay
     * 2. Incorrect financial reconciliation
     * 3. Audit trail inaccuracies
     * 4. Potential regulatory compliance violations
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PaymentEvent {
        private String eventId;
        private String eventType;
        private String paymentId;
        private String userId;

        /**
         * Payment amount with exact decimal precision.
         * CRITICAL: Must use BigDecimal for financial data, never Float or Double.
         * Event sourcing requires precise replay of historical transactions.
         */
        private java.math.BigDecimal amount;

        private String currency;
        private LocalDateTime timestamp;
        private Map<String, Object> payload;

        /**
         * Deprecated: For backward compatibility with legacy events only.
         * New code should NEVER use this method.
         *
         * @deprecated Use getAmount() which returns BigDecimal
         */
        @Deprecated(since = "2.0.0", forRemoval = true)
        public Double getAmountAsDouble() {
            return amount != null ? amount.doubleValue() : null;
        }

        /**
         * Deprecated: For backward compatibility with legacy event deserialization only.
         * Converts Double to BigDecimal with validation.
         *
         * @deprecated Use setAmount(BigDecimal) instead
         */
        @Deprecated(since = "2.0.0", forRemoval = true)
        public void setAmountFromDouble(Double amount) {
            if (amount != null) {
                // Log warning for production monitoring
                log.warn("DEPRECATED: Setting payment event amount from Double. " +
                        "This may cause precision loss. EventId: {}, Amount: {}",
                        eventId, amount);
                this.amount = java.math.BigDecimal.valueOf(amount);
            } else {
                this.amount = null;
            }
        }
    }
}