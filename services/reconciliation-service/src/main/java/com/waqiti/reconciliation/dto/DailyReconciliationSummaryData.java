package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyReconciliationSummaryData {

    private LocalDate reconciliationDate;
    
    private ReconciliationOverview overview;
    
    private List<AccountTypeReconciliation> accountTypeReconciliations;
    
    private List<CurrencyReconciliation> currencyReconciliations;
    
    private List<SystemReconciliation> systemReconciliations;
    
    private BreakSummary breakSummary;
    
    private PerformanceMetrics performanceMetrics;
    
    private Map<String, BigDecimal> variancesByCategory;
    
    private List<HighlightedIssue> highlightedIssues;
    
    private List<ActionItem> actionItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconciliationOverview {
        private int totalAccounts;
        private int reconciledAccounts;
        private int unreconciledAccounts;
        private BigDecimal totalBalance;
        private BigDecimal totalVariance;
        private double reconciliationRate;
        private String overallStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountTypeReconciliation {
        private String accountType;
        private int totalAccounts;
        private int reconciledAccounts;
        private BigDecimal totalBalance;
        private BigDecimal variance;
        private double reconciliationRate;
        private int breaksDetected;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrencyReconciliation {
        private String currency;
        private int accountCount;
        private BigDecimal totalAmount;
        private BigDecimal variance;
        private int breaksDetected;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemReconciliation {
        private String systemName;
        private String reconciliationType;
        private boolean completed;
        private int recordsProcessed;
        private int breaksDetected;
        private Long processingTimeMs;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakSummary {
        private int totalBreaks;
        private int criticalBreaks;
        private int highPriorityBreaks;
        private int mediumPriorityBreaks;
        private int lowPriorityBreaks;
        private int resolvedBreaks;
        private int pendingBreaks;
        private double resolutionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        private Long totalProcessingTimeMs;
        private Long averageProcessingTimeMs;
        private int recordsProcessedPerSecond;
        private double systemUtilization;
        private int parallelJobsExecuted;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HighlightedIssue {
        private String issueType;
        private String description;
        private String severity;
        private String impact;
        private String recommendedAction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionItem {
        private String action;
        private String priority;
        private String assignedTo;
        private LocalDate dueDate;
        private String status;
    }

    public boolean hasBreaks() {
        return breakSummary != null && breakSummary.getTotalBreaks() > 0;
    }

    public boolean hasCriticalIssues() {
        return breakSummary != null && breakSummary.getCriticalBreaks() > 0;
    }

    public boolean isFullyReconciled() {
        return overview != null && overview.getReconciledAccounts() == overview.getTotalAccounts();
    }
}