package com.waqiti.ledger.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_balances", 
    uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "currency"}),
    indexes = {
        @Index(name = "idx_balance_account_id", columnList = "account_id"),
        @Index(name = "idx_balance_currency", columnList = "currency"),
        @Index(name = "idx_balance_last_updated", columnList = "last_updated")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"id"})
@ToString(exclude = {"createdAt", "updatedAt"})
public class AccountBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String currency;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentBalance;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(name = "pending_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal pendingBalance;

    @Column(name = "reserved_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedBalance;

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "last_transaction_id")
    private String lastTransactionId;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    @Column(name = "is_frozen")
    private Boolean isFrozen = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = Instant.now();
        if (currentBalance == null) currentBalance = BigDecimal.ZERO;
        if (availableBalance == null) availableBalance = BigDecimal.ZERO;
        if (pendingBalance == null) pendingBalance = BigDecimal.ZERO;
        if (reservedBalance == null) reservedBalance = BigDecimal.ZERO;
    }

    public BigDecimal getEffectiveBalance() {
        BigDecimal effective = currentBalance;
        if (creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) > 0) {
            effective = effective.add(creditLimit);
        }
        return effective;
    }

    public boolean hasAvailableBalance(BigDecimal amount) {
        return availableBalance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        currentBalance = currentBalance.subtract(amount);
        availableBalance = availableBalance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        currentBalance = currentBalance.add(amount);
        availableBalance = availableBalance.add(amount);
    }

    public void reserveFunds(BigDecimal amount) {
        if (!hasAvailableBalance(amount)) {
            throw new IllegalArgumentException("Insufficient available balance to reserve funds");
        }
        availableBalance = availableBalance.subtract(amount);
        reservedBalance = reservedBalance.add(amount);
    }

    public void releaseFunds(BigDecimal amount) {
        if (reservedBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Cannot release more funds than reserved");
        }
        reservedBalance = reservedBalance.subtract(amount);
        availableBalance = availableBalance.add(amount);
    }
}