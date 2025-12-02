package com.waqiti.wallet.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Account Limits Entity
 *
 * Stores transaction limits and velocity controls for wallet accounts.
 * Provides configurable limits for compliance and risk management.
 */
@Entity
@Table(name = "account_limits", indexes = {
    @Index(name = "idx_account_limits_user_id", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountLimits {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36, unique = true)
    private String userId;

    @Column(name = "daily_transaction_limit", precision = 19, scale = 4)
    private BigDecimal dailyTransactionLimit;

    @Column(name = "monthly_transaction_limit", precision = 19, scale = 4)
    private BigDecimal monthlyTransactionLimit;

    @Column(name = "single_transaction_limit", precision = 19, scale = 4)
    private BigDecimal singleTransactionLimit;

    @Column(name = "withdrawal_limit", precision = 19, scale = 4)
    private BigDecimal withdrawalLimit;

    @Column(name = "deposit_limit", precision = 19, scale = 4)
    private BigDecimal depositLimit;

    @Column(name = "account_balance_limit", precision = 19, scale = 4)
    private BigDecimal accountBalanceLimit;

    @Column(name = "velocity_limit")
    private Integer velocityLimit;

    @Column(name = "velocity_time_window", length = 10)
    private String velocityTimeWindow;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "update_reason", length = 500)
    private String updateReason;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Version
    @Column(name = "version")
    private Long version;
}
