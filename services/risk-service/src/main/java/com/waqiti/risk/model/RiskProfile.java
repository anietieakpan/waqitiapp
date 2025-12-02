package com.waqiti.risk.model;

import com.waqiti.risk.domain.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Risk Profile Entity
 *
 * Stores comprehensive risk profile for users/merchants including:
 * - Historical risk scores
 * - Behavioral patterns
 * - Transaction history
 * - Risk indicators
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "risk_profiles")
public class RiskProfile {

    @Id
    private String id;

    @Indexed(unique = true)
    @NotNull
    private String userId;

    @NotNull
    private String profileType; // USER, MERCHANT

    // Current Risk Status
    @Min(0)
    @Max(1)
    private Double currentRiskScore;

    private RiskLevel currentRiskLevel;

    private LocalDateTime lastAssessmentAt;

    // Historical Risk Metrics
    private Double averageRiskScore;
    private Double peakRiskScore;
    private LocalDateTime peakRiskAt;

    private Integer totalAssessments;
    private Integer highRiskAssessments;
    private Integer blockedTransactions;

    // Transaction Behavior
    private BigDecimal averageTransactionAmount;
    private BigDecimal medianTransactionAmount;
    private BigDecimal totalTransactionVolume;
    private Integer totalTransactionCount;

    private BigDecimal largestTransaction;
    private LocalDateTime largestTransactionAt;

    private BigDecimal transactionAmountStdDev;
    private List<Integer> typicalTransactionHours; // 0-23
    private List<String> typicalTransactionDays; // MON, TUE, etc.

    // Geographic Patterns
    private Set<String> typicalCountries;
    private String primaryCountry;
    private Set<String> recentCountries;
    private Boolean hasImpossibleTravelHistory;

    // Device Patterns
    private Set<String> trustedDevices;
    private Integer totalDevicesUsed;
    private String primaryDeviceId;

    // Account Information
    private LocalDateTime accountCreatedAt;
    private Boolean isEmailVerified;
    private Boolean isPhoneVerified;
    private Boolean isIdentityVerified;
    private String verificationLevel; // BASIC, ENHANCED, FULL

    // Risk Indicators
    private Set<String> activeRiskFlags;
    private Map<String, LocalDateTime> historicalRiskFlags;

    // Merchant-Specific (if applicable)
    private String merchantCategory;
    private Double chargebackRate;
    private Double refundRate;
    private Double complaintRate;
    private LocalDateTime merchantOnboardedAt;

    // Behavioral Scores
    private Double behavioralConsistencyScore;
    private Double patternDeviationScore;
    private Double velocityScore;

    // Relationship Network
    private Set<String> frequentMerchants;
    private Set<String> frequentRecipients;

    // Machine Learning Features
    private Map<String, Double> mlFeatureVector;
    private String mlModelVersion;
    private LocalDateTime mlLastUpdated;

    // Audit Fields
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    private String createdBy;
    private String updatedBy;

    // Metadata
    private Map<String, Object> metadata;

    /**
     * Update risk score
     */
    public void updateRiskScore(Double newScore, RiskLevel newLevel) {
        this.currentRiskScore = newScore;
        this.currentRiskLevel = newLevel;
        this.lastAssessmentAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        // Update peak if necessary
        if (peakRiskScore == null || newScore > peakRiskScore) {
            this.peakRiskScore = newScore;
            this.peakRiskAt = LocalDateTime.now();
        }

        // Increment assessments
        this.totalAssessments = (totalAssessments != null ? totalAssessments : 0) + 1;

        // Track high-risk assessments
        if (newLevel == RiskLevel.HIGH) {
            this.highRiskAssessments = (highRiskAssessments != null ? highRiskAssessments : 0) + 1;
        }
    }

    /**
     * Add risk flag
     */
    public void addRiskFlag(String flag) {
        if (activeRiskFlags == null) {
            activeRiskFlags = new java.util.HashSet<>();
        }
        activeRiskFlags.add(flag);

        if (historicalRiskFlags == null) {
            historicalRiskFlags = new java.util.HashMap<>();
        }
        historicalRiskFlags.put(flag, LocalDateTime.now());

        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Remove risk flag
     */
    public void removeRiskFlag(String flag) {
        if (activeRiskFlags != null) {
            activeRiskFlags.remove(flag);
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if profile is high risk
     */
    public boolean isHighRisk() {
        return currentRiskLevel == RiskLevel.HIGH ||
               (currentRiskScore != null && currentRiskScore >= 0.7);
    }
}
