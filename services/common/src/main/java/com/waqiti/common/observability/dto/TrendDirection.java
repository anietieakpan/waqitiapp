package com.waqiti.common.observability.dto;

/**
 * Enterprise trend direction enumeration for analytical reporting
 * Provides comprehensive trend analysis with statistical significance
 */
public enum TrendDirection {
    
    /**
     * Strong upward trend - significant increase over time
     */
    STRONGLY_INCREASING("Strongly Increasing", "↗↗", "#dc3545", 1.0, "Immediate attention required"),
    
    /**
     * Moderate upward trend - noticeable increase
     */
    INCREASING("Increasing", "↗", "#fd7e14", 0.7, "Monitor closely"),
    
    /**
     * Slight upward trend - minor increase
     */
    SLIGHTLY_INCREASING("Slightly Increasing", "↗", "#ffc107", 0.3, "Normal fluctuation"),
    
    /**
     * Stable trend - no significant change
     */
    STABLE("Stable", "→", "#28a745", 0.0, "Maintain current approach"),
    
    /**
     * Slight downward trend - minor decrease
     */
    SLIGHTLY_DECREASING("Slightly Decreasing", "↘", "#17a2b8", -0.3, "Positive indicator"),
    
    /**
     * Moderate downward trend - noticeable decrease
     */
    DECREASING("Decreasing", "↘", "#6f42c1", -0.7, "Improvement noted"),
    
    /**
     * Strong downward trend - significant decrease
     */
    STRONGLY_DECREASING("Strongly Decreasing", "↘↘", "#20c997", -1.0, "Excellent progress"),
    
    /**
     * Volatile trend - frequent ups and downs
     */
    VOLATILE("Volatile", "↕", "#e83e8c", 0.0, "Requires analysis"),
    
    /**
     * Insufficient data for trend analysis
     */
    INSUFFICIENT_DATA("Insufficient Data", "?", "#6c757d", 0.0, "Collect more data"),
    
    /**
     * Seasonal pattern detected
     */
    SEASONAL("Seasonal", "~", "#fd7e14", 0.0, "Expected variation");
    
    private final String displayName;
    private final String icon;
    private final String colorCode;
    private final double severity;
    private final String recommendation;
    
    TrendDirection(String displayName, String icon, String colorCode, double severity, String recommendation) {
        this.displayName = displayName;
        this.icon = icon;
        this.colorCode = colorCode;
        this.severity = severity;
        this.recommendation = recommendation;
    }
    
    /**
     * Get human-readable trend name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get visual icon for trend
     */
    public String getIcon() {
        return icon;
    }
    
    /**
     * Get color code for UI display
     */
    public String getColorCode() {
        return colorCode;
    }
    
    /**
     * Get severity score (-1.0 to 1.0, negative is better for error trends)
     */
    public double getSeverity() {
        return severity;
    }
    
    /**
     * Get recommendation based on trend
     */
    public String getRecommendation() {
        return recommendation;
    }
    
    /**
     * Check if trend indicates deterioration
     */
    public boolean isDeteriorating() {
        return this == STRONGLY_INCREASING || this == INCREASING || this == VOLATILE;
    }
    
    /**
     * Check if trend indicates improvement
     */
    public boolean isImproving() {
        return this == STRONGLY_DECREASING || this == DECREASING;
    }
    
    /**
     * Check if trend is stable or acceptable
     */
    public boolean isStable() {
        return this == STABLE || this == SLIGHTLY_INCREASING || this == SLIGHTLY_DECREASING;
    }
    
    /**
     * Check if trend requires immediate attention
     */
    public boolean requiresAttention() {
        return Math.abs(severity) >= 0.7;
    }
    
    /**
     * Determine trend from slope value
     */
    public static TrendDirection fromSlope(double slope, double variance) {
        // High variance indicates volatility
        if (variance > 0.5) {
            return VOLATILE;
        }
        
        // Analyze slope
        if (slope > 0.8) {
            return STRONGLY_INCREASING;
        } else if (slope > 0.3) {
            return INCREASING;
        } else if (slope > 0.1) {
            return SLIGHTLY_INCREASING;
        } else if (slope > -0.1) {
            return STABLE;
        } else if (slope > -0.3) {
            return SLIGHTLY_DECREASING;
        } else if (slope > -0.8) {
            return DECREASING;
        } else {
            return STRONGLY_DECREASING;
        }
    }
    
    /**
     * Determine trend from percentage change
     */
    public static TrendDirection fromPercentageChange(double percentChange) {
        if (percentChange > 50) {
            return STRONGLY_INCREASING;
        } else if (percentChange > 20) {
            return INCREASING;
        } else if (percentChange > 5) {
            return SLIGHTLY_INCREASING;
        } else if (percentChange > -5) {
            return STABLE;
        } else if (percentChange > -20) {
            return SLIGHTLY_DECREASING;
        } else if (percentChange > -50) {
            return DECREASING;
        } else {
            return STRONGLY_DECREASING;
        }
    }
    
    /**
     * Get priority level for alerting
     */
    public int getAlertPriority() {
        switch (this) {
            case STRONGLY_INCREASING:
            case VOLATILE:
                return 1; // Critical
            case INCREASING:
                return 2; // High
            case SLIGHTLY_INCREASING:
                return 3; // Medium
            case STABLE:
            case SLIGHTLY_DECREASING:
                return 4; // Low
            case DECREASING:
            case STRONGLY_DECREASING:
                return 5; // Info
            default:
                return 3; // Medium
        }
    }
}