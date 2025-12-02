package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Result of email pattern analysis for fraud detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailPatternResult {
    
    private String email;
    private String localPart;
    private String domain;
    private PatternRiskLevel riskLevel;
    private double riskScore;
    private double patternScore;

    // Pattern analysis
    private boolean hasRandomPattern;
    private boolean hasNumberSequence;
    private boolean hasNumbers;
    private boolean hasNumericPattern; // Alias for hasNumbers
    private boolean hasRepeatingCharacters;
    private boolean hasSpecialCharacters;
    private boolean hasSpecialChars;
    private int length;
    private boolean suspiciousPattern;
    private boolean hasSuspiciousPattern; // Alias for suspiciousPattern
    private boolean hasTypicalFraudPatterns;
    private boolean matchesKnownFraudPatterns;
    
    // Structure analysis
    private int localPartLength;
    private int characterVariety;
    private boolean hasConsecutiveNumbers;
    private boolean hasConsecutiveLetters;
    private double entropyScore;
    private String patternType;
    
    // Behavioral indicators
    private boolean isGeneratedPattern;
    private boolean isHumanLikePattern;
    private boolean followsNamingConvention;
    private boolean hasPersonalizedElements;
    
    // Fraud indicators
    private List<String> matchedFraudPatterns;
    private List<String> suspiciousElements;
    private boolean isObfuscated;
    private boolean hasEvasionTechniques;
    
    // Similarity analysis
    private List<String> similarEmailsInDatabase;
    private int similarityCount;
    private double maxSimilarityScore;
    
    // Analysis metadata
    private LocalDateTime analyzedAt;
    private String analysisMethod;
    private Map<String, Object> patternDetails;
    private double confidence;
    private List<String> detectionReasons;
    
    /**
     * Check if email pattern appears legitimate
     */
    public boolean isLegitimatePattern() {
        return riskLevel == PatternRiskLevel.NORMAL || riskLevel == PatternRiskLevel.LOW;
    }
    
    /**
     * Check if pattern indicates bot/automated generation
     */
    public boolean indicatesAutomatedGeneration() {
        return hasRandomPattern && (entropyScore > 0.8 || hasNumberSequence);
    }

    /**
     * Check if pattern is suspicious
     */
    public boolean isSuspiciousPattern() {
        return suspiciousPattern || hasTypicalFraudPatterns;
    }
    
    /**
     * Check if pattern requires manual review
     */
    public boolean requiresManualReview() {
        return riskLevel == PatternRiskLevel.MEDIUM || hasTypicalFraudPatterns;
    }
    
    /**
     * Check if pattern should be automatically blocked
     */
    public boolean shouldBeBlocked() {
        return riskLevel == PatternRiskLevel.HIGH || 
               riskLevel == PatternRiskLevel.CRITICAL || 
               riskLevel == PatternRiskLevel.MALICIOUS;
    }
    
    /**
     * Get pattern complexity score
     */
    public double getComplexityScore() {
        double complexity = 0.0;
        complexity += characterVariety * 0.1;
        complexity += entropyScore * 0.4;
        if (hasSpecialCharacters) complexity += 0.2;
        if (hasPersonalizedElements) complexity += 0.3;
        return Math.min(complexity, 1.0);
    }
    
    /**
     * Generate pattern analysis summary
     */
    public String getPatternSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Email Pattern Analysis for: ").append(email).append("\n");
        summary.append("Risk Level: ").append(riskLevel).append(" (").append(String.format("%.2f", riskScore)).append(")\n");
        summary.append("Pattern Type: ").append(patternType).append("\n");
        summary.append("Entropy Score: ").append(String.format("%.2f", entropyScore)).append("\n");
        
        if (hasRandomPattern) summary.append("- Contains random patterns\n");
        if (hasTypicalFraudPatterns) summary.append("- Matches known fraud patterns\n");
        if (isGeneratedPattern) summary.append("- Appears to be machine-generated\n");
        if (similarityCount > 0) summary.append("- Similar emails found: ").append(similarityCount).append("\n");
        
        return summary.toString();
    }
}