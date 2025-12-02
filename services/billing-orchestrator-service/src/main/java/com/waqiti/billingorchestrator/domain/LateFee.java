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
 * Late Fee entity - represents late fees applied to overdue bills
 * Migrated from billing-service
 */
@Entity
@Table(name = "late_fees")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LateFee {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "bill_id", nullable = false)
    private UUID billId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "original_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "late_fee_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal lateFeeAmount;

    @Column(name = "days_overdue", nullable = false)
    private Integer daysOverdue;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (appliedAt == null) {
            appliedAt = LocalDateTime.now();
        }
    }
}
