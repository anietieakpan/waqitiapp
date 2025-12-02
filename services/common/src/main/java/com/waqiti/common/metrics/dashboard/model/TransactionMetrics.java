package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Transaction metrics data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMetrics {
    private Long totalTransactions;
    private Long successfulTransactions;
    private Long failedTransactions;
    private Long pendingTransactions;
    private BigDecimal totalVolume;
    private BigDecimal avgTransactionAmount;
    private Double successRate;
    private Map<String, Long> transactionsByType;
    private Map<String, Long> transactionsByStatus;
}