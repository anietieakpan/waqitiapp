package com.waqiti.ml.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of pattern-based fraud analysis.
 * Identifies known fraud patterns in transaction data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternAnalysisResult {

    private LocalDateTime timestamp;

    // Pattern matching results
    @Builder.Default
    private List<String> matchedPatterns = new ArrayList<>();
    
    @Builder.Default
    private List<FraudPattern> detectedPatterns = new ArrayList<>();

    // Pattern scores
    private Double patternMatchScore; // 0.0 - 1.0
    private Double patternSeverityScore; // 0.0 - 1.0
    private Double overallPatternScore; // 0.0 - 1.0

    // Specific pattern types
    private Boolean structuringDetected; // Breaking up large amounts
    private Boolean rapidFireDetected; // Rapid succession of transactions
    private Boolean roundAmountDetected; // Suspiciously round amounts
    private Boolean testingPatternDetected; // Small test transactions
    private Boolean accountTakeoverPattern; // ATO indicators
    private Boolean muleAccountPattern; // Money mule indicators

    // Pattern details
    private Integer patternsDetected;
    private Integer highSeverityPatterns;
    private String primaryPattern; // Most significant pattern detected

    // Historical pattern analysis
    private Boolean repeatOffender; // User has history of fraud patterns
    private Double patternFrequency; // How often user exhibits these patterns

    /**
     * Check if high-risk patterns detected
     */
    public boolean hasHighRiskPatterns() {
        return highSeverityPatterns != null && highSeverityPatterns > 0;
    }

    /**
     * Add a matched pattern
     */
    public void addMatchedPattern(String pattern) {
        if (matchedPatterns == null) {
            matchedPatterns = new ArrayList<>();
        }
        matchedPatterns.add(pattern);
    }
}
