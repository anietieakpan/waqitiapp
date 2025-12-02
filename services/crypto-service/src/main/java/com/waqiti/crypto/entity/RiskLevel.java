/**
 * Risk Level Enum
 * Risk assessment levels for transactions and fraud detection
 */
package com.waqiti.crypto.entity;

public enum RiskLevel {
    MINIMAL("Minimal risk", 0, 19),
    LOW("Low risk", 20, 39),
    MEDIUM("Medium risk", 40, 59),
    HIGH("High risk", 60, 79),
    CRITICAL("Critical risk", 80, 100);

    private final String description;
    private final int minScore;
    private final int maxScore;

    RiskLevel(String description, int minScore, int maxScore) {
        this.description = description;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public String getDescription() {
        return description;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public boolean isHighRisk() {
        return this == HIGH || this == CRITICAL;
    }

    public boolean requiresReview() {
        return this == MEDIUM || this == HIGH || this == CRITICAL;
    }

    public boolean requiresBlocking() {
        return this == CRITICAL;
    }

    public static RiskLevel fromScore(double score) {
        int intScore = (int) Math.round(score);
        
        if (intScore >= CRITICAL.minScore) return CRITICAL;
        if (intScore >= HIGH.minScore) return HIGH;
        if (intScore >= MEDIUM.minScore) return MEDIUM;
        if (intScore >= LOW.minScore) return LOW;
        return MINIMAL;
    }
}