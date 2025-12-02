package com.waqiti.rewards.domain;

public enum RewardTier {
    BRONZE(1),
    SILVER(2),
    GOLD(3),
    PLATINUM(4),
    DIAMOND(5);
    
    private final int level;
    
    RewardTier(int level) {
        this.level = level;
    }
    
    public int getLevel() {
        return level;
    }
}
