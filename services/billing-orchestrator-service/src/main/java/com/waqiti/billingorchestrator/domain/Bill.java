package com.waqiti.billingorchestrator.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bill entity - represents a bill that needs to be paid
 * Migrated from billing-service
 */
@Entity
@Table(name = "bills")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "bill_id", unique = true, nullable = false)
    private UUID billId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "bill_type")
    private String billType;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency = "USD";

    @Column(name = "late_fee", precision = 18, scale = 2)
    private BigDecimal lateFee;

    @Column(name = "total_amount_due", precision = 18, scale = 2)
    private BigDecimal totalAmountDue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BillStatus status = BillStatus.UNPAID;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "paid_date")
    private LocalDateTime paidDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (billId == null) {
            billId = UUID.randomUUID();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
