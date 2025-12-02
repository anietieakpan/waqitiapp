package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Event fired when a payment is successfully processed
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PaymentProcessedEvent extends FinancialEvent {
    
    private String paymentId;
    private String transactionId;
    private BigDecimal processedAmount;
    private BigDecimal feeAmount;
    private String processorResponse;
    private Instant processedAt;
    
    @Override
    public String getEventType() {
        return "PAYMENT_PROCESSED";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "paymentId", paymentId,
            "transactionId", transactionId,
            "processedAmount", processedAmount,
            "feeAmount", feeAmount,
            "processorResponse", processorResponse != null ? processorResponse : "",
            "processedAt", processedAt
        );
    }
}