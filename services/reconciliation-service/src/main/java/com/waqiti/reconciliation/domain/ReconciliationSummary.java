package com.waqiti.reconciliation.domain;

import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReconciliationSummary {
    private final String reconciliationId;
    private final String accountId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final ReconciliationStatus status;
    private final BigDecimal totalTransactionsProcessed;
    private final BigDecimal matchedTransactions;
    private final BigDecimal unmatchedTransactions;
    private final BigDecimal totalVolumeReconciled;
    private final List<String> errorMessages;
    private final BigDecimal processingTimeMs;
    
    public enum ReconciliationStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        PARTIALLY_COMPLETED
    }
    
    public BigDecimal getMatchRate() {
        if (totalTransactionsProcessed == null || totalTransactionsProcessed.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return matchedTransactions.divide(totalTransactionsProcessed, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    public boolean isSuccessful() {
        return status == ReconciliationStatus.COMPLETED;
    }
    
    public boolean hasErrors() {
        return errorMessages != null && !errorMessages.isEmpty();
    }
}