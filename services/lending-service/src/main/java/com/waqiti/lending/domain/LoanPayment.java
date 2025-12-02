package com.waqiti.lending.domain;

import com.waqiti.lending.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Loan Payment Entity
 * Represents a payment made against a loan
 */
@Entity
@Table(name = "loan_payment", indexes = {
    @Index(name = "idx_loan_payment_loan", columnList = "loan_id"),
    @Index(name = "idx_loan_payment_borrower", columnList = "borrower_id"),
    @Index(name = "idx_loan_payment_date", columnList = "payment_date"),
    @Index(name = "idx_loan_payment_id", columnList = "payment_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanPayment extends BaseEntity {

    @Column(name = "payment_id", nullable = false, unique = true, length = 100)
    @NotBlank
    private String paymentId;

    @Column(name = "loan_id", nullable = false, length = 100)
    @NotBlank
    private String loanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", referencedColumnName = "loan_id", insertable = false, updatable = false)
    private Loan loan;

    @Column(name = "borrower_id", nullable = false)
    @NotNull
    private UUID borrowerId;

    @Column(name = "payment_date", nullable = false)
    @NotNull
    @Builder.Default
    private Instant paymentDate = Instant.now();

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "payment_amount", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal paymentAmount;

    @Column(name = "principal_amount", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal principalAmount;

    @Column(name = "interest_amount", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal interestAmount;

    @Column(name = "late_fee", precision = 10, scale = 2)
    @DecimalMin(value = "0.00")
    @Builder.Default
    private BigDecimal lateFee = BigDecimal.ZERO;

    @Column(name = "payment_method", nullable = false, length = 50)
    @NotBlank
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @NotNull
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.COMPLETED;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "is_autopay")
    @Builder.Default
    private Boolean isAutopay = false;

    @Column(name = "is_extra_payment")
    @Builder.Default
    private Boolean isExtraPayment = false;

    @Column(name = "confirmation_number", length = 100)
    private String confirmationNumber;

    // Business Logic Methods

    public boolean isLate() {
        return dueDate != null && paymentDate.isAfter(dueDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
    }

    public boolean hasLateFee() {
        return lateFee != null && lateFee.compareTo(BigDecimal.ZERO) > 0;
    }

    public void markAsCompleted(String confirmationNumber) {
        this.paymentStatus = PaymentStatus.COMPLETED;
        this.confirmationNumber = confirmationNumber;
    }

    public void markAsFailed(String reason) {
        this.paymentStatus = PaymentStatus.FAILED;
        // Store failure reason in a separate field if needed
    }

    @Override
    public String toString() {
        return "LoanPayment{" +
                "paymentId='" + paymentId + '\'' +
                ", loanId='" + loanId + '\'' +
                ", paymentAmount=" + paymentAmount +
                ", paymentDate=" + paymentDate +
                ", paymentStatus=" + paymentStatus +
                '}';
    }
}
