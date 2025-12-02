/**
 * BNPL Installment Entity
 * Represents an individual installment payment
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
@Table(name = "bnpl_installments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnplInstallment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Version field for optimistic locking
     * CRITICAL: Prevents lost updates during concurrent installment payment processing
     * BNPL installments may be updated by auto-debit, manual payment, retry logic simultaneously
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private BnplApplication application;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "interest_amount", precision = 19, scale = 4)
    private BigDecimal interestAmount;

    @Column(name = "fee_amount", precision = 19, scale = 4)
    private BigDecimal feeAmount;
    
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InstallmentStatus status;
    
    @Column(name = "payment_date")
    private LocalDateTime paymentDate;
    
    @Column(name = "payment_amount", precision = 19, scale = 4)
    private BigDecimal paymentAmount;
    
    @Column(name = "payment_method")
    private String paymentMethod;
    
    @Column(name = "payment_reference")
    private String paymentReference;
    
    @Column(name = "transaction_id")
    private UUID transactionId;
    
    @Column(name = "days_late")
    private Integer daysLate;
    
    @Column(name = "late_fee_amount", precision = 19, scale = 4)
    private BigDecimal lateFeeAmount;
    
    @Column(name = "late_fee_applied_date")
    private LocalDateTime lateFeeAppliedDate;
    
    @Column(name = "retry_count")
    private Integer retryCount;
    
    @Column(name = "next_retry_date")
    private LocalDateTime nextRetryDate;
    
    @Column(name = "last_retry_date")
    private LocalDateTime lastRetryDate;
    
    @Column(name = "collection_status")
    private String collectionStatus;
    
    @Column(name = "collection_assigned_date")
    private LocalDateTime collectionAssignedDate;
    
    @Column(name = "collection_notes", columnDefinition = "TEXT")
    private String collectionNotes;
    
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
        if (interestAmount == null) {
            interestAmount = BigDecimal.ZERO;
        }
        if (feeAmount == null) {
            feeAmount = BigDecimal.ZERO;
        }
        if (daysLate == null) {
            daysLate = 0;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (lateFeeAmount == null) {
            lateFeeAmount = BigDecimal.ZERO;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
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
    
    public boolean isInCollections() {
        return collectionStatus != null && !collectionStatus.isEmpty();
    }
    
    public BigDecimal getRemainingAmount() {
        if (paymentAmount == null) {
            return amount.add(lateFeeAmount != null ? lateFeeAmount : BigDecimal.ZERO);
        }
        return amount.add(lateFeeAmount != null ? lateFeeAmount : BigDecimal.ZERO).subtract(paymentAmount);
    }
    
    public enum InstallmentStatus {
        PENDING,
        PAID,
        OVERDUE,
        PARTIALLY_PAID,
        FAILED,
        CANCELLED,
        IN_COLLECTIONS,
        WRITTEN_OFF
    }
}