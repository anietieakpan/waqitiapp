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
public class ResolutionExecutionResult {

    private boolean successful;
    
    private String executionNotes;
    
    private String failureReason;
    
    @Builder.Default
    private LocalDateTime executedAt = LocalDateTime.now();
    
    private String executedBy;
    
    private Long executionTimeMs;
    
    private List<String> stepsExecuted;
    
    private Map<String, Object> resultData;
    
    private BigDecimal financialImpact;
    
    private String verificationStatus;
    
    private List<String> warnings;

    public boolean isSuccessful() {
        return successful;
    }

    public boolean hasFailed() {
        return !successful;
    }

    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    public boolean hasFinancialImpact() {
        return financialImpact != null && financialImpact.compareTo(BigDecimal.ZERO) != 0;
    }

    public boolean wasQuickExecution() {
        return executionTimeMs != null && executionTimeMs < 5000; // Less than 5 seconds
    }

    public boolean wasSlowExecution() {
        return executionTimeMs != null && executionTimeMs > 60000; // More than 1 minute
    }
}