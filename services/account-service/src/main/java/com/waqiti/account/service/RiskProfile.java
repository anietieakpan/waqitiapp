package com.waqiti.account.service;

import java.util.UUID;

@lombok.Data
@lombok.Builder
public class RiskProfile {
    private final UUID userId;
    private final int score;
    private final long lastUpdated;
    
    private static final long CACHE_TTL_MS = 3600000; // 1 hour
    
    public boolean isExpired() {
        return System.currentTimeMillis() - lastUpdated > CACHE_TTL_MS;
    }
    
    public int getScore() {
        return score;
    }
}
