package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Result of email velocity analysis for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVelocityResult {
    
    private String email;
    private UUID userId;
    private VelocityRiskLevel riskLevel;
    private double riskScore;
    private double velocityScore; // PRODUCTION FIX: Overall velocity score
    
    // Velocity metrics
    private int transactionsLast1h;
    private int transactionsLastHour; // PRODUCTION FIX: Alias for transactionsLast1h
    private int transactionsLast24h;
    private int transactionsLast24Hours;
    private int transactionsLastWeek;
    private int transactionsLastMonth;
    private int accountCreationsLast24Hours;
    private int accountsCreatedLastHour; // Alias for high-frequency account creation
    private int loginAttemptsLast24Hours;
    private int uniqueAccountsLast24h;
    private double velocityRisk;
    
    // Time-based analysis
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
    private long daysSinceFirstSeen;
    private double averageTransactionInterval;
    private List<LocalDateTime> recentTransactionTimes;
    
    // Behavior patterns
    private boolean hasBurstActivity;
    private boolean hasUnusualTiming;
    private boolean hasNightTimeActivity;
    private boolean hasWeekendActivity;
    private boolean showsHumanPattern;
    
    // Velocity thresholds
    private boolean exceedsHourlyLimit;
    private boolean exceedsDailyLimit;
    private boolean exceedsWeeklyLimit;
    private boolean exceedsMonthlyLimit;
    
    // Cross-reference analysis
    private List<String> associatedDevices;
    private List<String> associatedIpAddresses;
    private int uniqueDeviceCount;
    private int uniqueIpCount;
    private boolean hasDeviceHopping;
    private boolean hasIpHopping;
    
    // Risk indicators
    private boolean indicatesAutomatedActivity;
    private boolean indicatesAccountTakeover;
    private boolean indicatesMultiAccounting;
    private double suspicionScore;
    
    // Analysis metadata
    private LocalDateTime analyzedAt;
    private String analysisTimeWindow;
    private Map<String, Integer> velocityBreakdown;
    private double confidence;
    private List<String> triggerReasons;
    
    /**
     * Velocity risk levels
     */
    public enum VelocityRiskLevel {
        NORMAL(0.1, "Normal velocity"),
        ELEVATED(0.3, "Elevated activity"),
        HIGH(0.6, "High velocity"),
        CRITICAL(0.8, "Critical velocity"),
        EXTREME(1.0, "Extreme velocity");
        
        private final double score;
        private final String description;
        
        VelocityRiskLevel(double score, String description) {
            this.score = score;
            this.description = description;
        }
        
        public double getScore() { return score; }
        public String getDescription() { return description; }
    }
    
    /**
     * Check if velocity is within normal limits
     */
    public boolean isWithinNormalLimits() {
        return riskLevel == VelocityRiskLevel.NORMAL;
    }
    
    /**
     * Check if velocity requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return riskLevel == VelocityRiskLevel.CRITICAL || riskLevel == VelocityRiskLevel.EXTREME;
    }
    
    /**
     * Check if activity suggests bot behavior
     */
    public boolean suggestsBotBehavior() {
        return indicatesAutomatedActivity || 
               (!showsHumanPattern && (hasBurstActivity || exceedsHourlyLimit));
    }
    
    /**
     * Check if velocity pattern indicates fraud
     */
    public boolean indicatesFraud() {
        return riskScore > 0.7 && (indicatesAccountTakeover || indicatesMultiAccounting);
    }
    
    /**
     * Get velocity intensity score
     */
    public double getVelocityIntensity() {
        double intensity = 0.0;
        intensity += transactionsLast24Hours * 0.1;
        intensity += accountCreationsLast24Hours * 0.3;
        intensity += (uniqueDeviceCount > 3 ? 0.2 : 0.0);
        intensity += (uniqueIpCount > 5 ? 0.2 : 0.0);
        return Math.min(intensity, 1.0);
    }

    /**
     * Get transactions in last 24 hours
     */
    public int getTransactionsLast24h() {
        return transactionsLast24h > 0 ? transactionsLast24h : transactionsLast24Hours;
    }
    
    /**
     * Generate velocity analysis summary
     */
    public String getVelocitySummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Email Velocity Analysis for: ").append(email).append("\n");
        summary.append("Risk Level: ").append(riskLevel.getDescription()).append(" (").append(String.format("%.2f", riskScore)).append(")\n");
        summary.append("24h Transactions: ").append(transactionsLast24Hours).append("\n");
        summary.append("Unique Devices: ").append(uniqueDeviceCount).append("\n");
        summary.append("Unique IPs: ").append(uniqueIpCount).append("\n");
        
        if (exceedsDailyLimit) summary.append("- Exceeds daily transaction limit\n");
        if (hasBurstActivity) summary.append("- Shows burst activity pattern\n");
        if (indicatesAutomatedActivity) summary.append("- Indicates automated activity\n");
        if (hasDeviceHopping) summary.append("- Shows device hopping behavior\n");
        
        return summary.toString();
    }
}