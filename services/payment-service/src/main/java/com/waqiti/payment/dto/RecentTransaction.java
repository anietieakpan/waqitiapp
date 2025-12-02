package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * DTO for recent transaction queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecentTransaction {
    
    private String transactionId;
    private String customerId;
    private String merchantId;
    private String merchantName;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String type;
    private String description;
    private String category;
    private String paymentMethod;
    private String referenceNumber;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant completedAt;
    
    // Location information
    private String country;
    private String city;
    private String merchantCategory;
    
    // Risk information
    private BigDecimal riskScore;
    private String riskLevel;
    private Boolean flagged;
    
    // Additional fields for analytics
    private String channel;
    private String deviceType;
    private String ipAddress;
}