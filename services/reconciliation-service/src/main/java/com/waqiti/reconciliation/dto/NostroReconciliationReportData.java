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
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NostroReconciliationReportData {

    private LocalDate reportDate;
    
    private NostroOverview overview;
    
    private List<NostroAccountReconciliation> accountReconciliations;
    
    private Map<String, BankReconciliationSummary> bankSummaries;
    
    private Map<String, CurrencyReconciliationSummary> currencySummaries;
    
    private List<NostroBreakDetail> breakDetails;
    
    private CorrespondentBankAnalysis correspondentBankAnalysis;
    
    private List<UnconfirmedTransaction> unconfirmedTransactions;
    
    private List<RecommendedAction> recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NostroOverview {
        private int totalNostroAccounts;
        private int reconciledAccounts;
        private int unreconciledAccounts;
        private BigDecimal totalInternalBalance;
        private BigDecimal totalExternalBalance;
        private BigDecimal totalVariance;
        private int totalBreaks;
        private double reconciliationRate;
        private LocalDateTime lastReconciliationTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NostroAccountReconciliation {
        private UUID accountId;
        private String accountNumber;
        private String bankCode;
        private String bankName;
        private String currency;
        private BigDecimal internalBalance;
        private BigDecimal externalBalance;
        private BigDecimal variance;
        private String reconciliationStatus;
        private LocalDateTime lastConfirmationReceived;
        private List<TransactionDiscrepancy> discrepancies;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDiscrepancy {
        private String transactionRef;
        private LocalDate transactionDate;
        private BigDecimal amount;
        private String discrepancyType;
        private String status;
        private String resolution;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankReconciliationSummary {
        private String bankCode;
        private String bankName;
        private int accountCount;
        private int reconciledCount;
        private BigDecimal totalVariance;
        private List<String> currencies;
        private double reconciliationRate;
        private boolean hasConnectionIssues;
        private LocalDateTime lastSuccessfulConnection;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyReconciliationSummary {
        private String currency;
        private int accountCount;
        private BigDecimal totalInternalBalance;
        private BigDecimal totalExternalBalance;
        private BigDecimal totalVariance;
        private int breakCount;
        private double reconciliationRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NostroBreakDetail {
        private UUID breakId;
        private String accountNumber;
        private String bankName;
        private String currency;
        private BigDecimal varianceAmount;
        private String breakType;
        private String severity;
        private LocalDateTime detectedAt;
        private String status;
        private String assignedTo;
        private String rootCause;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CorrespondentBankAnalysis {
        private Map<String, BankPerformance> bankPerformanceMetrics;
        private List<BankIssue> identifiedIssues;
        private List<BankRecommendation> recommendations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankPerformance {
        private String bankCode;
        private double reconciliationSuccessRate;
        private double averageConfirmationDelayHours;
        private int statementDiscrepancyCount;
        private double dataQualityScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankIssue {
        private String bankCode;
        private String issueType;
        private String description;
        private String severity;
        private LocalDateTime firstOccurrence;
        private int occurrenceCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankRecommendation {
        private String bankCode;
        private String recommendation;
        private String expectedBenefit;
        private String priority;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnconfirmedTransaction {
        private String transactionRef;
        private LocalDate valueDate;
        private BigDecimal amount;
        private String currency;
        private String bankCode;
        private int daysPending;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendedAction {
        private String actionType;
        private String description;
        private String priority;
        private String expectedImpact;
        private String responsibleParty;
    }

    public boolean hasVariances() {
        return overview != null && overview.getTotalVariance() != null && 
               overview.getTotalVariance().compareTo(BigDecimal.ZERO) != 0;
    }

    public boolean hasBreaks() {
        return overview != null && overview.getTotalBreaks() > 0;
    }

    public boolean hasUnconfirmedTransactions() {
        return unconfirmedTransactions != null && !unconfirmedTransactions.isEmpty();
    }
}