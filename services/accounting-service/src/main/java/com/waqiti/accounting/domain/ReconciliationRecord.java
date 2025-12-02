package com.waqiti.accounting.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reconciliation Record entity
 * Tracks account reconciliations with external systems
 */
@Entity
@Table(name = "reconciliation", indexes = {
    @Index(name = "idx_reconciliation_account", columnList = "account_code"),
    @Index(name = "idx_reconciliation_date", columnList = "reconciliation_date"),
    @Index(name = "idx_reconciliation_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "reconciliation_id", unique = true, nullable = false, length = 100)
    private String reconciliationId;

    @NotNull
    @Column(name = "account_code", nullable = false, length = 50)
    private String accountCode;

    @NotNull
    @Column(name = "reconciliation_date", nullable = false)
    private LocalDate reconciliationDate;

    @NotNull
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @NotNull
    @Column(name = "fiscal_period", nullable = false, length = 20)
    private String fiscalPeriod;

    @Column(name = "bank_statement_balance", precision = 18, scale = 2)
    private BigDecimal bankStatementBalance;

    @NotNull
    @Column(name = "book_balance", precision = 18, scale = 2, nullable = false)
    private BigDecimal bookBalance;

    @NotNull
    @Column(name = "adjusted_balance", precision = 18, scale = 2, nullable = false)
    private BigDecimal adjustedBalance;

    @NotNull
    @Column(name = "difference", precision = 18, scale = 2, nullable = false)
    private BigDecimal difference;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "reconciled_by", length = 100)
    private String reconciledBy;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "adjustments", columnDefinition = "JSONB")
    private String adjustments;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (reconciliationId == null) {
            reconciliationId = "REC-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
