package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Event fired when a payment is refunded
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PaymentRefundedEvent extends FinancialEvent {
    
    private String paymentId;
    private String refundId;
    private BigDecimal refundAmount;
    private String refundReason;
    private boolean partialRefund;
    
    @Override
    public String getEventType() {
        return "PAYMENT_REFUNDED";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "paymentId", paymentId,
            "refundId", refundId,
            "refundAmount", refundAmount,
            "refundReason", refundReason,
            "partialRefund", partialRefund
        );
    }
}