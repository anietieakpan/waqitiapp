package com.waqiti.wallet.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wallet Account Entity
 *
 * Represents a user's wallet account with balance and transaction capabilities.
 */
@Entity
@Table(name = "wallet_accounts", indexes = {
    @Index(name = "idx_wallet_account_number", columnList = "account_number", unique = true),
    @Index(name = "idx_wallet_user_id", columnList = "user_id"),
    @Index(name = "idx_wallet_user_currency", columnList = "user_id, currency"),
    @Index(name = "idx_wallet_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "account_number", nullable = false, unique = true, length = 50)
    private String accountNumber;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Column(name = "balance", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "available_balance", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "reserved_balance", precision = 19, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal reservedBalance = BigDecimal.ZERO;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "account_type", length = 50)
    @Builder.Default
    private String accountType = "WALLET";

    @Column(name = "is_primary")
    @Builder.Default
    private Boolean isPrimary = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    @Version
    @Column(name = "version")
    private Long version;
}
