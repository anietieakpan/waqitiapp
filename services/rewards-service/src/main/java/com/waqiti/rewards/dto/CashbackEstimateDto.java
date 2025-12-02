package com.waqiti.rewards.dto;

import com.waqiti.rewards.enums.LoyaltyTier;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO for cashback estimation response
 */
@Data
@Builder
public class CashbackEstimateDto {
    
    /**
     * Original transaction amount
     */
    private BigDecimal transactionAmount;
    
    /**
     * Estimated cashback amount
     */
    private BigDecimal estimatedCashback;
    
    /**
     * Effective cashback rate used
     */
    private BigDecimal cashbackRate;
    
    /**
     * Estimated points to be earned
     */
    private Long estimatedPoints;
    
    /**
     * User's current tier
     */
    private LoyaltyTier currentTier;
    
    /**
     * Tier multiplier applied
     */
    private BigDecimal tierMultiplier;
    
    /**
     * Base cashback rate before multipliers
     */
    private BigDecimal baseCashbackRate;
    
    /**
     * Merchant-specific rate (if applicable)
     */
    private BigDecimal merchantRate;
    
    /**
     * Category-specific rate (if applicable)  
     */
    private BigDecimal categoryRate;
    
    /**
     * Campaign bonus (if applicable)
     */
    private BigDecimal campaignBonus;
    
    /**
     * Whether daily limit would be exceeded
     */
    private Boolean exceedsDailyLimit;
    
    /**
     * Remaining daily cashback allowance
     */
    private BigDecimal remainingDailyLimit;
    
    /**
     * Explanation of how the cashback was calculated
     */
    private String explanation;
    
    /**
     * Currency of the amounts
     */
    private String currency;
}