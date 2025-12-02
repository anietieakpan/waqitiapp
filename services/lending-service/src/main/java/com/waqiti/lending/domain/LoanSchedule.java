package com.waqiti.lending.domain;

import com.waqiti.lending.domain.enums.ScheduleStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Loan Payment Schedule Entity
 * Represents the amortization schedule for a loan
 */
@Entity
@Table(name = "loan_schedule",
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_loan_payment_number", columnNames = {"loan_id", "payment_number"})
    },
    indexes = {
        @Index(name = "idx_loan_schedule_loan", columnList = "loan_id"),
        @Index(name = "idx_loan_schedule_due_date", columnList = "due_date"),
        @Index(name = "idx_loan_schedule_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanSchedule extends BaseEntity {

    @Column(name = "loan_id", nullable = false, length = 100)
    @NotBlank
    private String loanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", referencedColumnName = "loan_id", insertable = false, updatable = false)
    private Loan loan;

    @Column(name = "payment_number", nullable = false)
    @NotNull
    @Min(value = 1)
    private Integer paymentNumber;

    @Column(name = "due_date", nullable = false)
    @NotNull
    private LocalDate dueDate;

    @Column(name = "scheduled_payment", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal scheduledPayment;

    @Column(name = "principal_amount", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal principalAmount;

    @Column(name = "interest_amount", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal interestAmount;

    @Column(name = "remaining_balance", nullable = false, precision = 15, scale = 2)
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal remainingBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @NotNull
    @Builder.Default
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "paid_amount", precision = 15, scale = 2)
    @DecimalMin(value = "0.00")
    private BigDecimal paidAmount;

    // Business Logic Methods

    public void markAsPaid(LocalDate paymentDate, BigDecimal amount) {
        this.status = ScheduleStatus.PAID;
        this.paidDate = paymentDate;
        this.paidAmount = amount;
    }

    public void markAsOverdue() {
        if (LocalDate.now().isAfter(dueDate)) {
            this.status = ScheduleStatus.OVERDUE;
        }
    }

    public boolean isOverdue() {
        return status == ScheduleStatus.OVERDUE ||
               (status == ScheduleStatus.SCHEDULED && LocalDate.now().isAfter(dueDate));
    }

    public boolean isPaid() {
        return status == ScheduleStatus.PAID;
    }

    @Override
    public String toString() {
        return "LoanSchedule{" +
                "loanId='" + loanId + '\'' +
                ", paymentNumber=" + paymentNumber +
                ", dueDate=" + dueDate +
                ", scheduledPayment=" + scheduledPayment +
                ", status=" + status +
                '}';
    }
}
