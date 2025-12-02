package com.waqiti.common.compliance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of OFAC screening
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OFACScreeningResult {
    
    private String screeningId;
    private String requestId;
    private String entityName;
    private boolean match;
    private boolean hasMatch;
    private RiskLevel riskLevel;
    private double matchScore;
    private double confidenceScore;
    private ScreeningStatus screeningStatus;
    private String errorMessage;
    private String overrideReason;
    private String overrideAuthorizedBy;
    private boolean requiresInvestigation;
    private boolean requiresPostTransactionReview;
    private List<SanctionsMatch> matches;
    private List<SanctionMatch> sanctionMatches;
    private List<ProviderScreeningResult> providerResults;
    private List<String> providersUsed;
    private int providersChecked;
    private int providersWithMatches;
    private LocalDateTime screeningTime;
    private LocalDateTime screenedAt;
    private LocalDateTime lastChecked;
    private long processingTimeMs;
    private String decision; // CLEAR, REVIEW_REQUIRED, BLOCKED
    private Map<String, Object> metadata;
    private boolean cacheHit; // Whether this result came from cache
    
    // Compatibility methods for different naming conventions
    public boolean isMatch() {
        return match || hasMatch;
    }
    
    public void setMatch(boolean match) {
        this.match = match;
        this.hasMatch = match;
    }
    
    public double getConfidenceScore() {
        return confidenceScore != 0.0 ? confidenceScore : matchScore;
    }
    
    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
        this.matchScore = confidenceScore;
    }
    
    public boolean isRequiresInvestigation() {
        return requiresInvestigation;
    }
    
    /**
     * Sanctions match details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SanctionMatch {
        private String listName;
        private String entityName;
        private double matchScore;
        private String matchType;
        private String reason;
        private Map<String, String> matchedFields;
        private LocalDateTime listUpdateDate;
    }
}