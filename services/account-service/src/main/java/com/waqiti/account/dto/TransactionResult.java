package com.waqiti.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Transaction Result DTO
 * Result of ledger debit/credit operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResult {
    private boolean success;
    private String transactionId;
    private UUID accountId;
    private BigDecimal amount;
    private String errorCode;
    private String errorMessage;
    private boolean requiresRetry;
    private boolean fallbackMode;
    private BigDecimal balanceAfter;
    private String message;
    private boolean queued;
    private boolean requiresReconciliation;
}