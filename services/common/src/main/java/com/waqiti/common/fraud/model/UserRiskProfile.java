package com.waqiti.common.fraud.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive user risk profile for fraud detection and risk assessment
 * Tracks behavioral patterns, transaction history, and risk indicators
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_risk_profiles", indexes = {
    @Index(name = "idx_user_risk_user_id", columnList = "user_id", unique = true),
    @Index(name = "idx_user_risk_score", columnList = "overall_risk_score"),
    @Index(name = "idx_user_risk_level", columnList = "risk_level"),
    @Index(name = "idx_user_risk_updated", columnList = "last_updated")
})
public class UserRiskProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "email")
    private String email; // PRODUCTION FIX: User email for fraud analysis

    @Column(name = "overall_risk_score", precision = 5, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal overallRiskScore = BigDecimal.ZERO;
    
    @Transient
    private Double riskScore;

    @Column(name = "risk_level", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Column(name = "fraud_score", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal fraudScore = BigDecimal.ZERO;

    @Column(name = "behavioral_score", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal behavioralScore = BigDecimal.ZERO;

    @Column(name = "velocity_score", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal velocityScore = BigDecimal.ZERO;

    @Column(name = "geographic_score", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal geographicScore = BigDecimal.ZERO;

    @Column(name = "device_score", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal deviceScore = BigDecimal.ZERO;

    @Column(name = "transaction_count_24h")
    @Builder.Default
    private Integer transactionCount24h = 0;

    @Column(name = "transaction_amount_24h", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal transactionAmount24h = BigDecimal.ZERO;

    @Column(name = "failed_attempts_24h")
    @Builder.Default
    private Integer failedAttempts24h = 0;

    @Column(name = "unique_devices_7d")
    @Builder.Default
    private Integer uniqueDevices7d = 0;

    @Column(name = "unique_locations_7d")
    @Builder.Default
    private Integer uniqueLocations7d = 0;

    @Column(name = "unique_merchants_30d")
    @Builder.Default
    private Integer uniqueMerchants30d = 0;

    @Column(name = "average_transaction_amount", precision = 19, scale = 2)
    private BigDecimal averageTransactionAmount;

    @Column(name = "highest_transaction_amount", precision = 19, scale = 2)
    private BigDecimal highestTransactionAmount;

    @Column(name = "preferred_transaction_times", columnDefinition = "TEXT")
    private String preferredTransactionTimes; // JSON array of hours

    @Column(name = "preferred_locations", columnDefinition = "TEXT")
    private String preferredLocations; // JSON array of location data

    @Column(name = "preferred_merchants", columnDefinition = "TEXT")
    private String preferredMerchants; // JSON array of merchant IDs

    @Column(name = "kyc_status")
    @Enumerated(EnumType.STRING)
    private KYCStatus kycStatus;

    @Column(name = "account_age_days")
    private Integer accountAgeDays;

    @Column(name = "last_successful_transaction")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastSuccessfulTransaction;

    @Column(name = "last_failed_transaction")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastFailedTransaction;

    @Column(name = "first_transaction_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime firstTransactionDate;

    @ElementCollection
    @CollectionTable(name = "user_risk_alerts", 
        joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "alert_id")
    private List<String> recentAlerts;

    @ElementCollection
    @CollectionTable(name = "user_risk_flags", 
        joinColumns = @JoinColumn(name = "profile_id"))
    @MapKeyColumn(name = "flag_type")
    @Column(name = "flag_value")
    private Map<String, String> riskFlags;

    @ElementCollection
    @CollectionTable(name = "user_behavioral_indicators", 
        joinColumns = @JoinColumn(name = "profile_id"))
    @MapKeyColumn(name = "indicator_name")
    @Column(name = "indicator_value")
    private Map<String, BigDecimal> behavioralIndicators;

    @Column(name = "whitelist_status", nullable = false)
    @Builder.Default
    private Boolean whitelistStatus = false;

    @Column(name = "blacklist_status", nullable = false)
    @Builder.Default
    private Boolean blacklistStatus = false;

    @Column(name = "manual_review_required", nullable = false)
    @Builder.Default
    private Boolean manualReviewRequired = false;

    @Column(name = "enhanced_monitoring", nullable = false)
    @Builder.Default
    private Boolean enhancedMonitoring = false;

    @Column(name = "velocity_limits_applied", nullable = false)
    @Builder.Default
    private Boolean velocityLimitsApplied = false;

    @Column(name = "step_up_auth_required", nullable = false)
    @Builder.Default
    private Boolean stepUpAuthRequired = false;

    @Column(name = "suspicious_activity_detected", nullable = false)
    @Builder.Default
    private Boolean suspiciousActivityDetected = false;

    @Column(name = "pep_status", nullable = false)
    @Builder.Default
    private Boolean pepStatus = false;

    @Column(name = "sanctions_status", nullable = false)
    @Builder.Default
    private Boolean sanctionsStatus = false;

    @Column(name = "high_risk_merchant_usage", nullable = false)
    @Builder.Default
    private Boolean highRiskMerchantUsage = false;

    @Column(name = "cross_border_activity", nullable = false)
    @Builder.Default
    private Boolean crossBorderActivity = false;

    @Column(name = "unusual_time_activity", nullable = false)
    @Builder.Default
    private Boolean unusualTimeActivity = false;

    @Column(name = "device_anomalies", nullable = false)
    @Builder.Default
    private Boolean deviceAnomalies = false;

    @Column(name = "location_anomalies", nullable = false)
    @Builder.Default
    private Boolean locationAnomalies = false;

    @Column(name = "pattern_changes", nullable = false)
    @Builder.Default
    private Boolean patternChanges = false;

    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Column(name = "last_updated", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUpdated;

    @Column(name = "last_reviewed")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastReviewed;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "risk_model_version")
    private String riskModelVersion;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Version
    private Long version;

    /**
     * Risk levels
     */
    public enum RiskLevel {
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH,
        CRITICAL
    }

    /**
     * KYC status
     */
    public enum KYCStatus {
        NOT_STARTED,
        IN_PROGRESS,
        PENDING_REVIEW,
        APPROVED,
        REJECTED,
        EXPIRED,
        ENHANCED_DUE_DILIGENCE_REQUIRED
    }

    /**
     * Pre-persist callback
     */
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        lastUpdated = LocalDateTime.now();
    }

    /**
     * Pre-update callback
     */
    @PreUpdate
    public void preUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    /**
     * Calculate overall risk score based on component scores
     */
    public BigDecimal calculateOverallRiskScore() {
        BigDecimal score = BigDecimal.ZERO;
        int componentCount = 0;

        if (fraudScore != null) {
            score = score.add(fraudScore.multiply(new BigDecimal("0.3")));
            componentCount++;
        }
        
        if (behavioralScore != null) {
            score = score.add(behavioralScore.multiply(new BigDecimal("0.25")));
            componentCount++;
        }
        
        if (velocityScore != null) {
            score = score.add(velocityScore.multiply(new BigDecimal("0.2")));
            componentCount++;
        }
        
        if (geographicScore != null) {
            score = score.add(geographicScore.multiply(new BigDecimal("0.15")));
            componentCount++;
        }
        
        if (deviceScore != null) {
            score = score.add(deviceScore.multiply(new BigDecimal("0.1")));
            componentCount++;
        }

        // Add penalties for high-risk flags
        if (blacklistStatus) score = score.add(new BigDecimal("50"));
        if (sanctionsStatus) score = score.add(new BigDecimal("100"));
        if (pepStatus) score = score.add(new BigDecimal("30"));
        if (suspiciousActivityDetected) score = score.add(new BigDecimal("25"));

        // Add bonuses for positive indicators
        if (whitelistStatus) score = score.subtract(new BigDecimal("20"));
        if (kycStatus == KYCStatus.APPROVED) score = score.subtract(new BigDecimal("10"));

        this.overallRiskScore = score.min(new BigDecimal("100")).max(BigDecimal.ZERO);
        this.riskLevel = determineRiskLevel(this.overallRiskScore);
        
        return this.overallRiskScore;
    }

    /**
     * Determine risk level based on score
     */
    private RiskLevel determineRiskLevel(BigDecimal score) {
        if (score.compareTo(new BigDecimal("90")) >= 0) return RiskLevel.CRITICAL;
        if (score.compareTo(new BigDecimal("75")) >= 0) return RiskLevel.VERY_HIGH;
        if (score.compareTo(new BigDecimal("60")) >= 0) return RiskLevel.HIGH;
        if (score.compareTo(new BigDecimal("40")) >= 0) return RiskLevel.MEDIUM;
        if (score.compareTo(new BigDecimal("20")) >= 0) return RiskLevel.LOW;
        return RiskLevel.VERY_LOW;
    }

    /**
     * Check if user requires enhanced monitoring
     */
    public boolean requiresEnhancedMonitoring() {
        return riskLevel.ordinal() >= RiskLevel.HIGH.ordinal() ||
               blacklistStatus ||
               sanctionsStatus ||
               pepStatus ||
               suspiciousActivityDetected ||
               enhancedMonitoring;
    }

    /**
     * Check if user requires manual review
     */
    public boolean requiresManualReview() {
        return riskLevel == RiskLevel.CRITICAL ||
               riskLevel == RiskLevel.VERY_HIGH ||
               manualReviewRequired ||
               (overallRiskScore != null && overallRiskScore.compareTo(new BigDecimal("80")) >= 0);
    }

    /**
     * Check if user transactions should be blocked
     */
    public boolean shouldBlockTransactions() {
        return blacklistStatus ||
               sanctionsStatus ||
               riskLevel == RiskLevel.CRITICAL ||
               (overallRiskScore != null && overallRiskScore.compareTo(new BigDecimal("95")) >= 0);
    }

    /**
     * Check if step-up authentication is required
     */
    public boolean requiresStepUpAuth() {
        return stepUpAuthRequired ||
               riskLevel.ordinal() >= RiskLevel.HIGH.ordinal() ||
               deviceAnomalies ||
               locationAnomalies ||
               unusualTimeActivity;
    }

    /**
     * Update velocity metrics
     */
    public void updateVelocityMetrics(int transactions24h, BigDecimal amount24h, int failures24h) {
        this.transactionCount24h = transactions24h;
        this.transactionAmount24h = amount24h;
        this.failedAttempts24h = failures24h;
        
        // Recalculate velocity score
        calculateVelocityScore();
        calculateOverallRiskScore();
    }

    /**
     * Calculate velocity score based on transaction patterns
     */
    private void calculateVelocityScore() {
        BigDecimal score = BigDecimal.ZERO;
        
        // Transaction count risk
        if (transactionCount24h > 50) score = score.add(new BigDecimal("40"));
        else if (transactionCount24h > 20) score = score.add(new BigDecimal("20"));
        else if (transactionCount24h > 10) score = score.add(new BigDecimal("10"));
        
        // Amount risk
        if (transactionAmount24h.compareTo(new BigDecimal("100000")) > 0) {
            score = score.add(new BigDecimal("30"));
        } else if (transactionAmount24h.compareTo(new BigDecimal("50000")) > 0) {
            score = score.add(new BigDecimal("20"));
        }
        
        // Failed attempts risk
        if (failedAttempts24h > 10) score = score.add(new BigDecimal("30"));
        else if (failedAttempts24h > 5) score = score.add(new BigDecimal("15"));
        else if (failedAttempts24h > 2) score = score.add(new BigDecimal("5"));
        
        this.velocityScore = score.min(new BigDecimal("100"));
    }

    /**
     * Add risk flag
     */
    public void addRiskFlag(String flagType, String flagValue) {
        if (riskFlags == null) {
            riskFlags = new java.util.HashMap<>();
        }
        riskFlags.put(flagType, flagValue);
    }

    /**
     * Remove risk flag
     */
    public void removeRiskFlag(String flagType) {
        if (riskFlags != null) {
            riskFlags.remove(flagType);
        }
    }

    /**
     * Check if specific risk flag exists
     */
    public boolean hasRiskFlag(String flagType) {
        return riskFlags != null && riskFlags.containsKey(flagType);
    }

    /**
     * Get days since last review
     */
    public long getDaysSinceLastReview() {
        if (lastReviewed == null) {
            return java.time.Duration.between(createdAt, LocalDateTime.now()).toDays();
        }
        return java.time.Duration.between(lastReviewed, LocalDateTime.now()).toDays();
    }

    /**
     * Check if profile needs review based on time and risk level
     */
    public boolean needsReview() {
        long daysSinceReview = getDaysSinceLastReview();
        
        return switch (riskLevel) {
            case CRITICAL -> daysSinceReview > 1;      // Daily review
            case VERY_HIGH -> daysSinceReview > 3;     // Every 3 days
            case HIGH -> daysSinceReview > 7;          // Weekly
            case MEDIUM -> daysSinceReview > 30;       // Monthly
            case LOW, VERY_LOW -> daysSinceReview > 90; // Quarterly
        };
    }
    
    /**
     * Get earliest transaction hour (default 6 AM if not set)
     */
    public int getEarliestTransactionHour() {
        // Parse from preferredTransactionTimes if available
        // For now, return default values
        return 6; // 6 AM
    }
    
    /**
     * Get latest transaction hour (default 11 PM if not set)
     */
    public int getLatestTransactionHour() {
        // Parse from preferredTransactionTimes if available
        // For now, return default values
        return 23; // 11 PM
    }
    
    /**
     * Get common locations for the user
     */
    public java.util.Set<String> getCommonLocations() {
        if (preferredLocations != null && !preferredLocations.trim().isEmpty()) {
            try {
                // In a real implementation, this would parse JSON
                return java.util.Set.of(preferredLocations.split(","));
            } catch (Exception e) {
                // Return empty set if parsing fails
            }
        }
        return java.util.Set.of();
    }
    
    /**
     * Get account age in days (alias method for compatibility)
     */
    public Integer getAccountAgeDays() {
        return accountAgeDays;
    }
}