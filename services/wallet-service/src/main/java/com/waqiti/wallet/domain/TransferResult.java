package com.waqiti.wallet.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing the result of a wallet-to-wallet transfer.
 * Provides complete audit trail and balance tracking for transfer operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResult {
    
    private UUID transferId;
    private UUID fromWalletId;
    private UUID toWalletId;
    private BigDecimal amount;
    
    // Balance tracking
    private BigDecimal fromBalanceBefore;
    private BigDecimal fromBalanceAfter;
    private BigDecimal toBalanceBefore;
    private BigDecimal toBalanceAfter;
    
    // Transaction references
    private UUID debitTransactionId;
    private UUID creditTransactionId;
    
    // Status and metadata
    private TransferStatus status;
    private String description;
    private Instant initiatedAt;
    private Instant completedAt;
    private String failureReason;
    
    // Fee information
    private BigDecimal feeAmount;
    private String feeCurrency;
    private UUID feeTransactionId;
    
    // Compliance and audit
    private boolean amlChecked;
    private String amlCheckResult;
    private boolean fraudChecked;
    private String fraudCheckResult;
    
    // Exchange rate for cross-currency transfers
    private BigDecimal exchangeRate;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal convertedAmount;
}