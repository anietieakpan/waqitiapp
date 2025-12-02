package com.waqiti.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VarianceAnalysis {

    private UUID accountId;
    
    private LocalDateTime analysisDate;
    
    private BigDecimal varianceAmount;
    
    private String analysisType;
    
    private VarianceAnalysisService.VarianceCategory varianceCategory;
    
    private List<VariancePattern> identifiedPatterns;
    
    private List<String> potentialRootCauses;
    
    private double confidenceScore;
    
    private boolean autoResolvable;
    
    private List<ResolutionRecommendation> resolutionRecommendations;
    
    private VarianceImpactAssessment impactAssessment;
    
    private List<String> correlatedFactors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VarianceImpactAssessment {
        private String businessImpact;
        private String operationalImpact;
        private String regulatoryImpact;
        private String customerImpact;
        private String riskLevel;
        private BigDecimal financialImpact;
        private String urgency;
    }

    public boolean isAutoResolvable() {
        return autoResolvable;
    }

    public boolean isHighConfidence() {
        return confidenceScore >= 0.8;
    }

    public boolean isMediumConfidence() {
        return confidenceScore >= 0.6 && confidenceScore < 0.8;
    }

    public boolean isLowConfidence() {
        return confidenceScore < 0.6;
    }

    public boolean hasPatterns() {
        return identifiedPatterns != null && !identifiedPatterns.isEmpty();
    }

    public boolean hasRootCauses() {
        return potentialRootCauses != null && !potentialRootCauses.isEmpty();
    }

    public boolean isSignificantVariance() {
        return varianceAmount != null && 
               varianceAmount.abs().compareTo(new BigDecimal("1000.00")) > 0;
    }

    public List<VariancePattern> getHighConfidencePatterns() {
        if (identifiedPatterns == null) return List.of();
        
        return identifiedPatterns.stream()
                .filter(pattern -> pattern.getConfidence() >= 0.8)
                .toList();
    }

    public String getPrimaryRootCause() {
        if (potentialRootCauses == null || potentialRootCauses.isEmpty()) {
            return "Unknown";
        }
        return potentialRootCauses.get(0);
    }
}