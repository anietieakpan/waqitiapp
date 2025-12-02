package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BreakInvestigationResult {

    private UUID breakId;
    
    private boolean autoResolvable;
    
    @Builder.Default
    private LocalDateTime investigationStartedAt = LocalDateTime.now();
    
    private LocalDateTime investigationCompletedAt;
    
    private String investigationMethod;
    
    private RootCauseAnalysis rootCauseAnalysis;
    
    private PatternAnalysis patternAnalysis;
    
    private BusinessImpactAssessment businessImpactAssessment;
    
    private List<InvestigationFinding> findings;
    
    private List<ResolutionRecommendation> resolutionRecommendations;
    
    private String investigationNotes;
    
    private double confidenceScore;
    
    private InvestigationStatus status;
    
    private String investigatedBy;

    public enum InvestigationStatus {
        INITIATED,
        IN_PROGRESS,
        COMPLETED,
        ESCALATED,
        SUSPENDED
    }

    public boolean isAutoResolvable() {
        return autoResolvable;
    }

    public boolean isCompleted() {
        return InvestigationStatus.COMPLETED.equals(status);
    }

    public boolean isHighConfidence() {
        return confidenceScore >= 0.8;
    }

    public boolean hasFindings() {
        return findings != null && !findings.isEmpty();
    }

    public boolean hasRecommendations() {
        return resolutionRecommendations != null && !resolutionRecommendations.isEmpty();
    }

    public List<InvestigationFinding> getCriticalFindings() {
        if (findings == null) return List.of();
        
        return findings.stream()
                .filter(f -> "CRITICAL".equalsIgnoreCase(f.getSeverity()))
                .toList();
    }

    public List<ResolutionRecommendation> getAutoExecutableRecommendations() {
        if (resolutionRecommendations == null) return List.of();
        
        return resolutionRecommendations.stream()
                .filter(ResolutionRecommendation::isAutoExecutable)
                .toList();
    }

    public long getInvestigationDurationMs() {
        if (investigationStartedAt == null || investigationCompletedAt == null) {
            return 0L;
        }
        
        return java.time.Duration.between(investigationStartedAt, investigationCompletedAt)
                .toMillis();
    }
}