package com.waqiti.bnpl.domain;

import com.waqiti.bnpl.domain.enums.AssessmentStatus;
import com.waqiti.bnpl.domain.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Entity representing a credit assessment for BNPL eligibility
 */
@Entity
@Table(name = "credit_assessments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CreditAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bnpl_plan_id")
    private BnplPlan bnplPlan;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "assessment_id", unique = true, nullable = false)
    private String assessmentId;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "internal_score")
    private Integer internalScore;

    @Column(name = "risk_level")
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Column(name = "credit_limit", precision = 19, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "available_credit", precision = 19, scale = 2)
    private BigDecimal availableCredit;

    @Column(name = "utilized_credit", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal utilizedCredit = BigDecimal.ZERO;

    @Column(name = "monthly_income", precision = 19, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "monthly_obligations", precision = 19, scale = 2)
    private BigDecimal monthlyObligations;

    @Column(name = "debt_to_income_ratio", precision = 5, scale = 2)
    private BigDecimal debtToIncomeRatio;

    @Column(name = "payment_history_score")
    private Integer paymentHistoryScore;

    @Column(name = "account_age_months")
    private Integer accountAgeMonths;

    @Column(name = "total_transactions")
    private Integer totalTransactions;

    @Column(name = "successful_payments")
    private Integer successfulPayments;

    @Column(name = "failed_payments")
    private Integer failedPayments;

    @Column(name = "active_bnpl_plans")
    private Integer activeBnplPlans;

    @Column(name = "completed_bnpl_plans")
    private Integer completedBnplPlans;

    @Column(name = "defaulted_bnpl_plans")
    private Integer defaultedBnplPlans;

    @Column(name = "max_approved_amount", precision = 19, scale = 2)
    private BigDecimal maxApprovedAmount;

    @Column(name = "min_down_payment_percentage", precision = 5, scale = 2)
    private BigDecimal minDownPaymentPercentage;

    @Column(name = "max_installments")
    private Integer maxInstallments;

    @Column(name = "interest_rate", precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AssessmentStatus status;

    @Column(name = "decision")
    private String decision;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "assessment_data", columnDefinition = "TEXT")
    private String assessmentData;

    @Column(name = "external_check_performed")
    @Builder.Default
    private Boolean externalCheckPerformed = false;

    @Column(name = "external_check_provider")
    private String externalCheckProvider;

    @Column(name = "external_check_reference")
    private String externalCheckReference;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business methods

    public void approve(BigDecimal creditLimit, BigDecimal maxApprovedAmount) {
        this.status = AssessmentStatus.APPROVED;
        this.decision = "APPROVED";
        this.creditLimit = creditLimit;
        this.availableCredit = creditLimit.subtract(utilizedCredit);
        this.maxApprovedAmount = maxApprovedAmount;
        this.validUntil = LocalDateTime.now().plusMonths(6);
    }

    public void reject(String reason) {
        this.status = AssessmentStatus.REJECTED;
        this.decision = "REJECTED";
        this.decisionReason = reason;
        this.creditLimit = BigDecimal.ZERO;
        this.availableCredit = BigDecimal.ZERO;
        this.maxApprovedAmount = BigDecimal.ZERO;
    }

    public void updateCreditUtilization(BigDecimal amount) {
        this.utilizedCredit = this.utilizedCredit.add(amount);
        this.availableCredit = this.creditLimit.subtract(this.utilizedCredit);
    }

    public void releaseCreditUtilization(BigDecimal amount) {
        this.utilizedCredit = this.utilizedCredit.subtract(amount);
        if (this.utilizedCredit.compareTo(BigDecimal.ZERO) < 0) {
            this.utilizedCredit = BigDecimal.ZERO;
        }
        this.availableCredit = this.creditLimit.subtract(this.utilizedCredit);
    }

    public boolean isValid() {
        return this.status == AssessmentStatus.APPROVED && 
               this.validUntil != null && 
               LocalDateTime.now().isBefore(this.validUntil);
    }

    public boolean hasAvailableCredit(BigDecimal requestedAmount) {
        return this.availableCredit != null && 
               this.availableCredit.compareTo(requestedAmount) >= 0;
    }

    public BigDecimal calculateDebtToIncomeRatio() {
        if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return monthlyObligations.divide(monthlyIncome, 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    public void calculateRiskLevel() {
        if (creditScore == null || internalScore == null) {
            this.riskLevel = RiskLevel.HIGH;
            return;
        }

        int combinedScore = (creditScore + internalScore) / 2;
        
        if (combinedScore >= 750) {
            this.riskLevel = RiskLevel.LOW;
        } else if (combinedScore >= 650) {
            this.riskLevel = RiskLevel.MEDIUM;
        } else {
            this.riskLevel = RiskLevel.HIGH;
        }
    }
}