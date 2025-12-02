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
public class SystemAccountReconciliationResult {

    private LocalDate reconciliationDate;
    
    private int totalSystemAccounts;
    
    private int reconciledAccounts;
    
    private int breaksDetected;
    
    private List<SystemAccountBreak> systemAccountBreaks;
    
    private Map<String, AccountTypeReconciliation> accountTypeResults;
    
    @Builder.Default
    private LocalDateTime completedAt = LocalDateTime.now();
    
    private Long processingTimeMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemAccountBreak {
        private UUID accountId;
        private String accountType;
        private String accountName;
        private BigDecimal variance;
        private UUID breakId;
        private String description;
        private String severity;
        private boolean affectsGLBalance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountTypeReconciliation {
        private String accountType;
        private int totalAccounts;
        private int reconciledAccounts;
        private int breaksDetected;
        private BigDecimal totalVariance;
    }

    public double getReconciliationSuccessRate() {
        if (totalSystemAccounts == 0) return 0.0;
        return (double) reconciledAccounts / totalSystemAccounts * 100.0;
    }

    public boolean hasBreaks() {
        return breaksDetected > 0;
    }

    public boolean hasGLImpactingBreaks() {
        return systemAccountBreaks != null && 
               systemAccountBreaks.stream().anyMatch(SystemAccountBreak::isAffectsGLBalance);
    }
}