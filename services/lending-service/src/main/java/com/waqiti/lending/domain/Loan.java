package com.waqiti.lending.domain;

import com.waqiti.lending.domain.enums.InterestType;
import com.waqiti.lending.domain.enums.LoanStatus;
import com.waqiti.lending.domain.enums.LoanType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Loan Entity
 * Represents an active loan account
 */
@Entity
@Table(name = "loan", indexes = {
    @Index(name = "idx_loan_borrower", columnList = "borrower_id"),
    @Index(name = "idx_loan_status", columnList = "loan_status"),
    @Index(name = "idx_loan_next_payment", columnList = "next_payment_due_date"),
    @Index(name = "idx_loan_maturity", columnList = "maturity_date"),
    @Index(name = "idx_loan_past_due", columnList = "days_past_due"),
    @Index(name = "idx_loan_id", columnList = "loan_id", unique = true)
})
@SQLDelete(sql = "UPDATE loan SET deleted_at = NOW() WHERE id = ? AND version = ?")
@Where(clause = "deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Loan extends BaseEntity {

    @Column(name = "loan_id", nullable = false, unique = true, length = 100)
    @NotBlank
    private String loanId;

    @Column(name = "application_id", length = 100)
    private String applicationId;

    @Column(name = "borrower_id", nullable = false)
    @NotNull
    private UUID borrowerId;

    @Column(name = "co_borrower_id")
    private UUID coBorrowerId;

    @Column(name = "lender_id", length = 100)
    private String lenderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 50)
    @NotNull
    private LoanType loanType;

    @Column(name = "principal_amount", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal principalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    @NotBlank
    @Builder.Default
    private String currency = "USD";

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 4)
    @NotNull
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal interestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_type", nullable = false, length = 20)
    @NotNull
    @Builder.Default
    private InterestType interestType = InterestType.FIXED;

    @Column(name = "term_months", nullable = false)
    @NotNull
    @Min(value = 1)
    @Max(value = 360)
    private Integer termMonths;

    @Column(name = "monthly_payment", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal monthlyPayment;

    @Column(name = "origination_fee", precision = 10, scale = 2)
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal originationFee = BigDecimal.ZERO;

    @Column(name = "outstanding_balance", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal outstandingBalance;

    @Column(name = "interest_accrued", precision = 15, scale = 2)
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal interestAccrued = BigDecimal.ZERO;

    @Column(name = "total_paid", precision = 15, scale = 2)
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "next_payment_due_date")
    private LocalDate nextPaymentDueDate;

    @Column(name = "maturity_date", nullable = false)
    @NotNull
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_status", nullable = false, length = 20)
    @NotNull
    @Builder.Default
    private LoanStatus loanStatus = LoanStatus.PENDING;

    @Column(name = "disbursed_at")
    private Instant disbursedAt;

    @Column(name = "disbursement_method", length = 50)
    private String disbursementMethod;

    @Column(name = "first_payment_date")
    private LocalDate firstPaymentDate;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    @Column(name = "days_past_due")
    @Min(value = 0)
    @Builder.Default
    private Integer daysPastDue = 0;

    @Column(name = "default_date")
    private Instant defaultDate;

    @Column(name = "charged_off_date")
    private Instant chargedOffDate;

    @Column(name = "paid_off_date")
    private Instant paidOffDate;

    @Column(name = "collateral_id")
    private UUID collateralId;

    @Column(name = "risk_rating", length = 20)
    private String riskRating;

    @Column(name = "credit_score_at_origination")
    @Min(value = 300)
    @Max(value = 850)
    private Integer creditScoreAtOrigination;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Relationships
    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LoanPayment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "loan", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LoanSchedule> schedules = new ArrayList<>();

    // Business Logic Methods

    public void disburse(Instant disbursementTime, String method) {
        this.loanStatus = LoanStatus.ACTIVE;
        this.disbursedAt = disbursementTime;
        this.disbursementMethod = method;
        this.outstandingBalance = this.principalAmount;
    }

    public void applyPayment(BigDecimal amount, BigDecimal principalPortion, BigDecimal interestPortion) {
        this.outstandingBalance = this.outstandingBalance.subtract(principalPortion);
        this.totalPaid = this.totalPaid.add(amount);
        this.lastPaymentDate = LocalDate.now();

        if (this.outstandingBalance.compareTo(BigDecimal.ZERO) == 0) {
            this.loanStatus = LoanStatus.PAID_OFF;
            this.paidOffDate = Instant.now();
        }
    }

    public void markDelinquent(int daysPastDue) {
        this.daysPastDue = daysPastDue;
        if (daysPastDue >= 90) {
            this.loanStatus = LoanStatus.DEFAULT;
            this.defaultDate = Instant.now();
        } else if (daysPastDue > 0) {
            this.loanStatus = LoanStatus.DELINQUENT;
        }
    }

    public void chargeOff() {
        this.loanStatus = LoanStatus.CHARGED_OFF;
        this.chargedOffDate = Instant.now();
    }

    public BigDecimal calculateTotalAmountDue() {
        return outstandingBalance.add(interestAccrued);
    }

    public BigDecimal calculateMonthlyPayment() {
        // Standard amortization formula: M = P * [r(1+r)^n] / [(1+r)^n - 1]
        if (interestRate.compareTo(BigDecimal.ZERO) == 0) {
            return principalAmount.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        }

        BigDecimal monthlyRate = interestRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);

        // (1+r)^n
        BigDecimal onePlusRPowN = onePlusR.pow(termMonths);

        // r(1+r)^n
        BigDecimal numerator = monthlyRate.multiply(onePlusRPowN);

        // (1+r)^n - 1
        BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE);

        // M = P * [numerator / denominator]
        BigDecimal payment = principalAmount.multiply(numerator.divide(denominator, 10, RoundingMode.HALF_UP));

        return payment.setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isDelinquent() {
        return loanStatus == LoanStatus.DELINQUENT || loanStatus == LoanStatus.DEFAULT;
    }

    public boolean isActive() {
        return loanStatus == LoanStatus.ACTIVE || loanStatus == LoanStatus.CURRENT;
    }

    public boolean isPaidOff() {
        return loanStatus == LoanStatus.PAID_OFF;
    }

    public boolean isInDefault() {
        return loanStatus == LoanStatus.DEFAULT;
    }

    @Override
    public String toString() {
        return "Loan{" +
                "loanId='" + loanId + '\'' +
                ", borrowerId=" + borrowerId +
                ", principalAmount=" + principalAmount +
                ", outstandingBalance=" + outstandingBalance +
                ", loanStatus=" + loanStatus +
                '}';
    }
}
