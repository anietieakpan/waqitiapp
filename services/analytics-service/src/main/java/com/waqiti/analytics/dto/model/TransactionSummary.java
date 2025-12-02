package com.waqiti.analytics.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Transaction summary model
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummary {
    private long totalTransactions;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private BigDecimal largestTransaction;
    private BigDecimal smallestTransaction;
    private long successfulTransactions;
    private long failedTransactions;
    private BigDecimal successRate;
}