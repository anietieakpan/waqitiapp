package com.waqiti.recurringpayment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Payment Request DTO for Payment Service integration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    private String senderId;
    private String recipientId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String paymentMethod;
    private String paymentMethodId;
    private String idempotencyKey;
    private Map<String, String> metadata;
    private boolean skipNotification;
    private PaymentPriority priority;
    private String recurringPaymentId;
    private String executionId;
    
    public enum PaymentPriority {
        LOW, NORMAL, HIGH, URGENT
    }
}