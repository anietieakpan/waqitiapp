package com.waqiti.security.domain;

public enum SecuritySeverity {
    LOW(1),
    MEDIUM(2), 
    HIGH(3),
    CRITICAL(4);

    private final int level;

    SecuritySeverity(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }
}