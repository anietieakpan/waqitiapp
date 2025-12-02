package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Payment event model for event sourcing
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent extends FinancialEvent {

    private UUID paymentId;

    @Getter(AccessLevel.NONE)
    private UUID merchantId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String status;
    private String eventType;
    private Instant timestamp;
    private String description;
    private Map<String, Object> metadata;
    private UUID idempotencyKey;
    private String sourceSystem;
    private String targetSystem;
    private String failureReason;
    private Integer retryCount;
    private UUID parentEventId;
    private String eventVersion;

    /**
     * Create payment initiated event
     */
    public static PaymentEvent paymentInitiated(UUID paymentId, UUID userId, BigDecimal amount, String currency, String paymentMethod) {
        return PaymentEvent.builder()
            .paymentId(paymentId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .paymentMethod(paymentMethod)
            .status("INITIATED")
            .eventType("PAYMENT_INITIATED")
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Create payment completed event
     */
    public static PaymentEvent paymentCompleted(UUID paymentId, UUID userId, BigDecimal amount, String currency) {
        return PaymentEvent.builder()
            .paymentId(paymentId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .status("COMPLETED")
            .eventType("PAYMENT_COMPLETED")
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Create payment failed event
     */
    public static PaymentEvent paymentFailed(UUID paymentId, UUID userId, BigDecimal amount, String currency, String failureReason) {
        return PaymentEvent.builder()
            .paymentId(paymentId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .status("FAILED")
            .eventType("PAYMENT_FAILED")
            .failureReason(failureReason)
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Create payment processing event
     */
    public static PaymentEvent paymentProcessing(UUID paymentId, UUID userId, BigDecimal amount, String currency) {
        return PaymentEvent.builder()
            .paymentId(paymentId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .status("PROCESSING")
            .eventType("PAYMENT_PROCESSING")
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Create payment cancelled event
     */
    public static PaymentEvent paymentCancelled(UUID paymentId, UUID userId, BigDecimal amount, String currency, String reason) {
        return PaymentEvent.builder()
            .paymentId(paymentId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .status("CANCELLED")
            .eventType("PAYMENT_CANCELLED")
            .description(reason)
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Create payment refund event
     */
    public static PaymentEvent paymentRefunded(UUID paymentId, UUID userId, BigDecimal amount, String currency, UUID originalPaymentId) {
        return PaymentEvent.builder()
            .paymentId(paymentId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .status("REFUNDED")
            .eventType("PAYMENT_REFUNDED")
            .parentEventId(originalPaymentId)
            .timestamp(Instant.now())
            .eventVersion("1.0")
            .build();
    }
    
    /**
     * Check if payment is in terminal state
     */
    public boolean isTerminalState() {
        return "COMPLETED".equals(status) || 
               "FAILED".equals(status) || 
               "CANCELLED".equals(status) ||
               "REFUNDED".equals(status);
    }
    
    /**
     * Check if payment was successful
     */
    public boolean isSuccessful() {
        return "COMPLETED".equals(status);
    }
    
    /**
     * Check if payment failed
     */
    public boolean isFailed() {
        return "FAILED".equals(status) || "CANCELLED".equals(status);
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
}