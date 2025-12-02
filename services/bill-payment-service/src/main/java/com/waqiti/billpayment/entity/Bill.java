package com.waqiti.billpayment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a bill from an external biller (utility company, service provider, etc.)
 * This is the core entity representing bills that users can pay through the platform.
 */
@Entity
@Table(name = "bills", indexes = {
        @Index(name = "idx_bill_user_id", columnList = "user_id"),
        @Index(name = "idx_bill_biller_id", columnList = "biller_id"),
        @Index(name = "idx_bill_account_number", columnList = "account_number"),
        @Index(name = "idx_bill_due_date", columnList = "due_date"),
        @Index(name = "idx_bill_status", columnList = "status"),
        @Index(name = "idx_bill_user_status", columnList = "user_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "biller_id", nullable = false)
    private UUID billerId;

    @Column(name = "biller_name", nullable = false, length = 200)
    private String billerName;

    @Column(name = "account_number", nullable = false, length = 100)
    private String accountNumber;

    @Column(name = "bill_number", length = 100)
    private String billNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private BillCategory category;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "bill_date")
    private LocalDate billDate;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BillStatus status;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "external_bill_id", length = 100)
    private String externalBillId;

    @Column(name = "pdf_url", length = 500)
    private String pdfUrl;

    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring = false;

    @Column(name = "auto_pay_enabled", nullable = false)
    private Boolean autoPayEnabled = false;

    @Column(name = "minimum_amount_due", precision = 19, scale = 4)
    private BigDecimal minimumAmountDue;

    @Column(name = "late_fee", precision = 19, scale = 4)
    private BigDecimal lateFee;

    @Column(name = "paid_amount", precision = 19, scale = 4)
    private BigDecimal paidAmount;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "last_payment_id")
    private UUID lastPaymentId;

    @Column(name = "reminder_sent", nullable = false)
    private Boolean reminderSent = false;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @Column(name = "overdue_alert_sent", nullable = false)
    private Boolean overdueAlertSent = false;

    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    /**
     * Version field for optimistic locking
     * Prevents concurrent bill updates (e.g., simultaneous payments)
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    // Business logic methods

    public boolean isOverdue() {
        return status == BillStatus.UNPAID && LocalDate.now().isAfter(dueDate);
    }

    public boolean isDueSoon() {
        LocalDate threeDaysFromNow = LocalDate.now().plusDays(3);
        return status == BillStatus.UNPAID && dueDate.isBefore(threeDaysFromNow);
    }

    public BigDecimal getRemainingAmount() {
        if (paidAmount == null) {
            return amount;
        }
        return amount.subtract(paidAmount);
    }

    public boolean isFullyPaid() {
        return paidAmount != null && paidAmount.compareTo(amount) >= 0;
    }

    public void markAsPaid(BigDecimal paymentAmount, UUID paymentId) {
        this.paidAmount = (this.paidAmount == null ? BigDecimal.ZERO : this.paidAmount).add(paymentAmount);
        this.lastPaymentId = paymentId;

        if (isFullyPaid()) {
            this.status = BillStatus.PAID;
            this.paidDate = LocalDate.now();
        } else {
            this.status = BillStatus.PARTIALLY_PAID;
        }
    }

    public void softDelete(String deletedBy) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
        this.status = BillStatus.CANCELLED;
    }

    @PrePersist
    protected void onCreate() {
        if (currency == null) {
            currency = "USD";
        }
        if (isRecurring == null) {
            isRecurring = false;
        }
        if (autoPayEnabled == null) {
            autoPayEnabled = false;
        }
        if (reminderSent == null) {
            reminderSent = false;
        }
        if (overdueAlertSent == null) {
            overdueAlertSent = false;
        }
    }
}
