package com.waqiti.common.eventsourcing;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Event fired when a payment is created
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PaymentCreatedEvent extends FinancialEvent {
    
    private String paymentId;
    private String payerId;
    private String payeeId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String description;
    private Map<String, Object> paymentDetails;
    
    @Override
    public String getEventType() {
        return "PAYMENT_CREATED";
    }
    
    @Override
    public Map<String, Object> getEventData() {
        return Map.of(
            "paymentId", paymentId,
            "payerId", payerId,
            "payeeId", payeeId,
            "amount", amount,
            "currency", currency,
            "paymentMethod", paymentMethod,
            "description", description != null ? description : "",
            "paymentDetails", paymentDetails != null ? paymentDetails : Map.of()
        );
    }
}