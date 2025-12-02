/**
 * User Risk Score Response DTO
 * Contains current risk assessment information for a user
 */
package com.waqiti.payment.dto.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRiskScoreResponse {
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Current risk score (0.0 to 1.0)
     */
    private Double riskScore;
    
    /**
     * Current risk level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String riskLevel;
    
    /**
     * Risk category classification
     */
    private String riskCategory;
    
    /**
     * Overall risk rating
     */
    private String riskRating;
    
    /**
     * Transaction risk score
     */
    private Double transactionRiskScore;
    
    /**
     * Behavioral risk score
     */
    private Double behavioralRiskScore;
    
    /**
     * Device risk score
     */
    private Double deviceRiskScore;
    
    /**
     * Geographic risk score
     */
    private Double geographicRiskScore;
    
    /**
     * Whether enhanced monitoring is active
     */
    private Boolean enhancedMonitoringActive;
    
    /**
     * Risk factors contributing to the score
     */
    private List<String> riskFactors;
    
    /**
     * Recent fraud alerts for this user
     */
    private List<RiskAlert> recentAlerts;
    
    /**
     * Risk assessment metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * When the risk score was last calculated
     */
    private Instant lastCalculatedAt;
    
    /**
     * When the risk score was last updated
     */
    private Instant lastUpdatedAt;
    
    /**
     * When the next review is scheduled
     */
    private Instant nextReviewDate;
    
    /**
     * Number of days since last risk assessment
     */
    private Integer daysSinceLastReview;
    
    /**
     * Risk score trend (INCREASING, DECREASING, STABLE)
     */
    private String riskTrend;
    
    /**
     * Recommended actions based on risk level
     */
    private List<String> recommendedActions;
}