package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transaction summary report data transfer object
 * Contains aggregated transaction data for a specific period
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryReport {
    
    private UUID walletId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String currency;
    
    // Counts
    private int totalTransactions;
    private int creditTransactions;
    private int debitTransactions;
    private int pendingTransactions;
    private int failedTransactions;
    
    // Amounts
    private BigDecimal totalIncoming;
    private BigDecimal totalOutgoing;
    private BigDecimal netAmount;
    private BigDecimal largestTransaction;
    private BigDecimal averageTransaction;
    
    // Additional metrics
    private List<CategorySummary> categorySummaries;
    private BigDecimal feesCharged;
    private int internationalTransactions;
    
    /**
     * Check if there's net positive flow
     */
    public boolean hasPositiveFlow() {
        return netAmount != null && netAmount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Get success rate as percentage
     */
    public double getSuccessRate() {
        if (totalTransactions == 0) {
            return 0.0;
        }
        
        int successfulTransactions = totalTransactions - failedTransactions - pendingTransactions;
        return (double) successfulTransactions / totalTransactions * 100.0;
    }
    
    /**
     * Category summary for transactions
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummary {
        private String category;
        private int transactionCount;
        private BigDecimal totalAmount;
        private BigDecimal averageAmount;
    }
}