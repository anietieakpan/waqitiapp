package com.waqiti.virtualcard.validator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of spending limit validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingLimitValidation {
    
    private boolean valid;
    private String reason;
    private LimitType limitType;
    private BigDecimal requestedAmount;
    private BigDecimal availableAmount;
    private BigDecimal currentSpent;
    private BigDecimal limitAmount;
    private LocalDateTime resetTime;
    private List<LimitDetail> limitDetails;
    private LocalDateTime validatedAt;
    private String validatedBy;
    
    public enum LimitType {
        TRANSACTION_LIMIT,
        DAILY_LIMIT,
        WEEKLY_LIMIT,
        MONTHLY_LIMIT,
        YEARLY_LIMIT,
        ATM_WITHDRAWAL_LIMIT,
        ONLINE_LIMIT,
        INTERNATIONAL_LIMIT,
        CONTACTLESS_LIMIT,
        MERCHANT_LIMIT,
        CATEGORY_LIMIT,
        VELOCITY_LIMIT,
        NO_LIMIT
    }
    
    /**
     * Create successful validation
     */
    public static SpendingLimitValidation success() {
        return SpendingLimitValidation.builder()
            .valid(true)
            .limitType(LimitType.NO_LIMIT)
            .reason("All spending limits passed")
            .validatedAt(LocalDateTime.now())
            .limitDetails(new ArrayList<>())
            .build();
    }
    
    /**
     * Create successful validation with details
     */
    public static SpendingLimitValidation successWithDetails(BigDecimal requestedAmount, 
                                                            BigDecimal availableAmount) {
        return SpendingLimitValidation.builder()
            .valid(true)
            .limitType(LimitType.NO_LIMIT)
            .reason("Transaction within limits")
            .requestedAmount(requestedAmount)
            .availableAmount(availableAmount)
            .validatedAt(LocalDateTime.now())
            .limitDetails(new ArrayList<>())
            .build();
    }
    
    /**
     * Create failed validation
     */
    public static SpendingLimitValidation failure(LimitType limitType, String reason,
                                                 BigDecimal requestedAmount,
                                                 BigDecimal availableAmount) {
        return SpendingLimitValidation.builder()
            .valid(false)
            .limitType(limitType)
            .reason(reason)
            .requestedAmount(requestedAmount)
            .availableAmount(availableAmount)
            .validatedAt(LocalDateTime.now())
            .limitDetails(new ArrayList<>())
            .build();
    }
    
    /**
     * Create limit exceeded validation
     */
    public static SpendingLimitValidation limitExceeded(LimitType limitType,
                                                       BigDecimal requestedAmount,
                                                       BigDecimal limitAmount,
                                                       BigDecimal currentSpent,
                                                       LocalDateTime resetTime) {
        BigDecimal availableAmount = limitAmount.subtract(currentSpent).max(BigDecimal.ZERO);
        
        String reason = String.format("%s exceeded. Requested: %s, Available: %s, Limit: %s",
            limitType.name().replace("_", " ").toLowerCase(),
            requestedAmount.toPlainString(),
            availableAmount.toPlainString(),
            limitAmount.toPlainString()
        );
        
        return SpendingLimitValidation.builder()
            .valid(false)
            .limitType(limitType)
            .reason(reason)
            .requestedAmount(requestedAmount)
            .availableAmount(availableAmount)
            .currentSpent(currentSpent)
            .limitAmount(limitAmount)
            .resetTime(resetTime)
            .validatedAt(LocalDateTime.now())
            .limitDetails(new ArrayList<>())
            .build();
    }
    
    /**
     * Add limit detail
     */
    public void addLimitDetail(LimitDetail detail) {
        if (limitDetails == null) {
            limitDetails = new ArrayList<>();
        }
        limitDetails.add(detail);
    }
    
    /**
     * Add limit detail
     */
    public void addLimitDetail(String limitName, BigDecimal limit, BigDecimal spent, BigDecimal available) {
        addLimitDetail(new LimitDetail(limitName, limit, spent, available));
    }
    
    /**
     * Check if limit will reset soon
     */
    public boolean willResetSoon(int hoursThreshold) {
        if (resetTime == null) {
            return false;
        }
        return resetTime.isBefore(LocalDateTime.now().plusHours(hoursThreshold));
    }
    
    /**
     * Get percentage used
     */
    public double getPercentageUsed() {
        if (limitAmount == null || limitAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        if (currentSpent == null) {
            return 0.0;
        }
        return currentSpent.divide(limitAmount, 4, java.math.RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .doubleValue();
    }
    
    /**
     * Check if near limit
     */
    public boolean isNearLimit(double thresholdPercentage) {
        return getPercentageUsed() >= thresholdPercentage;
    }
    
    /**
     * Check if this is a hard limit
     */
    public boolean isHardLimit() {
        return limitType == LimitType.TRANSACTION_LIMIT ||
               limitType == LimitType.ATM_WITHDRAWAL_LIMIT ||
               limitType == LimitType.VELOCITY_LIMIT;
    }
    
    /**
     * Check if this is a rolling limit
     */
    public boolean isRollingLimit() {
        return limitType == LimitType.DAILY_LIMIT ||
               limitType == LimitType.WEEKLY_LIMIT ||
               limitType == LimitType.MONTHLY_LIMIT ||
               limitType == LimitType.YEARLY_LIMIT;
    }
    
    /**
     * Get suggested action
     */
    public String getSuggestedAction() {
        if (valid) {
            return "Transaction can proceed";
        }
        
        if (availableAmount != null && availableAmount.compareTo(BigDecimal.ZERO) > 0) {
            return String.format("Reduce transaction amount to %s or less", availableAmount.toPlainString());
        }
        
        if (resetTime != null && willResetSoon(1)) {
            return String.format("Wait until %s for limit reset", resetTime);
        }
        
        return "Contact support to increase limits or wait for limit reset";
    }
    
    /**
     * Limit detail
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LimitDetail {
        private String limitName;
        private BigDecimal limit;
        private BigDecimal spent;
        private BigDecimal available;
        private LocalDateTime resetTime;
        private boolean exceeded;
        
        public LimitDetail(String limitName, BigDecimal limit, BigDecimal spent, BigDecimal available) {
            this.limitName = limitName;
            this.limit = limit;
            this.spent = spent;
            this.available = available;
            this.exceeded = available.compareTo(BigDecimal.ZERO) <= 0;
        }
        
        public double getUsagePercentage() {
            if (limit == null || limit.compareTo(BigDecimal.ZERO) == 0) {
                return 0.0;
            }
            if (spent == null) {
                return 0.0;
            }
            return spent.divide(limit, 4, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .doubleValue();
        }
    }
}