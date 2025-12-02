package com.waqiti.card.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Card domain event model for card issuance, PIN management, limit adjustments,
 * replacement, rewards, statements, balance transfers, cash advances, and 3D Secure
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String cardId;
    private String cardNumber; // masked
    private String userId;
    private String accountId;
    private String cardType;
    private String cardStatus;
    private BigDecimal amount;
    private BigDecimal previousAmount;
    private String currency;
    private BigDecimal creditLimit;
    private BigDecimal availableCredit;
    private String pinStatus;
    private String replacementReason;
    private String oldCardId;
    private String newCardId;
    private String rewardType;
    private String rewardProgram;
    private BigDecimal rewardPoints;
    private String statementId;
    private Instant statementPeriodStart;
    private Instant statementPeriodEnd;
    private BigDecimal transferAmount;
    private String transferType;
    private String sourceCardId;
    private String targetCardId;
    private BigDecimal cashAdvanceAmount;
    private BigDecimal cashAdvanceFee;
    private String atmId;
    private String threeDSVersion;
    private String authenticationStatus;
    private String transactionId;
    private String merchantId;
    private String merchantName;
    private String processorResponse;
    private String deliveryMethod;
    private Instant expiryDate;
    private Instant activationDate;
    private String activationChannel;
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
     * Check if this is a card issuance event
     */
    public boolean isCardIssuanceEvent() {
        return "CARD_ISSUANCE".equals(eventType);
    }
    
    /**
     * Check if this is a high-value transaction event
     */
    public boolean isHighValueEvent() {
        return amount != null && amount.compareTo(new BigDecimal("10000")) > 0;
    }
    
    /**
     * Check if this is a high-priority event
     */
    public boolean isHighPriorityEvent() {
        return "CARD_3DS".equals(eventType) || 
               "CARD_REPLACEMENT".equals(eventType) ||
               isHighValueEvent();
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