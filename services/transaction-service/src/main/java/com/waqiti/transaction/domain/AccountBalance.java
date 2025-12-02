package com.waqiti.transaction.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Account Balance - Maintains current balance state with transaction limits
 *
 * <p>Features:
 * <ul>
 *   <li>Balance tracking (actual and available)</li>
 *   <li>Transaction limits (single, daily, monthly)</li>
 *   <li>Account status management</li>
 *   <li>Optimistic locking with @Version</li>
 *   <li>Pessimistic locking support for queries</li>
 * </ul>
 *
 * @author Waqiti Platform Team
 */
@Entity
@Table(name = "account_balances", indexes = {
        @Index(name = "idx_account_balance_account_id", columnList = "account_id", unique = true),
        @Index(name = "idx_account_balance_status", columnList = "status"),
        @Index(name = "idx_account_balance_updated", columnList = "updated_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountBalance {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true, length = 100)
    private String accountId;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "reserved_balance", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal reservedBalance = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "single_transaction_limit", precision = 19, scale = 2)
    private BigDecimal singleTransactionLimit;

    @Column(name = "daily_transaction_limit", precision = 19, scale = 2)
    private BigDecimal dailyTransactionLimit;

    @Column(name = "monthly_transaction_limit", precision = 19, scale = 2)
    private BigDecimal monthlyTransactionLimit;

    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;
}
