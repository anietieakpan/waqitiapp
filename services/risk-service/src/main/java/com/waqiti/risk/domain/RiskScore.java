package com.waqiti.risk.domain;

public enum RiskScore {
    LOW(0, 30),
    MEDIUM(31, 70),
    HIGH(71, 100);
    
    private final int min;
    private final int max;
    
    RiskScore(int min, int max) {
        this.min = min;
        this.max = max;
    }
}
