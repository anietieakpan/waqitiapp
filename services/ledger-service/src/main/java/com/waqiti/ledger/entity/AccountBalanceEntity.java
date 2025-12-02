package com.waqiti.ledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.waqiti.ledger.domain.AccountingPeriod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Account Balance Entity
 * 
 * Tracks the current balance and balance history for ledger accounts.
 * Maintains debit and credit balances separately for double-entry bookkeeping.
 */
@Entity
@Table(name = "account_balances",
    indexes = {
        @Index(name = "idx_balance_account", columnList = "account_id"),
        @Index(name = "idx_balance_period", columnList = "period_id"),
        @Index(name = "idx_balance_date", columnList = "balance_date")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"account", "accountingPeriod"})
@ToString(exclude = {"account", "accountingPeriod"})
public class AccountBalanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "balance_id")
    private UUID balanceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "period_id")
    private AccountingPeriod accountingPeriod;

    @Column(name = "balance_date", nullable = false)
    private LocalDateTime balanceDate;

    @Column(name = "debit_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal debitBalance;

    @Column(name = "credit_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditBalance;

    @Column(name = "net_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal netBalance;

    @Column(name = "opening_balance", precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 19, scale = 4)
    private BigDecimal closingBalance;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "balance_type", length = 50)
    @Enumerated(EnumType.STRING)
    private BalanceType balanceType;

    @Column(name = "is_reconciled")
    private Boolean isReconciled;

    @Column(name = "reconciliation_date")
    private LocalDateTime reconciliationDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Calculate net balance from debit and credit
     */
    @PrePersist
    @PreUpdate
    public void calculateNetBalance() {
        if (debitBalance == null) debitBalance = BigDecimal.ZERO;
        if (creditBalance == null) creditBalance = BigDecimal.ZERO;
        
        // For asset and expense accounts, net = debit - credit
        // For liability, equity, and revenue accounts, net = credit - debit
        if (account != null && account.getAccountType() != null) {
            switch (account.getAccountType()) {
                case ASSET:
                case EXPENSE:
                    this.netBalance = debitBalance.subtract(creditBalance);
                    break;
                case LIABILITY:
                case EQUITY:
                case REVENUE:
                    this.netBalance = creditBalance.subtract(debitBalance);
                    break;
                default:
                    this.netBalance = debitBalance.subtract(creditBalance);
            }
        } else {
            this.netBalance = debitBalance.subtract(creditBalance);
        }
    }

    public enum BalanceType {
        ACTUAL,
        BUDGET,
        FORECAST,
        PROVISIONAL
    }
}