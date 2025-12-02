package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoResolutionResult {

    private boolean successful;
    
    private String resolutionMethod;
    
    private String resolutionNotes;
    
    private String failureReason;
    
    @Builder.Default
    private LocalDateTime resolvedAt = LocalDateTime.now();
    
    private ResolutionExecutionResult executionResult;
    
    private List<String> actionsPerformed;
    
    private String confidence;
    
    private boolean requiresManualVerification;

    public boolean isSuccessful() {
        return successful;
    }

    public boolean hasFailed() {
        return !successful;
    }

    public boolean hasExecutionResult() {
        return executionResult != null;
    }

    public boolean requiresVerification() {
        return requiresManualVerification;
    }

    public boolean hasActions() {
        return actionsPerformed != null && !actionsPerformed.isEmpty();
    }
}