package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * P1-2 CRITICAL FIX: Wallet Balance Response for Reconciliation
 *
 * Returns the authoritative wallet balance calculated from ledger entries.
 * Used by wallet-service's automated reconciliation to detect balance discrepancies.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-10-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalanceResponse {

    /**
     * Wallet ID
     */
    private UUID walletId;

    /**
     * Calculated balance from ledger entries (source of truth)
     */
    private String balance;

    /**
     * Currency code (e.g., USD, EUR, GBP)
     */
    private String currency;

    /**
     * Ledger account ID for this wallet's liability account
     */
    private UUID ledgerAccountId;

    /**
     * Total number of ledger entries contributing to this balance
     */
    private Long entryCount;

    /**
     * Last transaction timestamp affecting this wallet
     */
    private LocalDateTime lastTransactionAt;

    /**
     * Timestamp when balance was calculated
     */
    private LocalDateTime calculatedAt;

    /**
     * Indicates if wallet has any pending/unposted transactions
     */
    private Boolean hasPendingTransactions;
}
