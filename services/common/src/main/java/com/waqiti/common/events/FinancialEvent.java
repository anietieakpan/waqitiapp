package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base financial event for event sourcing
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialEvent implements DomainEvent {
    
    private UUID eventId;
    private String eventType;
    private String eventCategory;
    private UUID entityId;
    protected UUID userId;
    private BigDecimal amount;
    private String currency;
    private String status;
    protected Instant timestamp;
    private String description;
    private Map<String, Object> metadata;
    private UUID correlationId;
    private UUID causationId;
    private String sourceSystem;
    private String targetSystem;
    private Integer version;
    private String aggregateType;
    private UUID aggregateId;
    private Long sequenceNumber;
    private String partitionKey;

    /**
     * Create a new financial event
     */
    public static FinancialEvent create(String eventType, UUID entityId, UUID userId, BigDecimal amount, String currency) {
        return FinancialEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .entityId(entityId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .timestamp(Instant.now())
            .version(1)
            .build();
    }
    
    /**
     * Check if event is a payment event
     */
    public boolean isPaymentEvent() {
        return eventType != null && eventType.startsWith("PAYMENT_");
    }
    
    /**
     * Check if event is a transaction event
     */
    public boolean isTransactionEvent() {
        return eventType != null && (eventType.startsWith("TRANSACTION_") || eventType.startsWith("TRANSFER_"));
    }
    
    /**
     * Check if event is a wallet event
     */
    public boolean isWalletEvent() {
        return eventType != null && eventType.startsWith("WALLET_");
    }
    
    /**
     * Check if event represents success
     */
    public boolean isSuccessful() {
        return "COMPLETED".equals(status) || "SUCCESS".equals(status);
    }
    
    /**
     * Check if event represents failure
     */
    public boolean isFailed() {
        return "FAILED".equals(status) || "ERROR".equals(status) || "CANCELLED".equals(status);
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        if (timestamp == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
    
    /**
     * Convert to PaymentEvent
     */
    public PaymentEvent toPaymentEvent() {
        return PaymentEvent.builder()
            .paymentId(entityId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .status(status)
            .eventType(eventType)
            .timestamp(timestamp)
            .description(description)
            .metadata(metadata)
            .correlationId(correlationId)
            .build();
    }
    
    /**
     * Convert to TransactionEvent
     */
    public TransactionEvent toTransactionEvent() {
        return TransactionEvent.builder()
            .transactionId(entityId)
            .userId(userId)
            .amount(amount)
            .currency(currency)
            .status(status)
            .eventType(eventType)
            .timestamp(timestamp)
            .description(description)
            .metadata(metadata)
            .correlationId(correlationId)
            .build();
    }

    // DomainEvent interface implementations
    @Override
    public String getEventId() {
        return eventId != null ? eventId.toString() : null;
    }

    @Override
    public String getTopic() {
        return "financial-events";
    }

    @Override
    public String getAggregateId() {
        return aggregateId != null ? aggregateId.toString() : (entityId != null ? entityId.toString() : null);
    }

    @Override
    public String getAggregateName() {
        return aggregateType != null ? aggregateType : "FinancialAggregate";
    }

    @Override
    public Long getVersion() {
        return version != null ? version.longValue() : 1L;
    }

    @Override
    public String getCorrelationId() {
        return correlationId != null ? correlationId.toString() : null;
    }

    @Override
    public String getUserId() {
        return userId != null ? userId.toString() : null;
    }

    @Override
    public String getSourceService() {
        return sourceSystem != null ? sourceSystem : "financial-service";
    }

    @Override
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}