package com.waqiti.recurringpayment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Fraud Check Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckRequest {
    private String userId;
    private String recipientId;
    private BigDecimal amount;
    private String currency;
    private String transactionType;
    private String paymentMethod;
    private String ipAddress;
    private String deviceId;
    private Map<String, Object> additionalData;
}