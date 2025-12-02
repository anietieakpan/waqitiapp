/**
 * Traditional Loan Application Entity
 * Extends BNPL capabilities to support traditional loan products
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
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "loan_applications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplication {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Version field for optimistic locking
     * CRITICAL: Prevents lost updates in concurrent loan application processing
     * Loan applications can be updated by credit scoring, approval workflows, disbursement processes
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "loan_number", unique = true, nullable = false)
    private String loanNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false)
    private LoanType loanType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status;
    
    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(name = "disbursed_amount", precision = 19, scale = 4)
    private BigDecimal disbursedAmount;

    @Column(name = "outstanding_balance", precision = 19, scale = 4)
    private BigDecimal outstandingBalance;
    
    @Column(nullable = false)
    private String currency;
    
    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal interestRate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "interest_type", nullable = false)
    private InterestType interestType;
    
    @Column(name = "loan_term_months", nullable = false)
    private Integer loanTermMonths;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false)
    private RepaymentFrequency repaymentFrequency;
    
    @Column(name = "monthly_payment", precision = 19, scale = 4)
    private BigDecimal monthlyPayment;

    @Column(name = "total_interest", precision = 19, scale = 4)
    private BigDecimal totalInterest;

    @Column(name = "total_repayment", precision = 19, scale = 4)
    private BigDecimal totalRepayment;
    
    @Column(name = "application_date", nullable = false)
    private LocalDateTime applicationDate;
    
    @Column(name = "approval_date")
    private LocalDateTime approvalDate;
    
    @Column(name = "disbursement_date")
    private LocalDateTime disbursementDate;
    
    @Column(name = "first_payment_date")
    private LocalDate firstPaymentDate;
    
    @Column(name = "maturity_date")
    private LocalDate maturityDate;
    
    @Column(name = "credit_score")
    private Integer creditScore;
    
    @Column(name = "debt_to_income_ratio", precision = 5, scale = 4)
    private BigDecimal debtToIncomeRatio;
    
    @Column(name = "annual_income", precision = 19, scale = 4)
    private BigDecimal annualIncome;
    
    @Column(name = "employment_status")
    private String employmentStatus;
    
    @Column(name = "employment_duration_months")
    private Integer employmentDurationMonths;
    
    @Column(name = "purpose")
    private String purpose;
    
    @Type(JsonType.class)
    @Column(name = "collateral", columnDefinition = "jsonb")
    private JsonNode collateral;
    
    @Type(JsonType.class)
    @Column(name = "documents", columnDefinition = "jsonb")
    private JsonNode documents;
    
    @Type(JsonType.class)
    @Column(name = "risk_assessment", columnDefinition = "jsonb")
    private JsonNode riskAssessment;
    
    @Column(name = "risk_grade")
    private String riskGrade;
    
    @Column(name = "decision")
    private String decision;
    
    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;
    
    @Column(name = "decision_date")
    private LocalDateTime decisionDate;
    
    @Column(name = "decision_by")
    private String decisionBy;
    
    @Column(name = "loan_officer_id")
    private UUID loanOfficerId;
    
    @Column(name = "branch_id")
    private UUID branchId;
    
    @Column(name = "product_id")
    private UUID productId;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "loanApplication", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LoanInstallment> installments;
    
    @OneToMany(mappedBy = "loanApplication", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<LoanTransaction> transactions;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (applicationDate == null) {
            applicationDate = LocalDateTime.now();
        }
        if (status == null) {
            status = LoanStatus.PENDING;
        }
        if (currency == null) {
            currency = "USD";
        }
        if (outstandingBalance == null) {
            outstandingBalance = BigDecimal.ZERO;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Status helper methods
    public boolean isPending() {
        return status == LoanStatus.PENDING;
    }
    
    public boolean isApproved() {
        return status == LoanStatus.APPROVED;
    }
    
    public boolean isActive() {
        return status == LoanStatus.ACTIVE;
    }
    
    public boolean isCompleted() {
        return status == LoanStatus.COMPLETED;
    }
    
    public boolean isDefaulted() {
        return status == LoanStatus.DEFAULTED;
    }
    
    // Business logic methods
    public boolean isSecured() {
        return collateral != null && !collateral.isEmpty();
    }
    
    public BigDecimal getRemainingBalance() {
        return outstandingBalance != null ? outstandingBalance : BigDecimal.ZERO;
    }
    
    public int getDaysToMaturity() {
        if (maturityDate == null) return 0;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), maturityDate);
    }
    
    public enum LoanType {
        PERSONAL_LOAN,
        BUSINESS_LOAN,
        EDUCATION_LOAN,
        HOME_LOAN,
        AUTO_LOAN,
        AGRICULTURE_LOAN,
        MICROFINANCE,
        EMERGENCY_LOAN,
        PAYDAY_LOAN,
        CONSOLIDATION_LOAN
    }
    
    public enum LoanStatus {
        PENDING,           // Application submitted
        UNDER_REVIEW,      // Being reviewed by loan officer
        APPROVED,          // Approved but not disbursed
        REJECTED,          // Application rejected
        ACTIVE,            // Loan disbursed and active
        OVERDUE,           // Payment overdue
        DEFAULTED,         // In default
        COMPLETED,         // Fully repaid
        CANCELLED,         // Cancelled before disbursement
        WRITTEN_OFF        // Written off as bad debt
    }
    
    public enum InterestType {
        SIMPLE,            // Simple interest
        COMPOUND,          // Compound interest
        FLAT_RATE,         // Flat rate
        REDUCING_BALANCE,  // Reducing balance method
        FIXED,             // Fixed rate
        VARIABLE           // Variable rate
    }
    
    public enum RepaymentFrequency {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        QUARTERLY,
        SEMI_ANNUALLY,
        ANNUALLY
    }
}