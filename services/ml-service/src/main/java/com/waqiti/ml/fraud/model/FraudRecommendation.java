package com.waqiti.ml.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fraud prevention recommendation.
 * Provides actionable recommendations based on fraud analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudRecommendation {

    private RecommendationType type;
    private String action;
    private String reason;
    private RecommendationPriority priority;
    private String description;

    // Additional context
    private String affectedEntity; // USER, TRANSACTION, DEVICE, ACCOUNT
    private String entityId;
    private Double confidenceScore; // Confidence in recommendation (0.0 - 1.0)

    public FraudRecommendation(RecommendationType type, String action) {
        this.type = type;
        this.action = action;
    }

    public FraudRecommendation(RecommendationType type, String action, String reason) {
        this.type = type;
        this.action = action;
        this.reason = reason;
    }

    /**
     * Recommendation types
     */
    public enum RecommendationType {
        BLOCK_TRANSACTION,
        MANUAL_REVIEW,
        CHALLENGE_USER, // Require additional authentication
        LIMIT_ACCOUNT,
        MONITOR_ACTIVITY,
        FLAG_FOR_INVESTIGATION,
        CONTACT_USER,
        UPDATE_RISK_PROFILE,
        ENABLE_MFA,
        FREEZE_ACCOUNT,
        VERIFY_IDENTITY,
        RESTRICT_FUNCTIONALITY,
        ALERT_COMPLIANCE,
        REPORT_AUTHORITIES
    }

    /**
     * Recommendation priority
     */
    public enum RecommendationPriority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL,
        IMMEDIATE
    }
}
