package com.waqiti.bnpl.domain;

import com.waqiti.bnpl.domain.enums.InstallmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing an installment in a BNPL plan
 */
@Entity
@Table(name = "bnpl_installments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BnplInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bnpl_plan_id", nullable = false)
    private BnplPlan bnplPlan;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_amount", precision = 19, scale = 2)
    private BigDecimal interestAmount;

    @Column(name = "late_fee", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal lateFee = BigDecimal.ZERO;

    @Column(name = "amount_paid", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "amount_due", precision = 19, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private InstallmentStatus status;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "reminder_sent")
    @Builder.Default
    private Boolean reminderSent = false;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @Column(name = "overdue_notification_sent")
    @Builder.Default
    private Boolean overdueNotificationSent = false;

    @Column(name = "overdue_notification_sent_at")
    private LocalDateTime overdueNotificationSentAt;

    @OneToMany(mappedBy = "installment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BnplTransaction> transactions = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business methods

    public void markAsPaid(BigDecimal amount, String paymentReference) {
        if (isPaid()) {
            throw new IllegalStateException("Installment is already paid");
        }
        
        this.amountPaid = this.amountPaid.add(amount);
        this.amountDue = this.amount.add(this.lateFee).subtract(this.amountPaid);
        
        if (this.amountDue.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = InstallmentStatus.PAID;
            this.paidDate = LocalDate.now();
            this.paymentReference = paymentReference;
            this.amountDue = BigDecimal.ZERO;
        } else {
            this.status = InstallmentStatus.PARTIALLY_PAID;
        }
    }

    public void addLateFee(BigDecimal fee) {
        this.lateFee = this.lateFee.add(fee);
        this.amountDue = this.amountDue.add(fee);
    }

    public boolean isOverdue() {
        return !isPaid() && LocalDate.now().isAfter(dueDate);
    }

    public boolean isPaid() {
        return status == InstallmentStatus.PAID;
    }

    public boolean isPartiallyPaid() {
        return status == InstallmentStatus.PARTIALLY_PAID;
    }

    public boolean isDueSoon(int daysBefore) {
        LocalDate warningDate = dueDate.minusDays(daysBefore);
        LocalDate today = LocalDate.now();
        return !isPaid() && (today.isEqual(warningDate) || 
                (today.isAfter(warningDate) && today.isBefore(dueDate)));
    }

    public long getDaysOverdue() {
        if (!isOverdue()) {
            return 0;
        }
        return LocalDate.now().toEpochDay() - dueDate.toEpochDay();
    }

    public long getDaysUntilDue() {
        if (isPaid() || isOverdue()) {
            return 0;
        }
        return dueDate.toEpochDay() - LocalDate.now().toEpochDay();
    }

    public BigDecimal getRemainingAmount() {
        return amountDue;
    }

    public BigDecimal getTotalAmount() {
        return amount.add(lateFee);
    }

    @PrePersist
    @PreUpdate
    public void calculateAmountDue() {
        if (amountDue == null) {
            amountDue = amount.add(lateFee != null ? lateFee : BigDecimal.ZERO)
                    .subtract(amountPaid != null ? amountPaid : BigDecimal.ZERO);
        }
    }
}