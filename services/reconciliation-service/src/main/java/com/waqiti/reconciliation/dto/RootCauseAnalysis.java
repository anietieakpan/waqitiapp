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
public class RootCauseAnalysis {

    private String analysisMethod;
    
    @Builder.Default
    private LocalDateTime analysisDate = LocalDateTime.now();
    
    private List<String> immediateCauses;
    
    private List<String> underlyingCauses;
    
    private List<String> systemicIssues;
    
    private String primaryRootCause;
    
    private List<String> preventionMeasures;
    
    private double confidenceLevel;
    
    private String analysisNotes;

    public boolean hasPrimaryRootCause() {
        return primaryRootCause != null && !primaryRootCause.isEmpty();
    }

    public boolean hasSystemicIssues() {
        return systemicIssues != null && !systemicIssues.isEmpty();
    }

    public boolean hasPreventionMeasures() {
        return preventionMeasures != null && !preventionMeasures.isEmpty();
    }

    public boolean isHighConfidence() {
        return confidenceLevel >= 0.8;
    }
}