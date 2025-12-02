package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Event fired when a payment is cancelled
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PaymentCancelledEvent extends FinancialEvent {
    
    private String paymentId;
    private String cancellationReason;
    private String cancelledBy;
    private boolean refundIssued;
    
    @Override
    public String getEventType() {
        return "PAYMENT_CANCELLED";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "paymentId", paymentId,
            "cancellationReason", cancellationReason,
            "cancelledBy", cancelledBy,
            "refundIssued", refundIssued
        );
    }
}