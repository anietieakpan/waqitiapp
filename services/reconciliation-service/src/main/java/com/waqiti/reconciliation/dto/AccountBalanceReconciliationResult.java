package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceReconciliationResult {

    private UUID accountId;

    private boolean reconciled;

    private BigDecimal accountServiceBalance;

    private BigDecimal ledgerBalance;

    private BigDecimal variance;

    private String varianceReason;

    private UUID breakId;

    private String message;

    @Builder.Default
    private LocalDateTime reconciledAt = LocalDateTime.now();

    private String reconciledBy;

    private Long processingTimeMs;

    private ReconciliationStatus status;

    private List<TransactionDiscrepancy> transactionDiscrepancies;

    private ReconciliationSummary summary;

    private String currency;

    private LocalDateTime balanceAsOf;

    public enum ReconciliationStatus {
        BALANCED,
        VARIANCE_WITHIN_TOLERANCE,
        VARIANCE_EXCEEDS_TOLERANCE,
        BREAK_DETECTED,
        ERROR,
        INVESTIGATION_REQUIRED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDiscrepancy {
        private UUID transactionId;
        private String transactionType;
        private BigDecimal amount;
        private String discrepancyReason;
        private LocalDateTime transactionDate;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconciliationSummary {
        private int totalTransactionsChecked;
        private int matchedTransactions;
        private int unmatchedTransactions;
        private BigDecimal totalVarianceAmount;
        private String reconciliationType;
        private LocalDateTime reconciliationDate;
    }

    public boolean hasVariance() {
        return variance != null && variance.compareTo(BigDecimal.ZERO) != 0;
    }

    public boolean isWithinTolerance(BigDecimal toleranceAmount) {
        if (variance == null || toleranceAmount == null) {
            return !hasVariance();
        }
        return variance.abs().compareTo(toleranceAmount) <= 0;
    }

    public boolean isSignificantVariance(BigDecimal significantAmount) {
        if (variance == null || significantAmount == null) {
            return false;
        }
        return variance.abs().compareTo(significantAmount) > 0;
    }

    public boolean requiresInvestigation() {
        return ReconciliationStatus.INVESTIGATION_REQUIRED.equals(status) ||
               ReconciliationStatus.BREAK_DETECTED.equals(status) ||
               (hasVariance() && ReconciliationStatus.VARIANCE_EXCEEDS_TOLERANCE.equals(status));
    }

    public static AccountBalanceReconciliationResult success(UUID accountId, BigDecimal accountBalance, 
                                                           BigDecimal ledgerBalance, String message) {
        return AccountBalanceReconciliationResult.builder()
            .accountId(accountId)
            .reconciled(true)
            .accountServiceBalance(accountBalance)
            .ledgerBalance(ledgerBalance)
            .variance(BigDecimal.ZERO)
            .status(ReconciliationStatus.BALANCED)
            .message(message)
            .build();
    }

    public static AccountBalanceReconciliationResult varianceDetected(UUID accountId, BigDecimal accountBalance, 
                                                                    BigDecimal ledgerBalance, BigDecimal variance,
                                                                    UUID breakId, String message) {
        return AccountBalanceReconciliationResult.builder()
            .accountId(accountId)
            .reconciled(false)
            .accountServiceBalance(accountBalance)
            .ledgerBalance(ledgerBalance)
            .variance(variance)
            .breakId(breakId)
            .status(ReconciliationStatus.BREAK_DETECTED)
            .message(message)
            .build();
    }
}