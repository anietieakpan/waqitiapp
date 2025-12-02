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
 * Compliance Risk Profile Model
 * 
 * CRITICAL: Represents user compliance risk profiles for regulatory compliance.
 * Tracks risk scores, ratings, and compliance monitoring requirements.
 * 
 * RISK RATINGS:
 * - VERY_LOW (0-20): Minimal compliance monitoring
 * - LOW (20-40): Standard monitoring
 * - MEDIUM (40-60): Enhanced monitoring
 * - HIGH (60-80): Intensive monitoring + reporting
 * - VERY_HIGH (80-100): Maximum monitoring + immediate escalation
 * 
 * COMPLIANCE IMPACT:
 * - Supports BSA risk-based monitoring requirements
 * - Enables automated compliance decisions
 * - Maintains audit trail for regulatory exams
 * - Supports enhanced due diligence triggers
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Entity
@Table(name = "compliance_risk_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ComplianceRiskProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    // Risk scoring
    @Column(nullable = false)
    private Double currentRiskScore;

    private Double previousRiskScore;

    @Column(nullable = false)
    private String riskRating; // VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH

    // KYC and tier information
    private String kycTier;
    private String customerType; // INDIVIDUAL, BUSINESS, FINANCIAL_INSTITUTION

    // Geographic risk factors
    private String residenceCountry;
    private String citizenshipCountry;
    private String businessCountry;
    private boolean isHighRiskJurisdiction = false;

    // Customer profile risk factors
    private boolean isPoliticallyExposed = false;
    private boolean hasSanctionsMatch = false;
    private boolean hasAdverseMedia = false;
    private boolean isHighNetWorth = false;

    // Business relationship factors
    private String businessRelationshipType;
    private String sourceOfWealth;
    private String sourceOfFunds;
    private boolean hasComplexOwnershipStructure = false;

    // Transaction risk factors
    private boolean hasUnusualTransactionPatterns = false;
    private boolean hasHighVelocityTransactions = false;
    private boolean hasLargeCashTransactions = false;
    private boolean hasInternationalTransactions = false;

    // Monitoring requirements
    private boolean requiresEnhancedMonitoring = false;
    private boolean requiresPeriodicReview = false;
    private Integer reviewFrequencyDays;
    private boolean requiresManualApproval = false;

    // Assessment tracking
    private Integer assessmentCount = 0;
    private LocalDateTime lastAssessmentDate;
    private String lastUpdateReason;
    private UUID assessedBy;

    // Escalation tracking
    private boolean hasBeenEscalated = false;
    private LocalDateTime lastEscalationDate;
    private String escalationReason;

    // Risk score components (for transparency)
    private Double baseRiskScore;
    private Double geographicRiskAdjustment;
    private Double customerRiskAdjustment;
    private Double transactionRiskAdjustment;
    private Double behaviorRiskAdjustment;

    // Compliance flags
    private boolean requiresCtrMonitoring = false;
    private boolean requiresSarMonitoring = false;
    private boolean requiresEnhancedDueDiligence = false;
    private boolean exemptFromAutomatedDecisions = false;

    // Audit fields
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

    // Business logic methods

    public boolean isLowRisk() {
        return "VERY_LOW".equals(riskRating) || "LOW".equals(riskRating);
    }

    public boolean isMediumRisk() {
        return "MEDIUM".equals(riskRating);
    }

    public boolean isHighRisk() {
        return "HIGH".equals(riskRating) || "VERY_HIGH".equals(riskRating);
    }

    public boolean isVeryHighRisk() {
        return "VERY_HIGH".equals(riskRating);
    }

    public boolean needsEnhancedMonitoring() {
        return requiresEnhancedMonitoring || isHighRisk();
    }

    public boolean needsManualReview() {
        return requiresManualApproval || isVeryHighRisk() || hasBeenEscalated;
    }

    public boolean isDueForReview() {
        if (!requiresPeriodicReview || reviewFrequencyDays == null) {
            return false;
        }
        
        LocalDateTime nextReviewDate = lastAssessmentDate.plusDays(reviewFrequencyDays);
        return LocalDateTime.now().isAfter(nextReviewDate);
    }

    public boolean hasHighRiskFactors() {
        return isPoliticallyExposed || hasSanctionsMatch || hasAdverseMedia || 
               isHighRiskJurisdiction || hasComplexOwnershipStructure;
    }

    public boolean hasUnusualActivity() {
        return hasUnusualTransactionPatterns || hasHighVelocityTransactions || 
               hasLargeCashTransactions;
    }

    public int getReviewPriorityScore() {
        int priority = 0;
        
        if (isVeryHighRisk()) priority += 10;
        else if (isHighRisk()) priority += 7;
        else if (isMediumRisk()) priority += 5;
        
        if (hasHighRiskFactors()) priority += 5;
        if (hasUnusualActivity()) priority += 3;
        if (hasBeenEscalated) priority += 2;
        if (isDueForReview()) priority += 1;
        
        return priority;
    }

    public String getRiskSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Risk Rating: ").append(riskRating);
        summary.append(" (Score: ").append(currentRiskScore).append(")");
        
        if (hasHighRiskFactors()) {
            summary.append(" - High Risk Factors Present");
        }
        
        if (needsEnhancedMonitoring()) {
            summary.append(" - Enhanced Monitoring Required");
        }
        
        return summary.toString();
    }

    @PrePersist
    protected void onCreate() {
        if (currentRiskScore == null) {
            currentRiskScore = 25.0; // Default medium-low risk
        }
        if (riskRating == null) {
            riskRating = calculateRiskRating(currentRiskScore);
        }
        if (assessmentCount == null) {
            assessmentCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        
        // Auto-update risk rating based on score
        if (currentRiskScore != null) {
            riskRating = calculateRiskRating(currentRiskScore);
        }
    }

    private String calculateRiskRating(double score) {
        if (score >= 80.0) return "VERY_HIGH";
        if (score >= 60.0) return "HIGH";
        if (score >= 40.0) return "MEDIUM";
        if (score >= 20.0) return "LOW";
        return "VERY_LOW";
    }
}