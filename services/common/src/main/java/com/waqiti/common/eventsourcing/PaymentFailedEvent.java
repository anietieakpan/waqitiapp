package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Event fired when a payment fails
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PaymentFailedEvent extends FinancialEvent {
    
    private String paymentId;
    private String failureReason;
    private String errorCode;
    private String processorResponse;
    private boolean retryable;
    
    @Override
    public String getEventType() {
        return "PAYMENT_FAILED";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "paymentId", paymentId,
            "failureReason", failureReason,
            "errorCode", errorCode != null ? errorCode : "",
            "processorResponse", processorResponse != null ? processorResponse : "",
            "retryable", retryable
        );
    }
}