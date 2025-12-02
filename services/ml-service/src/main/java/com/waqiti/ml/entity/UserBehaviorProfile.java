package com.waqiti.ml.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.Version;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * Production-ready User Behavior Profile entity with comprehensive tracking.
 * Stores ML features, behavioral patterns, and risk assessments.
 */
@Entity
@Table(name = "user_behavior_profiles", 
       indexes = {
           @Index(name = "idx_user_behavior_user_id", columnList = "user_id"),
           @Index(name = "idx_user_behavior_risk_score", columnList = "risk_score DESC"),
           @Index(name = "idx_user_behavior_last_updated", columnList = "last_updated DESC"),
           @Index(name = "idx_user_behavior_total_count", columnList = "total_transaction_count DESC")
       })
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"behaviorMetrics", "mlFeatures"})
public class UserBehaviorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private String id;

    @Column(name = "user_id", nullable = false, unique = true, length = 36)
    @EqualsAndHashCode.Include
    private String userId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Column(name = "total_transaction_count", nullable = false)
    private Long totalTransactionCount = 0L;

    @Column(name = "risk_score", nullable = false, precision = 5, scale = 2)
    private Double riskScore = 50.0;

    @Column(name = "risk_level", length = 20)
    private String riskLevel = "MEDIUM";

    @Column(name = "profile_status", length = 20)
    private String profileStatus = "ACTIVE"; // ACTIVE, SUSPENDED, UNDER_REVIEW

    @Column(name = "last_analysis_timestamp")
    private LocalDateTime lastAnalysisTimestamp;

    @Embedded
    private BehaviorMetrics behaviorMetrics;

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "ml_features", columnDefinition = "jsonb")
    private Map<String, Object> mlFeatures = new HashMap<>();

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "behavioral_patterns", columnDefinition = "jsonb")
    private Map<String, Object> behavioralPatterns = new HashMap<>();

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "risk_factors", columnDefinition = "jsonb")
    private Map<String, Double> riskFactors = new HashMap<>();

    @Type(com.vladmihalcea.hibernate.type.json.JsonType.class)
    @Column(name = "compliance_flags", columnDefinition = "jsonb")
    private Map<String, Boolean> complianceFlags = new HashMap<>();

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private Double confidenceScore = 0.5;

    @Column(name = "is_flagged_for_review")
    private Boolean isFlaggedForReview = false;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Audit fields
    @Column(name = "created_by", length = 100)
    private String createdBy = "SYSTEM";

    @Column(name = "updated_by", length = 100)
    private String updatedBy = "SYSTEM";

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * Increment version for optimistic locking
     */
    public void incrementVersion() {
        if (this.version == null) {
            this.version = 1L;
        } else {
            this.version++;
        }
    }

    /**
     * Check if profile needs review based on risk threshold
     */
    public boolean needsReview() {
        return riskScore != null && riskScore >= 70.0 || 
               Boolean.TRUE.equals(isFlaggedForReview);
    }

    /**
     * Get profile age in days
     */
    public double getProfileAgeDays() {
        if (createdAt == null) {
            return 0.0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(createdAt, java.time.LocalDateTime.now());
    }

    /**
     * Get confidence score
     */
    public double getConfidenceScore() {
        return confidenceScore != null ? confidenceScore : 0.5;
    }

    /**
     * Update risk assessment
     */
    public void updateRiskAssessment(double newRiskScore, String newRiskLevel) {
        this.riskScore = newRiskScore;
        this.riskLevel = newRiskLevel;
        this.lastAnalysisTimestamp = LocalDateTime.now();
        
        // Auto-flag for review if high risk
        if (newRiskScore >= 80.0) {
            this.isFlaggedForReview = true;
        }
    }

    /**
     * Add or update ML feature
     */
    public void updateMLFeature(String featureName, Object featureValue) {
        if (this.mlFeatures == null) {
            this.mlFeatures = new HashMap<>();
        }
        this.mlFeatures.put(featureName, featureValue);
    }

    /**
     * Add or update behavioral pattern
     */
    public void updateBehavioralPattern(String patternName, Object patternValue) {
        if (this.behavioralPatterns == null) {
            this.behavioralPatterns = new HashMap<>();
        }
        this.behavioralPatterns.put(patternName, patternValue);
    }

    /**
     * Add or update risk factor
     */
    public void updateRiskFactor(String factorName, Double factorValue) {
        if (this.riskFactors == null) {
            this.riskFactors = new HashMap<>();
        }
        this.riskFactors.put(factorName, factorValue);
    }

    /**
     * Add or update compliance flag
     */
    public void updateComplianceFlag(String flagName, Boolean flagValue) {
        if (this.complianceFlags == null) {
            this.complianceFlags = new HashMap<>();
        }
        this.complianceFlags.put(flagName, flagValue);
    }

    /**
     * Soft delete the profile
     */
    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
        this.profileStatus = "DELETED";
    }

    /**
     * Check if profile is active
     */
    public boolean isActive() {
        return "ACTIVE".equals(profileStatus) && deletedAt == null;
    }

    /**
     * Get age of profile in days
     */
    public long getProfileAgeDays() {
        if (createdAt == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(createdAt, LocalDateTime.now());
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (lastUpdated == null) lastUpdated = LocalDateTime.now();
        if (totalTransactionCount == null) totalTransactionCount = 0L;
        if (riskScore == null) riskScore = 50.0;
        if (version == null) version = 1L;
    }

    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
        updatedBy = "SYSTEM"; // Could be set from security context
    }
}