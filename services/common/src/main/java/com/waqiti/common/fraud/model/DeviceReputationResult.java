package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Device reputation analysis result for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceReputationResult {
    
    /**
     * Overall device reputation score (0.0 to 1.0)
     */
    private double reputation;
    
    /**
     * Number of previous fraud incidents associated with this device
     */
    private int previousFraudCount;
    
    /**
     * When this device was first seen in the system
     */
    private LocalDateTime firstSeen;
    
    /**
     * When this device was last seen in the system
     */
    private LocalDateTime lastSeen;
    
    /**
     * Number of different users associated with this device
     */
    private int associatedUserCount;
    
    /**
     * Whether device is on any blacklists
     */
    private boolean isBlacklisted;
    
    /**
     * List of blacklist sources if blacklisted
     */
    private List<String> blacklistSources;
    
    /**
     * Geographic locations where device has been used
     */
    private List<String> usageLocations;
    
    /**
     * Number of successful transactions from this device
     */
    private int successfulTransactions;
    
    /**
     * Number of failed transactions from this device
     */
    private int failedTransactions;
    
    /**
     * Device characteristics and fingerprint details
     */
    private Map<String, String> deviceCharacteristics;
    
    /**
     * When this reputation analysis was performed
     */
    private LocalDateTime analyzedAt;
    
    /**
     * Calculate device risk score based on reputation factors
     */
    public double getRiskScore() {
        double score = 0.0;
        
        // Factor in previous fraud
        if (previousFraudCount > 0) {
            score += Math.min(0.6, previousFraudCount * 0.2);
        }
        
        // Factor in blacklist status
        if (isBlacklisted) {
            score += 0.5;
        }
        
        // Factor in device age (very new devices are slightly riskier)
        if (firstSeen != null && firstSeen.isAfter(LocalDateTime.now().minusHours(24))) {
            score += 0.1;
        }
        
        // Factor in transaction failure rate
        int totalTransactions = successfulTransactions + failedTransactions;
        if (totalTransactions > 0) {
            double failureRate = (double) failedTransactions / totalTransactions;
            if (failureRate > 0.3) {
                score += failureRate * 0.3;
            }
        }
        
        // Factor in multiple users (shared devices can be riskier)
        if (associatedUserCount > 5) {
            score += Math.min(0.2, associatedUserCount * 0.02);
        }
        
        // Factor in reputation (lower reputation = higher risk)
        score += (1.0 - reputation) * 0.4;
        
        return Math.min(1.0, score);
    }
    
    /**
     * Get risk level based on device reputation
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
     * Check if device shows signs of compromise
     */
    public boolean showsCompromiseSigns() {
        return previousFraudCount > 2 || 
               isBlacklisted || 
               (failedTransactions > successfulTransactions && failedTransactions > 3);
    }
}