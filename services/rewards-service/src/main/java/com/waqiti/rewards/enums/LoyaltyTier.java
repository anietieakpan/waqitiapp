package com.waqiti.rewards.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public enum LoyaltyTier {
    BRONZE("Bronze", BigDecimal.valueOf(1.0), BigDecimal.valueOf(1000), 1),
    SILVER("Silver", BigDecimal.valueOf(1.25), BigDecimal.valueOf(5000), 2),
    GOLD("Gold", BigDecimal.valueOf(1.5), BigDecimal.valueOf(15000), 3),
    PLATINUM("Platinum", BigDecimal.valueOf(2.0), BigDecimal.valueOf(50000), 4);
    
    private final String displayName;
    private final BigDecimal multiplier;
    private final BigDecimal targetAmount;
    private final int level;
    
    public LoyaltyTier getNextTier() {
        switch (this) {
            case BRONZE: return SILVER;
            case SILVER: return GOLD;
            case GOLD: return PLATINUM;
            case PLATINUM: return PLATINUM; // Max tier, no further advancement
            default: return BRONZE; // Default to starting tier
        }
    }
    
    public boolean isMaxTier() {
        return this == PLATINUM;
    }
}