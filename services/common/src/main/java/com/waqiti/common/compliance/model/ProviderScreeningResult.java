package com.waqiti.common.compliance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result from a specific sanctions provider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderScreeningResult {
    
    private String providerName;
    private boolean hasMatch;
    private boolean match;
    private double matchScore;
    private double confidenceScore;
    private List<OFACScreeningResult.SanctionMatch> matches;
    private List<SanctionsMatch> sanctionsMatches;
    private LocalDateTime screeningTime;
    private LocalDateTime screenedAt;
    private long responseTimeMs;
    private boolean success;
    private String errorMessage;
    private Map<String, Object> providerSpecificData;
    
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
    
    public void setScreenedAt(LocalDateTime screenedAt) {
        this.screenedAt = screenedAt;
        this.screeningTime = screenedAt;
    }
    
    public List<SanctionsMatch> getMatches() {
        return sanctionsMatches;
    }
    
    public void setMatches(List<SanctionsMatch> matches) {
        this.sanctionsMatches = matches;
    }
}