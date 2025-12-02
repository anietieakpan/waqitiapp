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
 * Account Balance entity
 * Tracks period-based balances for each account in the chart of accounts
 */
@Entity
@Table(name = "account_balance",
    uniqueConstraints = @UniqueConstraint(columnNames = {"account_code", "fiscal_year", "fiscal_period"}),
    indexes = {
        @Index(name = "idx_account_balance_account", columnList = "account_code"),
        @Index(name = "idx_account_balance_fiscal", columnList = "fiscal_year,fiscal_period")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotNull
    @Column(name = "account_code", nullable = false, length = 50)
    private String accountCode;

    @NotNull
    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @NotNull
    @Column(name = "fiscal_period", nullable = false, length = 20)
    private String fiscalPeriod;

    @NotNull
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @NotNull
    @Column(name = "opening_balance", precision = 18, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @NotNull
    @Column(name = "period_debits", precision = 18, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal periodDebits = BigDecimal.ZERO;

    @NotNull
    @Column(name = "period_credits", precision = 18, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal periodCredits = BigDecimal.ZERO;

    @NotNull
    @Column(name = "closing_balance", precision = 18, scale = 2, nullable = false)
    private BigDecimal closingBalance;

    @Column(name = "last_transaction_date")
    private LocalDate lastTransactionDate;

    @NotNull
    @Column(name = "is_closed", nullable = false)
    @Builder.Default
    private Boolean isClosed = false;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
