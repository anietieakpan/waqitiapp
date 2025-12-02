package com.waqiti.rewards.dto;

import com.waqiti.rewards.enums.LoyaltyTier;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for loyalty tier information
 */
@Data
@Builder
public class LoyaltyTierDto {
    
    /**
     * Tier enum
     */
    private LoyaltyTier tier;
    
    /**
     * Tier name
     */
    private String name;
    
    /**
     * Spending requirement to reach this tier
     */
    private BigDecimal spendingRequirement;
    
    /**
     * Cashback rate for this tier
     */
    private BigDecimal cashbackRate;
    
    /**
     * Points multiplier for this tier
     */
    private BigDecimal pointsMultiplier;
    
    /**
     * List of benefits for this tier
     */
    private List<String> benefits;
    
    /**
     * Tier level (0 = Bronze, 1 = Silver, etc.)
     */
    private Integer level;
    
    /**
     * Tier color for UI display
     */
    private String color;
    
    /**
     * Tier icon URL
     */
    private String iconUrl;
    
    /**
     * Tier description
     */
    private String description;
    
    /**
     * Annual fee for this tier (if applicable)
     */
    private BigDecimal annualFee;
    
    /**
     * Whether this tier is currently active
     */
    private Boolean isActive;
}