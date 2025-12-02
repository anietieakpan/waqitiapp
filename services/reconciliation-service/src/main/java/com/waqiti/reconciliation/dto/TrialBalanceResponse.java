package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialBalanceResponse {

    private LocalDateTime generatedAt;
    
    private LocalDateTime balanceAsOf;
    
    private BigDecimal totalDebits;
    
    private BigDecimal totalCredits;
    
    private BigDecimal netDifference;
    
    private boolean isBalanced;
    
    private String baseCurrency;
    
    private List<TrialBalanceEntry> entries;
    
    private Map<String, AccountCategorySummary> categorySummaries;
    
    private TrialBalanceStatistics statistics;
    
    private List<TrialBalanceException> exceptions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrialBalanceEntry {
        private String accountCode;
        private String accountName;
        private String accountType;
        private String accountCategory;
        private BigDecimal debitBalance;
        private BigDecimal creditBalance;
        private BigDecimal netBalance;
        private String currency;
        private boolean hasActivity;
        private int transactionCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountCategorySummary {
        private String category;
        private BigDecimal totalDebits;
        private BigDecimal totalCredits;
        private BigDecimal netBalance;
        private int accountCount;
        private int activeAccountCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrialBalanceStatistics {
        private int totalAccounts;
        private int accountsWithBalance;
        private int dormantAccounts;
        private BigDecimal largestDebitBalance;
        private BigDecimal largestCreditBalance;
        private String accountWithLargestDebit;
        private String accountWithLargestCredit;
        private double imbalancePercentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrialBalanceException {
        private String exceptionType;
        private String accountCode;
        private String description;
        private BigDecimal amount;
        private String severity;
        private String recommendedAction;
    }

    public boolean isBalanced() {
        return isBalanced && (netDifference == null || 
                             netDifference.abs().compareTo(new BigDecimal("0.01")) <= 0);
    }

    public BigDecimal getImbalanceAmount() {
        return netDifference != null ? netDifference.abs() : BigDecimal.ZERO;
    }

    public double getImbalancePercentage() {
        if (totalDebits == null || totalDebits.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        return getImbalanceAmount().divide(totalDebits, 4, java.math.RoundingMode.HALF_UP)
               .multiply(new BigDecimal("100")).doubleValue();
    }

    public boolean hasExceptions() {
        return exceptions != null && !exceptions.isEmpty();
    }

    public boolean hasCriticalExceptions() {
        return exceptions != null && 
               exceptions.stream().anyMatch(e -> "CRITICAL".equalsIgnoreCase(e.getSeverity()));
    }

    public List<TrialBalanceException> getCriticalExceptions() {
        if (exceptions == null) return List.of();
        return exceptions.stream()
                .filter(e -> "CRITICAL".equalsIgnoreCase(e.getSeverity()))
                .toList();
    }
}