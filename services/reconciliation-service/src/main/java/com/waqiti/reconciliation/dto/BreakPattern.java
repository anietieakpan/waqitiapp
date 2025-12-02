package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BreakPattern {

    private String patternType;
    
    private String frequency;
    
    private String description;
    
    private double confidence;
    
    private String impact;
    
    private List<LocalDateTime> occurrences;
    
    private BigDecimal averageAmount;
    
    private String predictability;
    
    private List<String> associatedFactors;

    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    public boolean isRecurring() {
        return occurrences != null && occurrences.size() > 1;
    }

    public boolean hasHighImpact() {
        return "HIGH".equalsIgnoreCase(impact);
    }

    public boolean isPredictable() {
        return "HIGH".equalsIgnoreCase(predictability) || 
               "MEDIUM".equalsIgnoreCase(predictability);
    }

    public int getOccurrenceCount() {
        return occurrences != null ? occurrences.size() : 0;
    }
}