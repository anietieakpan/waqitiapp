package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Payment event for analytics processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {
    
    private String transactionId;
    private String customerId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String status;
    private Instant timestamp;
    private String location;
    private String deviceId;
    private String ipAddress;
    private Map<String, Object> metadata;
}