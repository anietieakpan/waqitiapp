package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Device velocity analysis result for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceVelocityResult {
    
    /**
     * Number of unique users who used this device in the last hour
     */
    private int uniqueUsersLast1h;
    
    /**
     * Number of transactions from this device in the last hour
     */
    private int transactionsLast1h;
    
    /**
     * Number of transactions from this device in the last 24 hours
     */
    private int transactionsLast24h;
    
    /**
     * Number of unique IP addresses this device connected from in last 24h
     */
    private int uniqueIpsLast24h;
    
    /**
     * Number of different geographic locations in last 24h
     */
    private int uniqueLocationsLast24h;
    
    /**
     * List of countries device was used from in last 24h
     */
    private List<String> countriesLast24h;
    
    /**
     * Average time between transactions (in minutes)
     */
    private double avgTimeBetweenTransactions;
    
    /**
     * Peak transaction rate (transactions per hour)
     */
    private double peakTransactionRate;
    
    /**
     * Whether device showed burst activity patterns
     */
    private boolean hasBurstActivity;
    
    /**
     * When this velocity analysis was performed
     */
    private LocalDateTime analyzedAt;
    
    /**
     * Calculate velocity-based risk score
     */
    public double getRiskScore() {
        double score = 0.0;
        
        // Multiple users on same device in short time (account sharing/compromise)
        if (uniqueUsersLast1h > 3) {
            score += Math.min(0.4, uniqueUsersLast1h * 0.1);
        }
        
        // High transaction frequency
        if (transactionsLast1h > 10) {
            score += Math.min(0.3, transactionsLast1h * 0.02);
        }
        
        // Very high 24h transaction volume
        if (transactionsLast24h > 50) {
            score += Math.min(0.3, transactionsLast24h * 0.005);
        }
        
        // Multiple geographic locations (impossible travel)
        if (uniqueLocationsLast24h > 3) {
            score += Math.min(0.4, uniqueLocationsLast24h * 0.1);
        }
        
        // Multiple countries (very suspicious)
        if (countriesLast24h != null && countriesLast24h.size() > 2) {
            score += Math.min(0.5, countriesLast24h.size() * 0.15);
        }
        
        // Burst activity patterns
        if (hasBurstActivity) {
            score += 0.2;
        }
        
        // Very fast transactions (automated/bot behavior)
        if (avgTimeBetweenTransactions < 5.0) {
            score += 0.2;
        }
        
        // Multiple IP addresses
        if (uniqueIpsLast24h > 5) {
            score += Math.min(0.2, uniqueIpsLast24h * 0.03);
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Get risk level based on velocity analysis
     */
    public FraudRiskLevel getRiskLevel() {
        double riskScore = getRiskScore();
        if (riskScore >= 0.8) return FraudRiskLevel.CRITICAL;
        if (riskScore >= 0.6) return FraudRiskLevel.HIGH;
        if (riskScore >= 0.4) return FraudRiskLevel.MEDIUM;
        if (riskScore >= 0.2) return FraudRiskLevel.LOW;
        return FraudRiskLevel.MINIMAL;
    }
    
    /**
     * Check if device shows impossible travel patterns
     */
    public boolean hasImpossibleTravel() {
        return countriesLast24h != null && countriesLast24h.size() > 3;
    }
    
    /**
     * Check if device shows bot-like behavior
     */
    public boolean showsBotBehavior() {
        return avgTimeBetweenTransactions < 2.0 && transactionsLast1h > 20;
    }
}