package com.waqiti.analytics.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Fraud event for analytics processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudEvent {
    
    private String eventId;
    private String transactionId;
    private String customerId;
    private String merchantId;
    
    // Transaction details
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String status;
    
    // Context information
    private String deviceId;
    private String ipAddress;
    private String location;
    private String userAgent;
    private Instant timestamp;
    
    // Risk indicators
    private BigDecimal riskScore;
    private String riskLevel;
    
    // Additional metadata
    private Map<String, Object> metadata;
}