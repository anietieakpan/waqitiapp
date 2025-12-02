package com.waqiti.merchant.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Base event model for merchant domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String merchantId;
    private String businessName;
    private String businessType;
    private String ownerId;
    private String customerId;
    private String paymentId;
    private String settlementId;
    private String chargebackId;
    private String refundId;
    private String reportId;
    private String checkId;
    private String tier;
    private String category;
    private String registrationNumber;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String status;
    private BigDecimal feeAmount;
    private String feeType;
    private BigDecimal feeRate;
    private Instant settlementPeriodStart;
    private Instant settlementPeriodEnd;
    private int transactionCount;
    private String reasonCode;
    private String reason;
    private String refundMethod;
    private String reportType;
    private String reportFormat;
    private Instant reportPeriodStart;
    private Instant reportPeriodEnd;
    private String checkType;
    private String checkResult;
    private String complianceFramework;
    private String riskLevel;
    private String recommendedAction;
    private Instant timestamp;
    private String correlationId;
    private String causationId;
    private String version;
    private String description;
    private Long sequenceNumber;
    private Integer retryCount;
    private Map<String, Object> metadata;
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
    
    /**
     * Check if this is a financial event
     */
    public boolean isFinancialEvent() {
        return "MERCHANT_PAYMENT_PROCESSED".equals(eventType) || 
               "MERCHANT_SETTLEMENT".equals(eventType) ||
               "MERCHANT_REFUND".equals(eventType) ||
               "MERCHANT_FEE_CALCULATED".equals(eventType);
    }
    
    /**
     * Check if this is a high-priority event
     */
    public boolean isHighPriorityEvent() {
        return "MERCHANT_CHARGEBACK".equals(eventType) || 
               "MERCHANT_COMPLIANCE_CHECK".equals(eventType);
    }
    
    /**
     * Check if this is a dispute-related event
     */
    public boolean isDisputeEvent() {
        return "MERCHANT_CHARGEBACK".equals(eventType) ||
               "MERCHANT_REFUND".equals(eventType);
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        if (timestamp == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
}