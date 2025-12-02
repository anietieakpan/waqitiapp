package com.waqiti.virtualcard.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * DTO for merchant restrictions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantRestrictions {
    
    @Size(max = 100, message = "Too many allowed merchants")
    private List<String> allowedMerchants;
    
    @Size(max = 100, message = "Too many blocked merchants")
    private List<String> blockedMerchants;
    
    @Size(max = 100, message = "Too many allowed merchant IDs")
    private List<String> allowedMerchantIds;
    
    @Size(max = 100, message = "Too many blocked merchant IDs")
    private List<String> blockedMerchantIds;
    
    @Size(max = 50, message = "Too many allowed categories")
    private List<String> allowedCategories;
    
    @Size(max = 50, message = "Too many blocked categories")
    private List<String> blockedCategories;
    
    @Size(max = 100, message = "Too many allowed MCCs")
    private List<@Size(min = 4, max = 4, message = "MCC must be 4 characters") String> allowedMccs;
    
    @Size(max = 100, message = "Too many blocked MCCs")
    private List<@Size(min = 4, max = 4, message = "MCC must be 4 characters") String> blockedMccs;
    
    private Map<String, BigDecimal> merchantLimits;
    
    private Map<String, BigDecimal> categoryLimits;
    
    private boolean whitelistMode = false;
    
    private boolean blacklistMode = false;
    
    private boolean allowUnknownMerchants = true;
    
    private boolean requireApprovalForNewMerchants = false;
    
    private boolean autoApproveTrustedMerchants = true;
    
    private boolean blockHighRiskMerchants = true;
    
    private boolean blockGambling = false;
    
    private boolean blockAdultContent = false;
    
    private boolean blockAlcohol = false;
    
    private boolean blockTobacco = false;
    
    private boolean blockCryptocurrency = false;
    
    private boolean blockCashAdvance = false;
    
    private boolean blockMoneyTransfer = false;
    
    private boolean blockSubscriptionServices = false;
}