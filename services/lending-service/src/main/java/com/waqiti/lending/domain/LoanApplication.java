package com.waqiti.lending.domain;

import com.waqiti.lending.domain.enums.ApplicationStatus;
import com.waqiti.lending.domain.enums.LoanType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Loan Application Entity
 * Represents a customer's application for a loan product
 */
@Entity
@Table(name = "loan_application", indexes = {
    @Index(name = "idx_loan_application_borrower", columnList = "borrower_id"),
    @Index(name = "idx_loan_application_status", columnList = "application_status"),
    @Index(name = "idx_loan_application_submitted", columnList = "submitted_at"),
    @Index(name = "idx_loan_application_id", columnList = "application_id", unique = true)
})
@SQLDelete(sql = "UPDATE loan_application SET deleted_at = NOW() WHERE id = ? AND version = ?")
@Where(clause = "deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanApplication extends BaseEntity {

    @Column(name = "application_id", nullable = false, unique = true, length = 100)
    @NotBlank
    private String applicationId;

    @Column(name = "borrower_id", nullable = false)
    @NotNull
    private UUID borrowerId;

    @Column(name = "co_borrower_id")
    private UUID coBorrowerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 50)
    @NotNull
    private LoanType loanType;

    @Column(name = "requested_amount", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin(value = "100.00", message = "Minimum loan amount is $100")
    @DecimalMax(value = "1000000.00", message = "Maximum loan amount is $1,000,000")
    private BigDecimal requestedAmount;

    @Column(name = "currency", nullable = false, length = 3)
    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code")
    @Builder.Default
    private String currency = "USD";

    @Column(name = "purpose", nullable = false, length = 100)
    @NotBlank
    private String purpose;

    @Column(name = "requested_term_months", nullable = false)
    @NotNull
    @Min(value = 1, message = "Minimum term is 1 month")
    @Max(value = 360, message = "Maximum term is 360 months")
    private Integer requestedTermMonths;

    @Column(name = "employment_status", length = 50)
    private String employmentStatus;

    @Column(name = "annual_income", precision = 15, scale = 2)
    @DecimalMin(value = "0.00", message = "Annual income cannot be negative")
    private BigDecimal annualIncome;

    @Column(name = "debt_to_income_ratio", precision = 5, scale = 4)
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal debtToIncomeRatio;

    @Column(name = "credit_score")
    @Min(value = 300)
    @Max(value = 850)
    private Integer creditScore;

    @Column(name = "collateral_type", length = 50)
    private String collateralType;

    @Column(name = "collateral_value", precision = 15, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal collateralValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_status", nullable = false, length = 20)
    @NotNull
    @Builder.Default
    private ApplicationStatus applicationStatus = ApplicationStatus.SUBMITTED;

    @Column(name = "submitted_at", nullable = false)
    @NotNull
    @Builder.Default
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "decision", length = 20)
    private String decision;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal approvedAmount;

    @Column(name = "approved_term_months")
    @Min(value = 1)
    @Max(value = 360)
    private Integer approvedTermMonths;

    @Column(name = "approved_interest_rate", precision = 5, scale = 4)
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal approvedInterestRate;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Business Logic Methods

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isApproved() {
        return applicationStatus == ApplicationStatus.APPROVED;
    }

    public boolean isRejected() {
        return applicationStatus == ApplicationStatus.REJECTED;
    }

    public boolean isPending() {
        return applicationStatus == ApplicationStatus.PENDING ||
               applicationStatus == ApplicationStatus.SUBMITTED;
    }

    public void approve(BigDecimal amount, Integer termMonths, BigDecimal interestRate, String reviewedBy) {
        this.applicationStatus = ApplicationStatus.APPROVED;
        this.approvedAmount = amount;
        this.approvedTermMonths = termMonths;
        this.approvedInterestRate = interestRate;
        this.reviewedAt = Instant.now();
        this.reviewedBy = reviewedBy;
        this.decision = "APPROVED";
    }

    public void reject(String reason, String reviewedBy) {
        this.applicationStatus = ApplicationStatus.REJECTED;
        this.decisionReason = reason;
        this.reviewedAt = Instant.now();
        this.reviewedBy = reviewedBy;
        this.decision = "REJECTED";
    }

    public void markForManualReview(String reason) {
        this.applicationStatus = ApplicationStatus.MANUAL_REVIEW;
        this.decisionReason = reason;
    }

    @Override
    public String toString() {
        return "LoanApplication{" +
                "applicationId='" + applicationId + '\'' +
                ", borrowerId=" + borrowerId +
                ", loanType=" + loanType +
                ", requestedAmount=" + requestedAmount +
                ", applicationStatus=" + applicationStatus +
                '}';
    }
}
