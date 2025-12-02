/**
 * Loan Installment Entity
 * Represents individual repayment installments for traditional loans
 */
package com.waqiti.bnpl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "loan_installments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanInstallment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Version field for optimistic locking
     * CRITICAL: Prevents lost updates when processing installment payments
     * Multiple payment systems may attempt to update the same installment simultaneously
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_application_id", nullable = false)
    private LoanApplication loanApplication;
    
    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;
    
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    
    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "interest_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal interestAmount;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", precision = 19, scale = 4)
    private BigDecimal paidAmount;

    @Column(name = "outstanding_amount", precision = 19, scale = 4)
    private BigDecimal outstandingAmount;

    @Column(name = "penalty_amount", precision = 19, scale = 4)
    private BigDecimal penaltyAmount;

    @Column(name = "late_fee", precision = 19, scale = 4)
    private BigDecimal lateFee;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstallmentStatus status;
    
    @Column(name = "payment_date")
    private LocalDateTime paymentDate;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    @Column(name = "payment_reference")
    private String paymentReference;
    
    @Column(name = "days_overdue")
    private Integer daysOverdue;
    
    @Column(name = "grace_period_days")
    private Integer gracePeriodDays;
    
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
        if (status == null) {
            status = InstallmentStatus.PENDING;
        }
        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO;
        }
        if (outstandingAmount == null) {
            outstandingAmount = totalAmount;
        }
        if (penaltyAmount == null) {
            penaltyAmount = BigDecimal.ZERO;
        }
        if (lateFee == null) {
            lateFee = BigDecimal.ZERO;
        }
        if (gracePeriodDays == null) {
            gracePeriodDays = 0;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        
        // Calculate days overdue
        if (status == InstallmentStatus.OVERDUE || status == InstallmentStatus.PARTIALLY_PAID) {
            LocalDate currentDate = LocalDate.now();
            LocalDate gracePeriodEnd = dueDate.plusDays(gracePeriodDays);
            if (currentDate.isAfter(gracePeriodEnd)) {
                daysOverdue = (int) java.time.temporal.ChronoUnit.DAYS.between(gracePeriodEnd, currentDate);
            } else {
                daysOverdue = 0;
            }
        }
        
        // Update outstanding amount
        if (paidAmount != null && totalAmount != null) {
            outstandingAmount = totalAmount.subtract(paidAmount).add(penaltyAmount != null ? penaltyAmount : BigDecimal.ZERO);
            if (outstandingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                outstandingAmount = BigDecimal.ZERO;
                if (status != InstallmentStatus.PAID) {
                    status = InstallmentStatus.PAID;
                    paymentDate = LocalDateTime.now();
                }
            }
        }
    }
    
    // Business logic methods
    public boolean isPaid() {
        return status == InstallmentStatus.PAID;
    }
    
    public boolean isOverdue() {
        return status == InstallmentStatus.OVERDUE;
    }
    
    public boolean isPending() {
        return status == InstallmentStatus.PENDING;
    }
    
    public boolean isPartiallyPaid() {
        return status == InstallmentStatus.PARTIALLY_PAID;
    }
    
    public BigDecimal getRemainingAmount() {
        return outstandingAmount != null ? outstandingAmount : totalAmount;
    }
    
    public boolean isDueToday() {
        return dueDate.equals(LocalDate.now());
    }
    
    public boolean isPastDue() {
        return LocalDate.now().isAfter(dueDate);
    }
    
    public boolean isInGracePeriod() {
        if (!isPastDue()) return false;
        LocalDate gracePeriodEnd = dueDate.plusDays(gracePeriodDays);
        return LocalDate.now().isBefore(gracePeriodEnd) || LocalDate.now().equals(gracePeriodEnd);
    }
    
    public int getDaysPastDue() {
        if (!isPastDue()) return 0;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(dueDate, LocalDate.now());
    }
    
    public enum InstallmentStatus {
        PENDING,           // Not yet due
        DUE,              // Due today
        OVERDUE,          // Past due date
        PARTIALLY_PAID,   // Partial payment received
        PAID,             // Fully paid
        WAIVED,           // Waived by admin
        WRITTEN_OFF       // Written off
    }
}