package com.waqiti.common.fraud.profiling;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive user risk profile for fraud detection.
 * Contains behavioral, transactional, and external risk data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRiskProfile {
    
    /**
     * User identifier
     */
    private String userId;

    /**
     * User email address
     */
    private String email;

    /**
     * Profile version for tracking changes
     */
    private String profileVersion;

    /**
     * Overall risk score (0.0 to 1.0)
     */
    private double overallRiskScore;

    /**
     * Typical transaction amount for this user
     */
    private java.math.BigDecimal typicalTransactionAmount;

    /**
     * Account age in days
     */
    private Integer accountAge;

    /**
     * Account age in days (alias for accountAge for compatibility)
     */
    private Integer accountAgeDays;

    /**
     * Typical number of daily transactions
     */
    private Integer typicalDailyTransactions;

    /**
     * Customer segment classification
     */
    private String customerSegment;

    /**
     * Risk flags associated with this user
     */
    private List<String> riskFlags;

    /**
     * Typical active hours for transactions
     */
    private java.util.Set<Integer> typicalActiveHours;

    /**
     * Typical transaction locations
     */
    private List<String> typicalLocations;

    /**
     * Known devices for this user
     */
    private java.util.Set<String> knownDevices;

    /**
     * Last profile update timestamp
     */
    private LocalDateTime lastUpdated;

    /**
     * Risk score (alias for overallRiskScore for compatibility)
     */
    private double riskScore;

    /**
     * Risk level classification
     */
    private UserRiskProfileService.RiskLevel riskLevel;
    
    /**
     * Profile status
     */
    private UserRiskProfileService.ProfileStatus profileStatus;
    
    /**
     * Behavioral risk analysis data
     */
    private BehavioralRiskData behavioralData;
    
    /**
     * Transactional risk analysis data
     */
    private TransactionalRiskData transactionalData;
    
    /**
     * External risk data from third parties
     */
    private ExternalRiskData externalData;
    
    /**
     * Comprehensive risk scores breakdown
     */
    private RiskScores riskScores;
    
    /**
     * Profile creation timestamp
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Update history
     */
    private List<ProfileUpdate> updateHistory;

    /**
     * Profile metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Check if profile needs refresh
     */
    public boolean needsRefresh() {
        // Refresh if older than 24 hours
        return lastUpdated.isBefore(LocalDateTime.now().minusHours(24));
    }
    
    /**
     * Check if user is high risk
     */
    public boolean isHighRisk() {
        return riskLevel == UserRiskProfileService.RiskLevel.HIGH || 
               riskLevel == UserRiskProfileService.RiskLevel.CRITICAL;
    }
    
    /**
     * Get profile age in days
     */
    public long getProfileAgeDays() {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toDays();
    }
    
    /**
     * Get profile summary
     */
    public String getSummary() {
        return String.format("User %s: Risk Level %s (Score: %.3f), Version: %s, Status: %s",
            userId, riskLevel, overallRiskScore, profileVersion, profileStatus);
    }

    /**
     * Create a default user risk profile
     */
    public static UserRiskProfile createDefault(String userId) {
        return UserRiskProfile.builder()
            .userId(userId)
            .profileVersion("1.0")
            .overallRiskScore(0.0)
            .riskLevel(UserRiskProfileService.RiskLevel.LOW)
            .profileStatus(UserRiskProfileService.ProfileStatus.ACTIVE)
            .createdAt(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    /**
     * Update profile from fraud analysis result
     */
    public void updateFromAnalysis(com.waqiti.common.fraud.FraudAnalysisResult result) {
        if (result != null) {
            this.lastUpdated = LocalDateTime.now();
            // Update risk scores based on analysis
            if (result.getFraudScore() != null) {
                this.overallRiskScore = result.getFraudScore().getOverallScore();
            }
        }
    }

    /**
     * Get typical transaction amount
     */
    public java.math.BigDecimal getTypicalTransactionAmount() {
        if (transactionalData != null && transactionalData.getAverageTransactionAmount() != null) {
            return transactionalData.getAverageTransactionAmount();
        }
        return java.math.BigDecimal.ZERO;
    }

    /**
     * Get typical active hours
     */
    public List<Integer> getTypicalActiveHours() {
        if (behavioralData != null && behavioralData.getActiveHours() != null) {
            return behavioralData.getActiveHours();
        }
        return List.of();
    }

    /**
     * Get typical locations
     */
    public List<String> getTypicalLocations() {
        if (behavioralData != null && behavioralData.getKnownLocations() != null) {
            return behavioralData.getKnownLocations();
        }
        return List.of();
    }

    /**
     * Get known devices
     */
    public List<String> getKnownDevices() {
        if (behavioralData != null && behavioralData.getKnownDevices() != null) {
            return behavioralData.getKnownDevices();
        }
        return List.of();
    }
}

