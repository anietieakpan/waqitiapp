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
public class SettlementMatchResult {

    private boolean matched;
    
    private List<SettlementDiscrepancy> discrepancies;
    
    private double matchScore;
    
    private String matchingAlgorithm;
    
    @Builder.Default
    private LocalDateTime matchedAt = LocalDateTime.now();
    
    private MatchDetails matchDetails;
    
    private String failureReason;
    
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchDetails {
        private boolean amountMatched;
        private boolean currencyMatched;
        private boolean dateMatched;
        private boolean referenceMatched;
        private boolean counterpartyMatched;
        private double overallScore;
        private String primaryMatchingField;
        private List<String> matchedFields;
        private List<String> unmatchedFields;
    }

    public boolean isMatched() {
        return matched;
    }

    public boolean hasDiscrepancies() {
        return discrepancies != null && !discrepancies.isEmpty();
    }

    public boolean isHighQualityMatch() {
        return matched && matchScore >= 0.9;
    }

    public boolean requiresManualReview() {
        return !matched || hasDiscrepancies() || matchScore < 0.8;
    }

    public List<SettlementDiscrepancy> getCriticalDiscrepancies() {
        if (discrepancies == null) return List.of();
        
        return discrepancies.stream()
                .filter(d -> "CRITICAL".equalsIgnoreCase(d.getSeverity()) || 
                           "HIGH".equalsIgnoreCase(d.getSeverity()))
                .toList();
    }

    public boolean hasCriticalDiscrepancies() {
        return !getCriticalDiscrepancies().isEmpty();
    }
}