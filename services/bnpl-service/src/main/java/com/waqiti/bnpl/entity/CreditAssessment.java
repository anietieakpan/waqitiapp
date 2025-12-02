/**
 * Credit Assessment Entity
 * Represents credit scoring and risk assessment data
 */
package com.waqiti.bnpl.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "credit_assessments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditAssessment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "application_id")
    private UUID applicationId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false)
    private AssessmentType assessmentType;
    
    @Column(name = "assessment_date", nullable = false)
    private LocalDateTime assessmentDate;
    
    @Column(name = "credit_score")
    private Integer creditScore;
    
    @Column(name = "credit_score_source")
    private String creditScoreSource;
    
    @Column(name = "score_date")
    private LocalDate scoreDate;
    
    @Column(name = "score_version")
    private String scoreVersion;
    
    @Column(name = "monthly_income", precision = 19, scale = 4)
    private BigDecimal monthlyIncome;
    
    @Column(name = "income_source")
    private String incomeSource;
    
    @Column(name = "income_verified")
    private Boolean incomeVerified;
    
    @Column(name = "employment_status")
    private String employmentStatus;
    
    @Column(name = "employment_length_months")
    private Integer employmentLengthMonths;
    
    @Column(name = "bank_balance", precision = 19, scale = 4)
    private BigDecimal bankBalance;

    @Column(name = "average_monthly_balance", precision = 19, scale = 4)
    private BigDecimal averageMonthlyBalance;
    
    @Column(name = "transaction_volume_monthly")
    private Integer transactionVolumeMonthly;
    
    @Column(name = "overdraft_incidents_6m")
    private Integer overdraftIncidents6m;
    
    @Column(name = "total_debt", precision = 19, scale = 4)
    private BigDecimal totalDebt;

    @Column(name = "monthly_debt_payments", precision = 19, scale = 4)
    private BigDecimal monthlyDebtPayments;
    
    @Column(name = "debt_to_income_ratio", precision = 5, scale = 4)
    private BigDecimal debtToIncomeRatio;
    
    @Column(name = "credit_utilization_ratio", precision = 5, scale = 4)
    private BigDecimal creditUtilizationRatio;
    
    @Column(name = "on_time_payments_percentage", precision = 5, scale = 2)
    private BigDecimal onTimePaymentsPercentage;
    
    @Column(name = "late_payments_30d")
    private Integer latePayments30d;
    
    @Column(name = "late_payments_60d")
    private Integer latePayments60d;
    
    @Column(name = "late_payments_90d")
    private Integer latePayments90d;
    
    @Column(name = "charge_offs")
    private Integer chargeOffs;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_tier")
    private RiskTier riskTier;
    
    @Column(name = "risk_score")
    private Integer riskScore;
    
    @Type(JsonType.class)
    @Column(name = "risk_factors", columnDefinition = "jsonb")
    private JsonNode riskFactors;
    
    @Column(name = "social_score")
    private Integer socialScore;
    
    @Column(name = "digital_footprint_score")
    private Integer digitalFootprintScore;
    
    @Column(name = "behavioral_score")
    private Integer behavioralScore;
    
    @Column(name = "recommended_limit", precision = 19, scale = 4)
    private BigDecimal recommendedLimit;
    
    @Type(JsonType.class)
    @Column(name = "recommended_terms", columnDefinition = "jsonb")
    private JsonNode recommendedTerms;
    
    @Column(name = "assessment_notes", columnDefinition = "TEXT")
    private String assessmentNotes;
    
    @Column(name = "valid_until")
    private LocalDateTime validUntil;
    
    @Column(name = "superseded_by")
    private UUID supersededBy;
    
    @Column(name = "is_active")
    private Boolean isActive;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (assessmentDate == null) {
            assessmentDate = LocalDateTime.now();
        }
        if (isActive == null) {
            isActive = true;
        }
        if (incomeVerified == null) {
            incomeVerified = false;
        }
        if (overdraftIncidents6m == null) {
            overdraftIncidents6m = 0;
        }
        if (totalDebt == null) {
            totalDebt = BigDecimal.ZERO;
        }
        if (monthlyDebtPayments == null) {
            monthlyDebtPayments = BigDecimal.ZERO;
        }
        if (latePayments30d == null) {
            latePayments30d = 0;
        }
        if (latePayments60d == null) {
            latePayments60d = 0;
        }
        if (latePayments90d == null) {
            latePayments90d = 0;
        }
        if (chargeOffs == null) {
            chargeOffs = 0;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public boolean isExpired() {
        return validUntil != null && validUntil.isBefore(LocalDateTime.now());
    }
    
    public boolean isLowRisk() {
        return riskTier == RiskTier.LOW;
    }
    
    public boolean isMediumRisk() {
        return riskTier == RiskTier.MEDIUM;
    }
    
    public boolean isHighRisk() {
        return riskTier == RiskTier.HIGH || riskTier == RiskTier.VERY_HIGH;
    }
    
    public enum AssessmentType {
        INITIAL,
        PERIODIC,
        AD_HOC,
        CREDIT_INCREASE,
        DELINQUENCY_REVIEW
    }
    
    public enum RiskTier {
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH
    }
}