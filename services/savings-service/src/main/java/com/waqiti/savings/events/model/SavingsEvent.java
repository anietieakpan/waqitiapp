package com.waqiti.savings.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Base event model for savings domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavingsEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String accountId;
    private String userId;
    private String accountType;
    private String goalId;
    private String goalName;
    private String ruleId;
    private String cdId;
    private String cdType;
    private BigDecimal amount;
    private BigDecimal newBalance;
    private BigDecimal principal;
    private BigDecimal targetAmount;
    private BigDecimal maturityValue;
    private BigDecimal progressPercentage;
    private BigDecimal interestRate;
    private BigDecimal penaltyAmount;
    private String currency;
    private String depositMethod;
    private String withdrawalMethod;
    private String transactionId;
    private String term;
    private String maturityDate;
    private String targetDate;
    private String automationType;
    private String triggerCondition;
    private String sourceAccount;
    private String frequency;
    private String bonusType;
    private String bonusReason;
    private String campaignId;
    private String compoundingFrequency;
    private Instant calculationPeriodStart;
    private Instant calculationPeriodEnd;
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
     * Check if this is a financial transaction event
     */
    public boolean isFinancialEvent() {
        return "SAVINGS_DEPOSIT".equals(eventType) || 
               "SAVINGS_WITHDRAWAL".equals(eventType) ||
               "INTEREST_CALCULATED".equals(eventType) ||
               "BONUS_ISSUED".equals(eventType);
    }
    
    /**
     * Check if this is a goal-related event
     */
    public boolean isGoalEvent() {
        return "GOAL_PROGRESS_UPDATED".equals(eventType);
    }
    
    /**
     * Check if this is an automation event
     */
    public boolean isAutomationEvent() {
        return "AUTOMATION_TRIGGERED".equals(eventType);
    }
    
    /**
     * Check if this is a certificate deposit event
     */
    public boolean isCDEvent() {
        return "CD_CREATED".equals(eventType);
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