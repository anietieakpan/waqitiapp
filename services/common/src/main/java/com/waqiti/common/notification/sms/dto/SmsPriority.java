package com.waqiti.common.notification.sms.dto;

/**
 * Priority levels for SMS messages
 */
public enum SmsPriority {
    LOW(1),
    NORMAL(2), 
    HIGH(3),
    URGENT(4),
    CRITICAL(5);
    
    private final int level;
    
    SmsPriority(int level) {
        this.level = level;
    }
    
    public int getLevel() {
        return level;
    }
    
    public boolean isHigherThan(SmsPriority other) {
        return this.level > other.level;
    }
}