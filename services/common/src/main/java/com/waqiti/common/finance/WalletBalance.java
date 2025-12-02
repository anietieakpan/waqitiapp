package com.waqiti.common.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Wallet balance entity with fund reservation support
 */
@Entity
@Table(name = "wallet_balances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalance {

    @Id
    private UUID id;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal reservedAmount;

    @Column(nullable = false)
    private String status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Get available balance (balance minus reserved funds)
     */
    public BigDecimal getAvailableBalance() {
        return balance.subtract(reservedAmount != null ? reservedAmount : BigDecimal.ZERO);
    }
}
