package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fraud status assessment for routing numbers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutingNumberFraudStatus {
    
    private String routingNumber;
    private FraudStatus status;
    private double riskScore;
    private String bankName;
    private String bankLocation;
    private LocalDateTime lastAssessment;
    private LocalDateTime blacklistDate;
    private String blacklistReason;
    private List<String> fraudIndicators;
    private int fraudReportCount;
    private double velocityScore;
    private boolean isActive;
    
    /**
     * Fraud status enumeration
     */
    public enum FraudStatus {
        CLEAN,          // No fraud indicators
        SUSPICIOUS,     // Some indicators but not confirmed
        CONFIRMED_FRAUD, // Confirmed fraudulent activity
        BLACKLISTED,    // Permanently blacklisted
        UNDER_REVIEW    // Currently being investigated
    }
    
    /**
     * Check if routing number is safe to use
     */
    public boolean isSafeToUse() {
        return status == FraudStatus.CLEAN || status == FraudStatus.SUSPICIOUS;
    }
    
    /**
     * Check if requires manual review
     */
    public boolean requiresReview() {
        return status == FraudStatus.SUSPICIOUS || status == FraudStatus.UNDER_REVIEW;
    }
    
    /**
     * Check if should be blocked
     */
    public boolean shouldBlock() {
        return status == FraudStatus.CONFIRMED_FRAUD || status == FraudStatus.BLACKLISTED;
    }
    
    /**
     * Check if routing number is fraudulent
     */
    public boolean isFraudulent() {
        return status == FraudStatus.CONFIRMED_FRAUD || status == FraudStatus.BLACKLISTED;
    }
    
    /**
     * Get fraud reason
     */
    public String getReason() {
        return blacklistReason != null ? blacklistReason : 
               (status != null ? "Status: " + status.toString() : "Unknown");
    }
}