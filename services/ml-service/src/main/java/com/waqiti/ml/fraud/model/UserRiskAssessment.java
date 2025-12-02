package com.waqiti.ml.fraud.model;

import com.waqiti.common.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive user risk assessment.
 * Provides overall risk profile for a user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRiskAssessment {

    private String userId;
    private LocalDateTime timestamp;

    // Risk scores
    private Double overallRiskScore; // 0.0 - 1.0
    private Double historicalRiskScore;
    private Double sessionRiskScore;
    private Double securityScore;
    private Double behaviorConsistencyScore;
    private Double trustScore; // Inverse of risk

    // Risk classification
    private RiskLevel riskLevel;
    private String riskCategory;

    // Risk factors
    @Builder.Default
    private List<String> riskFactors = new ArrayList<>();

    // Trust indicators
    @Builder.Default
    private List<String> trustIndicators = new ArrayList<>();

    // Account metrics
    private Integer accountAge; // days
    private Integer totalTransactions;
    private Integer flaggedTransactions;
    private Integer blockedTransactions;
    private Double fraudIncidentRate;

    // Behavioral metrics
    private Double behaviorStability; // How consistent is user behavior
    private Double activityPattern; // Consistency of activity patterns
    private Integer anomalousActivities;

    // Security metrics
    private Boolean mfaEnabled;
    private Integer securityIncidents;
    private LocalDateTime lastPasswordChange;
    private Integer failedLoginAttempts;

    // Recommendations
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();

    // Watchlist status
    private Boolean onWatchlist;
    private String watchlistReason;
    private LocalDateTime watchlistAddedDate;

    /**
     * Add risk factor
     */
    public void addRiskFactor(String factor) {
        if (riskFactors == null) {
            riskFactors = new ArrayList<>();
        }
        riskFactors.add(factor);
    }

    /**
     * Check if user requires enhanced monitoring
     */
    public boolean requiresEnhancedMonitoring() {
        return riskLevel != null && riskLevel.ordinal() >= RiskLevel.MEDIUM.ordinal();
    }

    /**
     * Check if account should be restricted
     */
    public boolean shouldRestrictAccount() {
        return riskLevel == RiskLevel.CRITICAL || Boolean.TRUE.equals(onWatchlist);
    }
}
