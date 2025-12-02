package com.waqiti.compliance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * KYC Tier Model
 * 
 * CRITICAL: Represents user KYC tiers for compliance and risk management.
 * Tracks tier progression and upgrade history for regulatory compliance.
 * 
 * TIER LEVELS:
 * - BASIC: Limited transaction amounts, basic verification
 * - STANDARD: Higher limits, enhanced verification
 * - PREMIUM: Professional limits, comprehensive verification
 * - VIP: Maximum limits, complete verification + enhanced monitoring
 * 
 * COMPLIANCE IMPACT:
 * - Supports BSA compliance requirements
 * - Enables risk-based customer classification
 * - Maintains audit trail for regulatory exams
 * - Supports enhanced due diligence requirements
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Entity
@Table(name = "kyc_tiers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class KycTier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private String currentTier;

    private String previousTier;

    @Column(columnDefinition = "TEXT")
    private String upgradeReason;

    @Column(nullable = false)
    private LocalDateTime effectiveDate;

    private LocalDateTime tierExpiry;

    // Verification status
    private boolean documentVerified = false;
    private boolean identityVerified = false;
    private boolean addressVerified = false;
    private boolean incomeVerified = false;
    private boolean sourceOfFundsVerified = false;

    // Enhanced verification (for higher tiers)
    private boolean enhancedDueDiligenceCompleted = false;
    private boolean beneficialOwnershipVerified = false;
    private boolean pepScreeningCompleted = false;
    private boolean sanctionsScreeningCompleted = false;

    // Risk assessment
    private String riskRating; // LOW, MEDIUM, HIGH, VERY_HIGH
    private Double riskScore;
    private LocalDateTime lastRiskAssessment;

    // Compliance flags
    private boolean requiresEnhancedMonitoring = false;
    private boolean requiresPeriodic Review = false;
    private Integer reviewFrequencyDays;

    // Audit fields
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

    // Business logic methods

    public boolean isBasicTier() {
        return "BASIC".equals(currentTier);
    }

    public boolean isStandardTier() {
        return "STANDARD".equals(currentTier);
    }

    public boolean isPremiumTier() {
        return "PREMIUM".equals(currentTier);
    }

    public boolean isVipTier() {
        return "VIP".equals(currentTier);
    }

    public boolean isFullyVerified() {
        return documentVerified && identityVerified && addressVerified;
    }

    public boolean isEnhancedVerified() {
        return isFullyVerified() && incomeVerified && sourceOfFundsVerified;
    }

    public boolean isHighestTier() {
        return isVipTier();
    }

    public boolean canUpgradeToStandard() {
        return isBasicTier() && isFullyVerified();
    }

    public boolean canUpgradeToPremium() {
        return (isBasicTier() || isStandardTier()) && isEnhancedVerified();
    }

    public boolean canUpgradeToVip() {
        return isPremiumTier() && enhancedDueDiligenceCompleted && 
               beneficialOwnershipVerified && pepScreeningCompleted && 
               sanctionsScreeningCompleted;
    }

    public boolean requiresReview() {
        if (!requiresPeriodic Review || reviewFrequencyDays == null) {
            return false;
        }
        
        LocalDateTime nextReviewDate = updatedAt.plusDays(reviewFrequencyDays);
        return LocalDateTime.now().isAfter(nextReviewDate);
    }

    public boolean isTierExpired() {
        return tierExpiry != null && LocalDateTime.now().isAfter(tierExpiry);
    }

    @PrePersist
    protected void onCreate() {
        if (currentTier == null) {
            currentTier = "BASIC";
        }
        if (effectiveDate == null) {
            effectiveDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}