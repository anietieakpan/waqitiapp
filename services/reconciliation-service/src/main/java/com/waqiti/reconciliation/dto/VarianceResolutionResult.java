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
public class VarianceResolutionResult {

    private UUID accountId;
    
    private boolean resolved;
    
    private VarianceAnalysis analysis;
    
    private String resolutionMethod;
    
    private String resolutionNotes;
    
    private List<ResolutionAction> actionsPerformed;
    
    private List<String> recommendedActions;
    
    @Builder.Default
    private LocalDateTime resolvedAt = LocalDateTime.now();
    
    private String resolvedBy;
    
    private ResolutionConfidence confidence;
    
    private List<String> preventiveMeasures;

    public enum ResolutionConfidence {
        HIGH(0.9),
        MEDIUM(0.7),
        LOW(0.5),
        UNCERTAIN(0.3);

        private final double threshold;

        ResolutionConfidence(double threshold) {
            this.threshold = threshold;
        }

        public double getThreshold() {
            return threshold;
        }
    }

    public boolean isResolved() {
        return resolved;
    }

    public boolean hasActionsPerformed() {
        return actionsPerformed != null && !actionsPerformed.isEmpty();
    }

    public boolean hasRecommendations() {
        return recommendedActions != null && !recommendedActions.isEmpty();
    }

    public boolean isHighConfidence() {
        return ResolutionConfidence.HIGH.equals(confidence);
    }

    public boolean requiresFollowUp() {
        return !resolved || ResolutionConfidence.LOW.equals(confidence) || 
               ResolutionConfidence.UNCERTAIN.equals(confidence);
    }

    public List<ResolutionAction> getSuccessfulActions() {
        if (actionsPerformed == null) return List.of();
        
        return actionsPerformed.stream()
                .filter(ResolutionAction::isSuccessful)
                .toList();
    }

    public List<ResolutionAction> getFailedActions() {
        if (actionsPerformed == null) return List.of();
        
        return actionsPerformed.stream()
                .filter(action -> !action.isSuccessful())
                .toList();
    }
}