package com.waqiti.atm.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Base event model for ATM domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ATMEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String atmId;
    private String atmLocation;
    private String atmNetwork;
    private String userId;
    private String accountId;
    private String cardNumber;
    private String cardType;
    private BigDecimal amount;
    private String currency;
    private String transactionId;
    private String transactionType;
    private String authorizationCode;
    private String responseCode;
    private String responseMessage;
    private String terminalId;
    private String merchantId;
    private String acquirerId;
    private String issuerName;
    private String processorName;
    private BigDecimal fee;
    private BigDecimal cashbackAmount;
    private BigDecimal availableBalance;
    private BigDecimal accountBalance;
    private String pinVerificationStatus;
    private String chipAuthenticationStatus;
    private String contactlessIndicator;
    private String receiptNumber;
    private String surchargeAmount;
    private String networkFee;
    private String interchangeFee;
    private Boolean isInternational;
    private Boolean isSurcharge;
    private Boolean isBalanceInquiry;
    private Boolean isCashWithdrawal;
    private Boolean isDeposit;
    private Boolean isForeignCard;
    private String fraudScore;
    private String riskAssessment;
    private Instant timestamp;
    private String correlationId;
    private String causationId;
    private String version;
    private String status;
    private String description;
    private Long sequenceNumber;
    private Integer retryCount;
    private Map<String, Object> metadata;
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
    
    /**
     * Check if this is a cash withdrawal event
     */
    public boolean isCashWithdrawalEvent() {
        return "ATM_WITHDRAWAL".equals(eventType) || 
               Boolean.TRUE.equals(isCashWithdrawal);
    }
    
    /**
     * Check if this is a deposit event
     */
    public boolean isDepositEvent() {
        return "ATM_DEPOSIT".equals(eventType) || 
               Boolean.TRUE.equals(isDeposit);
    }
    
    /**
     * Check if this is a high-priority event
     */
    public boolean isHighPriorityEvent() {
        return "ATM_FRAUD_DETECTED".equals(eventType) || 
               "ATM_CARD_CAPTURED".equals(eventType) ||
               "ATM_UNAUTHORIZED_ACCESS".equals(eventType);
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
    
    /**
     * Check if transaction is suspicious
     */
    public boolean isSuspiciousTransaction() {
        return fraudScore != null && Double.parseDouble(fraudScore) > 0.8;
    }
}