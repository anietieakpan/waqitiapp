package com.waqiti.reconciliation.dto;

import com.waqiti.reconciliation.domain.ReconciliationVariance;
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
public class TransactionLedgerMatchResult {

    private boolean matched;
    
    private LedgerEntry matchedLedgerEntry;
    
    private List<ReconciliationVariance> variances;
    
    private String matchingAlgorithm;
    
    private double matchScore;
    
    private MatchQuality matchQuality;
    
    @Builder.Default
    private LocalDateTime matchedAt = LocalDateTime.now();
    
    private String failureReason;
    
    private List<String> issues;
    
    private MatchMetadata metadata;

    public enum MatchQuality {
        EXACT_MATCH(1.0),
        HIGH_CONFIDENCE(0.9),
        MEDIUM_CONFIDENCE(0.7),
        LOW_CONFIDENCE(0.5),
        NO_MATCH(0.0);

        private final double threshold;

        MatchQuality(double threshold) {
            this.threshold = threshold;
        }

        public double getThreshold() {
            return threshold;
        }

        public static MatchQuality fromScore(double score) {
            if (score >= EXACT_MATCH.threshold) return EXACT_MATCH;
            if (score >= HIGH_CONFIDENCE.threshold) return HIGH_CONFIDENCE;
            if (score >= MEDIUM_CONFIDENCE.threshold) return MEDIUM_CONFIDENCE;
            if (score >= LOW_CONFIDENCE.threshold) return LOW_CONFIDENCE;
            return NO_MATCH;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchMetadata {
        private Long processingTimeMs;
        private String algorithmVersion;
        private boolean requiresManualReview;
        private String confidence;
        private List<String> matchingFactors;
        private List<String> discrepancyFactors;
    }

    public boolean isMatched() {
        return matched;
    }

    public boolean hasVariances() {
        return variances != null && !variances.isEmpty();
    }

    public boolean isHighQualityMatch() {
        return matched && matchScore >= 0.9;
    }

    public boolean requiresManualReview() {
        return !matched || (metadata != null && metadata.requiresManualReview) ||
               matchScore < 0.7;
    }

    public MatchQuality getQualityFromScore() {
        return MatchQuality.fromScore(matchScore);
    }
}