package com.waqiti.layer2.model;

public enum Layer2Solution {
    OPTIMISTIC_ROLLUP("Optimistic Rollup", "Low cost, 1-hour finality"),
    ZK_ROLLUP("ZK Rollup", "Privacy-focused, 30-minute finality"),
    STATE_CHANNEL("State Channel", "Instant finality, off-chain"),
    PLASMA("Plasma", "High throughput, 10-minute finality");
    
    private final String displayName;
    private final String description;
    
    Layer2Solution(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
}