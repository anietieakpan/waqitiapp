package com.waqiti.common.eventsourcing;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Concrete implementation of financial transaction aggregate
 */
@Data
public class FinancialTransactionAggregate implements FinancialAggregate {
    
    private String aggregateId;
    private Long version;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String payerId;
    private String payeeId;
    private Instant createdAt;
    private Instant updatedAt;
    private List<FinancialEvent> uncommittedEvents;
    
    public FinancialTransactionAggregate(String aggregateId) {
        this.aggregateId = aggregateId;
        this.version = 0L;
        this.uncommittedEvents = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    @Override
    public String getAggregateType() {
        return "FINANCIAL_TRANSACTION";
    }
    
    @Override
    public FinancialAggregate applyEvent(FinancialEvent event) {
        switch (event.getEventType()) {
            case "PAYMENT_CREATED":
                return applyPaymentCreated((PaymentCreatedEvent) event);
            case "PAYMENT_PROCESSED":
                return applyPaymentProcessed((PaymentProcessedEvent) event);
            case "PAYMENT_FAILED":
                return applyPaymentFailed((PaymentFailedEvent) event);
            case "PAYMENT_CANCELLED":
                return applyPaymentCancelled((PaymentCancelledEvent) event);
            case "TRANSFER_INITIATED":
                return applyTransferInitiated((TransferInitiatedEvent) event);
            case "TRANSFER_COMPLETED":
                return applyTransferCompleted((TransferCompletedEvent) event);
            case "WALLET_BALANCE_UPDATED":
                return applyWalletBalanceUpdated((WalletBalanceUpdatedEvent) event);
            case "FRAUD_DETECTED":
                return applyFraudDetected((FraudDetectedEvent) event);
            default:
                throw new IllegalArgumentException("Unknown event type: " + event.getEventType());
        }
    }
    
    @Override
    public void markEventsAsCommitted() {
        this.uncommittedEvents.clear();
    }
    
    // Event application methods
    
    private FinancialTransactionAggregate applyPaymentCreated(PaymentCreatedEvent event) {
        FinancialTransactionAggregate newState = copy();
        newState.status = "CREATED";
        newState.amount = event.getAmount();
        newState.currency = event.getCurrency();
        newState.payerId = event.getPayerId();
        newState.payeeId = event.getPayeeId();
        newState.version = event.getVersion();
        newState.updatedAt = event.getTimestamp();
        return newState;
    }
    
    private FinancialTransactionAggregate applyPaymentProcessed(PaymentProcessedEvent event) {
        FinancialTransactionAggregate newState = copy();
        newState.status = "PROCESSED";
        newState.amount = event.getProcessedAmount();
        newState.version = event.getVersion();
        newState.updatedAt = event.getTimestamp();
        return newState;
    }
    
    private FinancialTransactionAggregate applyPaymentFailed(PaymentFailedEvent event) {
        FinancialTransactionAggregate newState = copy();
        newState.status = "FAILED";
        newState.version = event.getVersion();
        newState.updatedAt = event.getTimestamp();
        return newState;
    }
    
    private FinancialTransactionAggregate applyPaymentCancelled(PaymentCancelledEvent event) {
        FinancialTransactionAggregate newState = copy();
        newState.status = "CANCELLED";
        newState.version = event.getVersion();
        newState.updatedAt = event.getTimestamp();
        return newState;
    }
    
    private FinancialTransactionAggregate applyTransferInitiated(TransferInitiatedEvent event) {
        FinancialTransactionAggregate newState = copy();
        newState.status = "TRANSFER_INITIATED";
        newState.amount = event.getAmount();
        newState.currency = event.getCurrency();
        newState.version = event.getVersion();
        newState.updatedAt = event.getTimestamp();
        return newState;
    }
    
    private FinancialTransactionAggregate applyTransferCompleted(TransferCompletedEvent event) {
        FinancialTransactionAggregate newState = copy();
        newState.status = "TRANSFER_COMPLETED";
        newState.amount = event.getFinalAmount();
        newState.version = event.getVersion();
        newState.updatedAt = event.getTimestamp();
        return newState;
    }
    
    private FinancialTransactionAggregate applyWalletBalanceUpdated(WalletBalanceUpdatedEvent event) {
        FinancialTransactionAggregate newState = copy();
        newState.status = "BALANCE_UPDATED";
        newState.version = event.getVersion();
        newState.updatedAt = event.getTimestamp();
        return newState;
    }
    
    private FinancialTransactionAggregate applyFraudDetected(FraudDetectedEvent event) {
        FinancialTransactionAggregate newState = copy();
        newState.status = "FRAUD_DETECTED";
        newState.version = event.getVersion();
        newState.updatedAt = event.getTimestamp();
        return newState;
    }
    
    // Helper method to create a deep copy
    private FinancialTransactionAggregate copy() {
        FinancialTransactionAggregate copy = new FinancialTransactionAggregate(this.aggregateId);
        copy.version = this.version;
        copy.status = this.status;
        copy.amount = this.amount;
        copy.currency = this.currency;
        copy.payerId = this.payerId;
        copy.payeeId = this.payeeId;
        copy.createdAt = this.createdAt;
        copy.updatedAt = this.updatedAt;
        copy.uncommittedEvents = new ArrayList<>(this.uncommittedEvents);
        return copy;
    }
}