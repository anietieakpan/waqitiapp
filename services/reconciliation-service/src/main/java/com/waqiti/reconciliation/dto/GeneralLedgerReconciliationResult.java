package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneralLedgerReconciliationResult {

    private LocalDate reconciliationDate;
    
    private boolean balanced;
    
    private BigDecimal totalDebits;
    
    private BigDecimal totalCredits;
    
    private BigDecimal variance;
    
    private List<LedgerAccountSummary> accountSummaries;
    
    private Map<String, LedgerCategoryBalance> categoryBalances;
    
    private List<UnbalancedEntry> unbalancedEntries;
    
    @Builder.Default
    private LocalDateTime completedAt = LocalDateTime.now();
    
    private Long processingTimeMs;
    
    private TrialBalanceResult trialBalance;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerAccountSummary {
        private String accountCode;
        private String accountName;
        private String accountType;
        private BigDecimal debitBalance;
        private BigDecimal creditBalance;
        private BigDecimal netBalance;
        private boolean hasDiscrepancy;
        private String discrepancyReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerCategoryBalance {
        private String category;
        private BigDecimal totalDebits;
        private BigDecimal totalCredits;
        private BigDecimal netBalance;
        private int accountCount;
        private boolean balanced;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrialBalanceResult {
        private BigDecimal assetTotal;
        private BigDecimal liabilityTotal;
        private BigDecimal equityTotal;
        private BigDecimal revenueTotal;
        private BigDecimal expenseTotal;
        private BigDecimal netIncome;
        private boolean balanced;
        private BigDecimal imbalanceAmount;
    }

    public boolean isBalanced() {
        return balanced && (variance == null || variance.compareTo(BigDecimal.ZERO) == 0);
    }

    public boolean hasSignificantVariance() {
        return variance != null && variance.abs().compareTo(new BigDecimal("0.01")) > 0;
    }

    public boolean requiresInvestigation() {
        return !balanced || hasSignificantVariance() || 
               (unbalancedEntries != null && !unbalancedEntries.isEmpty());
    }

    public BigDecimal getAbsoluteVariance() {
        return variance != null ? variance.abs() : BigDecimal.ZERO;
    }
}