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
public class VariancePattern {

    private String patternType;
    
    private String description;
    
    private double confidence;
    
    private boolean autoResolvable;
    
    private String frequency;
    
    private PatternCategory category;
    
    private List<LocalDateTime> occurrences;
    
    private BigDecimal averageImpact;
    
    private String predictability;
    
    private Map<String, Object> patternAttributes;
    
    private List<String> correlatedFactors;
    
    private String recommendedAction;

    public enum PatternCategory {
        TIMING_BASED,
        AMOUNT_BASED,
        SYSTEM_BASED,
        PROCESS_BASED,
        EXTERNAL_FACTOR,
        SEASONAL,
        CYCLICAL
    }

    public boolean isAutoResolvable() {
        return autoResolvable;
    }

    public boolean isHighConfidence() {
        return confidence >= 0.8;
    }

    public boolean isMediumConfidence() {
        return confidence >= 0.6 && confidence < 0.8;
    }

    public boolean isLowConfidence() {
        return confidence < 0.6;
    }

    public boolean isRecurring() {
        return occurrences != null && occurrences.size() > 1;
    }

    public boolean isPredictable() {
        return "HIGH".equalsIgnoreCase(predictability) || 
               "MEDIUM".equalsIgnoreCase(predictability);
    }

    public int getOccurrenceCount() {
        return occurrences != null ? occurrences.size() : 0;
    }

    public boolean hasSignificantImpact() {
        return averageImpact != null && 
               averageImpact.compareTo(new BigDecimal("100.00")) > 0;
    }

    public LocalDateTime getLastOccurrence() {
        if (occurrences == null || occurrences.isEmpty()) {
            return null;
        }
        return occurrences.stream().max(LocalDateTime::compareTo).orElse(null);
    }

    public LocalDateTime getFirstOccurrence() {
        if (occurrences == null || occurrences.isEmpty()) {
            return null;
        }
        return occurrences.stream().min(LocalDateTime::compareTo).orElse(null);
    }
}