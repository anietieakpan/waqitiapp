package com.waqiti.common.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Transaction audit request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAuditRequest {
    
    /**
     * Transaction ID
     */
    private String transactionId;
    
    /**
     * Transaction type
     */
    private String transactionType;
    
    /**
     * Source account
     */
    private String sourceAccount;
    
    /**
     * Destination account
     */
    private String destinationAccount;
    
    /**
     * Transaction amount
     */
    private BigDecimal amount;
    
    /**
     * Currency
     */
    private String currency;
    
    /**
     * Transaction status
     */
    private String status;
    
    /**
     * User ID who initiated
     */
    private String userId;
    
    /**
     * Transaction timestamp
     */
    private Instant timestamp;
    
    /**
     * IP address
     */
    private String ipAddress;
    
    /**
     * User agent
     */
    private String userAgent;
    
    /**
     * Session ID
     */
    private String sessionId;
    
    /**
     * Device info
     */
    private Map<String, String> deviceInfo;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Risk score
     */
    private Double riskScore;
    
    /**
     * Fraud indicators
     */
    private Map<String, Boolean> fraudIndicators;
}