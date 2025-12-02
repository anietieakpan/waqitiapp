package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Transaction event model for analytics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    
    private String transactionId;
    private String customerId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String paymentMethod;
    private String location;
    private Instant timestamp;
    private Map<String, Object> metadata;
}