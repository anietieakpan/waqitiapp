package com.waqiti.rewards.dto;

import com.waqiti.rewards.enums.LoyaltyTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyTierInfo {
    private LoyaltyTier currentTier;
    private LoyaltyTier nextTier;
    private BigDecimal progress;
    private BigDecimal target;
    private BigDecimal remainingAmount;
    private int progressPercentage;
    private boolean isMaxTier;
    private List<String> currentBenefits;
    private List<String> nextTierBenefits;
    private BigDecimal currentMultiplier;
    private BigDecimal nextMultiplier;
    private BigDecimal currentCashbackRate;
    private BigDecimal nextCashbackRate;
    private Map<String, Object> perks;
}