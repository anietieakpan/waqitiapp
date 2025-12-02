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
public class PatternAnalysis {

    @Builder.Default
    private LocalDateTime analysisDate = LocalDateTime.now();
    
    private List<BreakPattern> patternsIdentified;
    
    private double overallPatternStrength;
    
    private String trendDirection;
    
    private String analysisMethod;
    
    private String analysisNotes;

    public boolean hasPatternsIdentified() {
        return patternsIdentified != null && !patternsIdentified.isEmpty();
    }

    public boolean hasStrongPatterns() {
        return overallPatternStrength >= 0.7;
    }

    public int getPatternCount() {
        return patternsIdentified != null ? patternsIdentified.size() : 0;
    }

    public List<BreakPattern> getHighConfidencePatterns() {
        if (patternsIdentified == null) return List.of();
        
        return patternsIdentified.stream()
                .filter(pattern -> pattern.getConfidence() >= 0.8)
                .toList();
    }
}