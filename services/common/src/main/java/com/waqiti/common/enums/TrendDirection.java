package com.waqiti.common.enums;

/**
 * Common trend direction enumeration used across observability and analytics
 * Represents standardized trend analysis directions
 */
public enum TrendDirection {
    INCREASING("Increasing", "📈"),
    DECREASING("Decreasing", "📉"),
    STABLE("Stable", "➡️"),
    VOLATILE("Volatile", "📊"),
    WORSENING("Worsening", "⚠️"),
    IMPROVING("Improving", "✅"),
    DECLINING("Declining", "📉"),
    UNKNOWN("Unknown", "❓");
    
    private final String displayName;
    private final String emoji;
    
    TrendDirection(String displayName, String emoji) {
        this.displayName = displayName;
        this.emoji = emoji;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getEmoji() {
        return emoji;
    }
    
    /**
     * Check if trend direction indicates a negative pattern
     */
    public boolean isNegative() {
        return this == DECREASING || this == WORSENING || this == VOLATILE;
    }
    
    /**
     * Check if trend direction indicates a positive pattern
     */
    public boolean isPositive() {
        return this == INCREASING || this == IMPROVING;
    }
}