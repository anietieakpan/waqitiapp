package com.waqiti.accounting.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
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
 * General Ledger Entry entity
 * Represents individual postings to the general ledger from journal entries
 */
@Entity
@Table(name = "general_ledger", indexes = {
    @Index(name = "idx_gl_account", columnList = "account_code"),
    @Index(name = "idx_gl_posting_date", columnList = "posting_date"),
    @Index(name = "idx_gl_fiscal", columnList = "fiscal_year,fiscal_period")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "ledger_id", unique = true, nullable = false, length = 100)
    private String ledgerId;

    @NotNull
    @Column(name = "account_code", nullable = false, length = 50)
    private String accountCode;

    @NotNull
    @Column(name = "entry_id", nullable = false, length = 100)
    private String entryId;

    @NotNull
    @Column(name = "line_id", nullable = false, length = 100)
    private String lineId;

    @NotNull
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @NotNull
    @Column(name = "posting_date", nullable = false)
    private LocalDate postingDate;

    @NotNull
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @NotNull
    @Column(name = "fiscal_period", nullable = false, length = 20)
    private String fiscalPeriod;

    @Column(name = "debit_amount", precision = 19, scale = 4)
    @DecimalMin(value = "0.0000")
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", precision = 19, scale = 4)
    @DecimalMin(value = "0.0000")
    private BigDecimal creditAmount;

    @NotNull
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @NotNull
    @Column(name = "running_balance", precision = 19, scale = 4, nullable = false)
    private BigDecimal runningBalance;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Column(name = "cost_center", length = 50)
    private String costCenter;

    @Column(name = "department", length = 50)
    private String department;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (ledgerId == null) {
            ledgerId = "GL-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }
}
