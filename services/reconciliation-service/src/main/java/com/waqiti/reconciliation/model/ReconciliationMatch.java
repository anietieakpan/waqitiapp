package com.waqiti.reconciliation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the result of matching an internal transaction with a provider transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationMatch {

    private MatchType matchType;
    private ProviderTransaction providerTransaction;
    private double confidence;
    private String matchReason;
    private double matchScore;

    public enum MatchType {
        EXACT_MATCH,     // Perfect match on all criteria
        FUZZY_MATCH,     // Good match within acceptable thresholds
        PARTIAL_MATCH,   // Possible match but with discrepancies
        NO_MATCH         // No suitable match found
    }

    /**
     * Create an exact match result
     */
    public static ReconciliationMatch exactMatch(ProviderTransaction providerTransaction) {
        return ReconciliationMatch.builder()
                .matchType(MatchType.EXACT_MATCH)
                .providerTransaction(providerTransaction)
                .confidence(1.0)
                .matchScore(1.0)
                .matchReason("Exact match on transaction ID and amount")
                .build();
    }

    /**
     * Create a fuzzy match result
     */
    public static ReconciliationMatch fuzzyMatch(ProviderTransaction providerTransaction) {
        return ReconciliationMatch.builder()
                .matchType(MatchType.FUZZY_MATCH)
                .providerTransaction(providerTransaction)
                .confidence(0.85)
                .matchScore(0.85)
                .matchReason("Match based on amount and timing within tolerance")
                .build();
    }

    /**
     * Create a partial match result
     */
    public static ReconciliationMatch partialMatch(ProviderTransaction providerTransaction, double confidence) {
        return ReconciliationMatch.builder()
                .matchType(MatchType.PARTIAL_MATCH)
                .providerTransaction(providerTransaction)
                .confidence(confidence)
                .matchScore(confidence)
                .matchReason("Partial match with discrepancies - requires review")
                .build();
    }

    /**
     * Create a no match result
     */
    public static ReconciliationMatch noMatch() {
        return ReconciliationMatch.builder()
                .matchType(MatchType.NO_MATCH)
                .providerTransaction(null)
                .confidence(0.0)
                .matchScore(0.0)
                .matchReason("No suitable provider transaction found")
                .build();
    }

    // Utility methods
    public boolean isMatched() {
        return matchType == MatchType.EXACT_MATCH || matchType == MatchType.FUZZY_MATCH;
    }

    public boolean hasPartialMatch() {
        return matchType == MatchType.PARTIAL_MATCH;
    }

    public boolean hasNoMatch() {
        return matchType == MatchType.NO_MATCH;
    }

    public boolean isHighConfidenceMatch() {
        return confidence >= 0.9;
    }

    public boolean requiresManualReview() {
        return matchType == MatchType.PARTIAL_MATCH || 
               (isMatched() && confidence < 0.8);
    }

    public String getMatchDescription() {
        switch (matchType) {
            case EXACT_MATCH:
                return "Perfect match found";
            case FUZZY_MATCH:
                return String.format("Good match found (%.1f%% confidence)", confidence * 100);
            case PARTIAL_MATCH:
                return String.format("Partial match found (%.1f%% confidence) - requires review", confidence * 100);
            case NO_MATCH:
            default:
                return "No match found";
        }
    }
}