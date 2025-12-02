package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NostroReconciliationResult {

    private LocalDate reconciliationDate;

    private int totalNostroAccounts;

    private int reconciledAccounts;

    private int breaksDetected;

    private List<NostroAccountBreak> breaks;

    @Builder.Default
    private LocalDateTime completedAt = LocalDateTime.now();

    private Long processingTimeMs;

    private ReconciliationSummary summary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NostroAccountBreak {
        private UUID nostroAccountId;
        private String nostroAccountNumber;
        private String externalBankName;
        private BigDecimal variance;
        private UUID breakId;
        private String breakDescription;
        private String severity;
        private boolean requiresManualReview;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconciliationSummary {
        private BigDecimal totalVarianceAmount;
        private String reconciliationStatus;
        private int highPriorityBreaks;
        private int mediumPriorityBreaks;
        private int lowPriorityBreaks;
        private double successRate;
    }

    public double getReconciliationSuccessRate() {
        if (totalNostroAccounts == 0) return 0.0;
        return (double) reconciledAccounts / totalNostroAccounts * 100.0;
    }

    public boolean hasBreaks() {
        return breaksDetected > 0;
    }

    public boolean isFullyReconciled() {
        return breaksDetected == 0 && reconciledAccounts == totalNostroAccounts;
    }

    public List<NostroAccountBreak> getHighPriorityBreaks() {
        if (breaks == null) return List.of();
        return breaks.stream()
            .filter(b -> "HIGH".equalsIgnoreCase(b.severity) || "CRITICAL".equalsIgnoreCase(b.severity))
            .toList();
    }
}
