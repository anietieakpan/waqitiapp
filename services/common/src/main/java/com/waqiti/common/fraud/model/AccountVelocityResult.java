package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Result of account velocity analysis for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVelocityResult {
    
    private String accountNumber;
    private UUID userId;
    private VelocityRiskLevel riskLevel;
    private double riskScore;
    
    // Transaction velocity
    private int transactionsLast1h;
    private int transactionsLast24h;
    private int transactionsLast24Hours;
    private int transactionsLastWeek;
    private int transactionsLastMonth;
    private int uniqueUsersLast24h;
    private BigDecimal volumeLast24Hours;
    private BigDecimal volumeLastWeek;
    private BigDecimal volumeLastMonth;
    
    // Account activity patterns
    private LocalDateTime firstTransactionDate;
    private LocalDateTime lastTransactionDate;
    private double averageTransactionAmount;
    private double averageTransactionFrequency;
    private int dormantDays;
    private boolean hasRecentActivitySpike;
    
    // Velocity thresholds
    private boolean exceedsTransactionLimit;
    private boolean exceedsVolumeLimit;
    private boolean exceedsFrequencyThreshold;
    private BigDecimal dailyTransactionLimit;
    private BigDecimal monthlyVolumeLimit;
    
    // Behavioral indicators
    private boolean indicatesAccountTakeover;
    private boolean showsAutomatedActivity;
    private boolean hasUnusualTransactionTiming;
    private boolean deviatesFromHistoricalPattern;
    private double behaviorDeviationScore;
    
    // Cross-account analysis
    private List<String> linkedAccounts;
    private int totalAccountsUsed;
    private boolean showsAccountHopping;
    private boolean hasCircularTransactions;
    
    // Risk factors
    private List<String> riskFactors;
    private boolean hasHighRiskTransactions;
    private boolean exceedsRiskThreshold;
    private String primaryRiskFactor;
    
    // Analysis metadata
    private LocalDateTime analyzedAt;
    private String analysisTimeWindow;
    private Map<String, Object> velocityMetrics;
    private double confidence;
    private List<String> triggerReasons;
    
    /**
     * Velocity risk levels for accounts
     */
    public enum VelocityRiskLevel {
        NORMAL(0.1, "Normal account activity"),
        ELEVATED(0.3, "Elevated account activity"),
        HIGH(0.6, "High velocity activity"),
        SUSPICIOUS(0.8, "Suspicious account behavior"),
        CRITICAL(1.0, "Critical velocity breach");
        
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
     * Check if account activity is within normal parameters
     */
    public boolean isWithinNormalLimits() {
        return riskLevel == VelocityRiskLevel.NORMAL && 
               !exceedsTransactionLimit && 
               !exceedsVolumeLimit;
    }
    
    /**
     * Check if account shows signs of compromise
     */
    public boolean showsSignsOfCompromise() {
        return indicatesAccountTakeover || 
               deviatesFromHistoricalPattern && behaviorDeviationScore > 0.7;
    }
    
    /**
     * Check if account requires immediate intervention
     */
    public boolean requiresImmediateIntervention() {
        return riskLevel == VelocityRiskLevel.CRITICAL || 
               (riskLevel == VelocityRiskLevel.SUSPICIOUS && indicatesAccountTakeover);
    }
    
    /**
     * Check if activity suggests money laundering
     */
    public boolean suggestsMoneyLaundering() {
        return hasCircularTransactions || 
               (showsAccountHopping && volumeLast24Hours != null && 
                volumeLast24Hours.compareTo(new BigDecimal("50000")) > 0);
    }
    
    /**
     * Get velocity intensity rating
     */
    public String getVelocityIntensity() {
        if (riskScore >= 0.8) return "EXTREME";
        if (riskScore >= 0.6) return "HIGH";
        if (riskScore >= 0.4) return "MODERATE";
        if (riskScore >= 0.2) return "LOW";
        return "MINIMAL";
    }
    
    /**
     * Get recommended actions based on velocity analysis
     */
    public List<String> getRecommendedActions() {
        List<String> actions = new java.util.ArrayList<>();
        
        if (requiresImmediateIntervention()) {
            actions.add("FREEZE_ACCOUNT");
            actions.add("NOTIFY_COMPLIANCE");
        } else if (showsSignsOfCompromise()) {
            actions.add("REQUIRE_ADDITIONAL_AUTH");
            actions.add("ENHANCED_MONITORING");
        } else if (exceedsTransactionLimit) {
            actions.add("APPLY_VELOCITY_CONTROLS");
        }
        
        if (suggestsMoneyLaundering()) {
            actions.add("FILE_SAR");
            actions.add("ESCALATE_TO_AML_TEAM");
        }
        
        if (actions.isEmpty()) {
            actions.add("CONTINUE_MONITORING");
        }
        
        return actions;
    }
    
    /**
     * Generate velocity analysis summary
     */
    public String getVelocitySummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Account Velocity Analysis\n");
        summary.append("Account: ").append(maskAccount(accountNumber)).append("\n");
        summary.append("Risk Level: ").append(riskLevel.getDescription()).append(" (").append(String.format("%.2f", riskScore)).append(")\n");
        summary.append("24h Transactions: ").append(transactionsLast24Hours).append("\n");
        summary.append("24h Volume: $").append(volumeLast24Hours != null ? volumeLast24Hours.toString() : "0").append("\n");
        
        if (exceedsTransactionLimit) summary.append("- Exceeds transaction limits\n");
        if (indicatesAccountTakeover) summary.append("- Possible account takeover\n");
        if (showsAutomatedActivity) summary.append("- Shows automated activity\n");
        if (hasCircularTransactions) summary.append("- Circular transaction patterns detected\n");
        
        return summary.toString();
    }
    
    /**
     * Mask account number for security
     */
    private String maskAccount(String account) {
        if (account == null || account.length() < 4) return "****";
        return "****" + account.substring(account.length() - 4);
    }
}