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
public class InvestigationFinding {

    private String findingType;
    
    private String description;
    
    private double confidence;
    
    private String severity;
    
    private boolean actionRequired;
    
    @Builder.Default
    private LocalDateTime identifiedAt = LocalDateTime.now();
    
    private String evidence;
    
    private List<String> supportingData;
    
    private String recommendedAction;

    public boolean isCritical() {
        return "CRITICAL".equalsIgnoreCase(severity);
    }

    public boolean isHigh() {
        return "HIGH".equalsIgnoreCase(severity);
    }

    public boolean isMedium() {
        return "MEDIUM".equalsIgnoreCase(severity);
    }

    public boolean isLow() {
        return "LOW".equalsIgnoreCase(severity);
    }

    public boolean isActionRequired() {
        return actionRequired;
    }

    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    public boolean hasSupportingData() {
        return supportingData != null && !supportingData.isEmpty();
    }
}