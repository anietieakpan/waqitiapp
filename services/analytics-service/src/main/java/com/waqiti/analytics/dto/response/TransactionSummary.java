package com.waqiti.analytics.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Transaction Summary DTO
 *
 * Comprehensive summary of user transaction metrics for a given period.
 * Provides high-level financial overview including spending, income, and cash flow.
 *
 * @author Waqiti Analytics Team
 * @version 2.0
 * @since 2025-10-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummary {

    /**
     * Total number of transactions in the period
     */
    private Long totalTransactions;

    /**
     * Total amount spent (outgoing transactions)
     */
    private BigDecimal totalSpent;

    /**
     * Total amount received (incoming transactions)
     */
    private BigDecimal totalReceived;

    /**
     * Net cash flow (totalReceived - totalSpent)
     */
    private BigDecimal netCashFlow;

    /**
     * Average transaction amount across all transactions
     */
    private BigDecimal averageTransactionAmount;

    /**
     * Largest single transaction amount
     */
    private BigDecimal largestTransaction;

    /**
     * Smallest single transaction amount
     */
    private BigDecimal smallestTransaction;

    /**
     * Number of unique merchants transacted with
     */
    private Integer uniqueMerchants;

    /**
     * Number of unique spending categories
     */
    private Integer uniqueCategories;

    /**
     * Number of successful transactions
     */
    private Long successfulTransactions;

    /**
     * Number of failed transactions
     */
    private Long failedTransactions;

    /**
     * Number of pending transactions
     */
    private Long pendingTransactions;
}
