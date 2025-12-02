package com.waqiti.wallet.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Base event model for wallet domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String walletId;
    private String targetWalletId;
    private String userId;
    private String targetUserId;
    private String walletType;
    private BigDecimal amount;
    private BigDecimal previousAmount;
    private String currency;
    private String transactionId;
    private String sourceAccountId;
    private String destinationAccountId;
    private String transferType;
    private String withdrawalMethod;
    private String reason;
    private String freezeType;
    private String adminId;
    private String limitType;
    private String rewardType;
    private String campaignId;
    private String statementId;
    private Instant statementPeriodStart;
    private Instant statementPeriodEnd;
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
     * Check if this is a debit event
     */
    public boolean isDebitEvent() {
        return "WALLET_WITHDRAWAL".equals(eventType) || 
               "WALLET_TRANSFER".equals(eventType);
    }
    
    /**
     * Check if this is a credit event
     */
    public boolean isCreditEvent() {
        return "WALLET_TOP_UP".equals(eventType) || 
               "WALLET_REWARD_ISSUED".equals(eventType);
    }
    
    /**
     * Check if this is a high-priority event
     */
    public boolean isHighPriorityEvent() {
        return "WALLET_FROZEN".equals(eventType) || 
               "WALLET_UNFROZEN".equals(eventType);
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