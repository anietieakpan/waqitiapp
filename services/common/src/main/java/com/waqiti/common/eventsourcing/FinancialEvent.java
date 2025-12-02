package com.waqiti.common.eventsourcing;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;

/**
 * Base class for all financial events in the event sourcing system
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PaymentCreatedEvent.class, name = "PAYMENT_CREATED"),
    @JsonSubTypes.Type(value = PaymentProcessedEvent.class, name = "PAYMENT_PROCESSED"),
    @JsonSubTypes.Type(value = PaymentFailedEvent.class, name = "PAYMENT_FAILED"),
    @JsonSubTypes.Type(value = PaymentCancelledEvent.class, name = "PAYMENT_CANCELLED"),
    @JsonSubTypes.Type(value = PaymentRefundedEvent.class, name = "PAYMENT_REFUNDED"),
    @JsonSubTypes.Type(value = TransferInitiatedEvent.class, name = "TRANSFER_INITIATED"),
    @JsonSubTypes.Type(value = TransferCompletedEvent.class, name = "TRANSFER_COMPLETED"),
    @JsonSubTypes.Type(value = TransferFailedEvent.class, name = "TRANSFER_FAILED"),
    @JsonSubTypes.Type(value = WalletCreatedEvent.class, name = "WALLET_CREATED"),
    @JsonSubTypes.Type(value = WalletBalanceUpdatedEvent.class, name = "WALLET_BALANCE_UPDATED"),
    @JsonSubTypes.Type(value = WalletFrozenEvent.class, name = "WALLET_FROZEN"),
    @JsonSubTypes.Type(value = WalletUnfrozenEvent.class, name = "WALLET_UNFROZEN"),
    @JsonSubTypes.Type(value = FraudDetectedEvent.class, name = "FRAUD_DETECTED"),
    @JsonSubTypes.Type(value = ComplianceCheckEvent.class, name = "COMPLIANCE_CHECK")
})
@Data
@SuperBuilder
public abstract class FinancialEvent {
    
    protected String aggregateId;
    protected String aggregateType;
    protected String eventType;
    protected Long version;
    protected Instant timestamp;
    protected String userId;
    protected String correlationId;
    protected String causationId;
    protected Map<String, Object> metadata;
    
    public FinancialEvent() {
        this.timestamp = Instant.now();
    }
    
    /**
     * Get the unique identifier for this event type
     */
    public abstract String getEventType();
    
    /**
     * Get event-specific data for serialization
     */
    public abstract Map<String, Object> getEventData();
}