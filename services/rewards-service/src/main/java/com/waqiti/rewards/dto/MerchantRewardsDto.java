package com.waqiti.rewards.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for merchant rewards information
 */
@Data
@Builder
public class MerchantRewardsDto {
    
    /**
     * Merchant ID  
     */
    private String merchantId;
    
    /**
     * Merchant name
     */
    private String merchantName;
    
    /**
     * Merchant category
     */
    private String category;
    
    /**
     * Cashback rate for this merchant
     */
    private BigDecimal cashbackRate;
    
    /**
     * Points multiplier for this merchant
     */
    private BigDecimal pointsMultiplier;
    
    /**
     * Merchant logo URL
     */
    private String logoUrl;
    
    /**
     * Merchant description
     */
    private String description;
    
    /**
     * Whether the merchant offer is currently active
     */
    private Boolean isActive;
    
    /**
     * Special offer text (if any)
     */
    private String offerText;
    
    /**
     * Offer valid from
     */
    private Instant validFrom;
    
    /**
     * Offer valid until
     */
    private Instant validUntil;
    
    /**
     * Maximum cashback per transaction
     */
    private BigDecimal maxCashbackPerTransaction;
    
    /**
     * Maximum cashback per month
     */
    private BigDecimal maxCashbackPerMonth;
    
    /**
     * Terms and conditions
     */
    private String termsAndConditions;
    
    /**
     * Is this a featured merchant
     */
    private Boolean isFeatured;
    
    /**
     * Merchant website URL
     */
    private String websiteUrl;
}