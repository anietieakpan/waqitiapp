/**
 * User Risk Profile DTO
 * Comprehensive risk profile information for a user
 */
package com.waqiti.payment.dto.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRiskProfile {
    
    /**
     * User ID
     */
    private String userId;
    
    /**
     * Overall risk score (0.0 to 1.0)
     */
    private Double overallRiskScore;
    
    /**
     * Risk level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    private String riskLevel;
    
    /**
     * Risk tier classification
     */
    private String riskTier;
    
    /**
     * Account age in days
     */
    private Integer accountAgeDays;
    
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
     * Identity verification risk score
     */
    private Double identityRiskScore;
    
    /**
     * Total number of transactions
     */
    private Long totalTransactionCount;
    
    /**
     * Total transaction volume
     */
    private BigDecimal totalTransactionVolume;
    
    /**
     * Average transaction amount
     */
    private BigDecimal averageTransactionAmount;
    
    /**
     * Number of failed transactions
     */
    private Long failedTransactionCount;
    
    /**
     * Number of disputed transactions
     */
    private Long disputedTransactionCount;
    
    /**
     * Number of chargebacks
     */
    private Long chargebackCount;
    
    /**
     * Fraud alerts count
     */
    private Long fraudAlertCount;
    
    /**
     * Recent fraud alerts
     */
    private List<RiskAlert> recentFraudAlerts;
    
    /**
     * Active risk factors
     */
    private List<String> activeRiskFactors;
    
    /**
     * Risk mitigation measures in place
     */
    private List<String> activeMitigations;
    
    /**
     * KYC verification status
     */
    private String kycStatus;
    
    /**
     * KYC risk level
     */
    private String kycRiskLevel;
    
    /**
     * AML risk status
     */
    private String amlRiskStatus;
    
    /**
     * Whether user is on watchlist
     */
    private Boolean onWatchlist;
    
    /**
     * Watchlist categories if applicable
     */
    private List<String> watchlistCategories;
    
    /**
     * Whether enhanced monitoring is active
     */
    private Boolean enhancedMonitoringActive;
    
    /**
     * Enhanced monitoring start date
     */
    private Instant enhancedMonitoringStartDate;
    
    /**
     * Enhanced monitoring end date
     */
    private Instant enhancedMonitoringEndDate;
    
    /**
     * Risk assessment history summary
     */
    private Map<String, Object> assessmentHistory;
    
    /**
     * Last significant risk event
     */
    private Instant lastRiskEvent;
    
    /**
     * Risk trend over time (INCREASING, DECREASING, STABLE)
     */
    private String riskTrend;
    
    /**
     * Risk velocity indicators
     */
    private Map<String, Object> riskVelocity;
    
    /**
     * Recommended monitoring frequency
     */
    private String monitoringFrequency;
    
    /**
     * Next scheduled review date
     */
    private Instant nextReviewDate;
    
    /**
     * Profile creation timestamp
     */
    private Instant createdAt;
    
    /**
     * Last profile update timestamp
     */
    private Instant lastUpdatedAt;
    
    /**
     * Profile version for tracking changes
     */
    private String profileVersion;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
}