package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive pattern-based risk scoring with advanced analytics
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class PatternRiskScore {
    
    private String scoreId;
    private String transactionId;
    private String userId;
    private String sessionId;
    
    // Overall pattern risk assessment
    private Double overallPatternScore;
    private Double score; // Alias for overallPatternScore for compatibility
    private FraudRiskLevel riskLevel;
    private String dominantPattern;
    private String riskCategory;
    
    // Individual pattern scores
    private Double velocityPatternScore;
    private Double amountPatternScore;
    private Double temporalPatternScore;
    private Double behavioralPatternScore;
    private Double networkPatternScore;
    private Double devicePatternScore;
    private Double locationPatternScore;
    private Double frequencyPatternScore;
    
    // Pattern detection results
    private List<FraudPattern> detectedPatterns;
    private Integer totalPatternsDetected;
    private Integer highRiskPatterns;
    private Integer mediumRiskPatterns;
    private Integer lowRiskPatterns;
    
    // Scoring metadata
    private LocalDateTime calculatedAt;
    private String scoringMethod;
    private String modelVersion;
    private Long calculationDurationMs;
    private Double confidence;
    
    // Historical pattern analysis
    private Integer historicalPatternCount;
    private LocalDateTime earliestPatternDetection;
    private LocalDateTime latestPatternDetection;
    private Double patternEvolutionScore;
    private Boolean isPersistentPattern;
    
    // Pattern correlation analysis
    private Map<String, Double> patternCorrelations;
    private List<String> correlatedPatterns;
    private Double patternComplexityScore;
    private Boolean isMultiLayerPattern;
    
    // Temporal analysis
    private String timeWindowAnalyzed;
    private Map<String, Integer> patternsPerTimeframe;
    private Double seasonalityScore;
    private Boolean hasRecurringPattern;
    
    // Risk escalation factors
    private List<String> escalationTriggers;
    private Boolean triggersRegulatory;
    private Boolean triggersCompliance;
    private Boolean triggersLawEnforcement;
    
    // Additional risk factors
    private Map<String, Double> customRiskFactors;
    private List<String> mitigatingFactors;
    private List<String> aggravatingFactors;
    
    /**
     * Get score value (returns score or overallPatternScore)
     */
    public Double getScore() {
        return score != null ? score : overallPatternScore;
    }

    /**
     * Calculate comprehensive pattern risk score using weighted components
     */
    public double calculateWeightedPatternScore() {
        double score = 0.0;
        double totalWeight = 0.0;
        
        // Velocity patterns (highest weight - immediate risk)
        if (velocityPatternScore != null) {
            score += velocityPatternScore * 0.25;
            totalWeight += 0.25;
        }
        
        // Behavioral patterns (high weight - indicates intent)
        if (behavioralPatternScore != null) {
            score += behavioralPatternScore * 0.2;
            totalWeight += 0.2;
        }
        
        // Amount patterns (high weight - financial impact)
        if (amountPatternScore != null) {
            score += amountPatternScore * 0.18;
            totalWeight += 0.18;
        }
        
        // Network patterns (moderate-high weight - organized activity)
        if (networkPatternScore != null) {
            score += networkPatternScore * 0.15;
            totalWeight += 0.15;
        }
        
        // Temporal patterns (moderate weight - timing anomalies)
        if (temporalPatternScore != null) {
            score += temporalPatternScore * 0.1;
            totalWeight += 0.1;
        }
        
        // Device patterns (moderate weight - technical indicators)
        if (devicePatternScore != null) {
            score += devicePatternScore * 0.07;
            totalWeight += 0.07;
        }
        
        // Location patterns (lower weight - can be legitimate)
        if (locationPatternScore != null) {
            score += locationPatternScore * 0.05;
            totalWeight += 0.05;
        }
        
        // Normalize by actual weights used
        if (totalWeight > 0) {
            score = score / totalWeight;
        }
        
        // Apply pattern complexity multiplier
        if (patternComplexityScore != null) {
            score *= (1.0 + (patternComplexityScore * 0.2));
        }
        
        // Apply persistence multiplier for recurring patterns
        if (isPersistentPattern != null && isPersistentPattern) {
            score *= 1.3;
        }
        
        // Apply multi-layer pattern multiplier
        if (isMultiLayerPattern != null && isMultiLayerPattern) {
            score *= 1.4;
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Get the most significant pattern based on risk and confidence
     */
    public FraudPattern getMostSignificantPattern() {
        if (detectedPatterns == null || detectedPatterns.isEmpty()) {
            return null;
        }
        
        return detectedPatterns.stream()
            .max((p1, p2) -> {
                double score1 = p1.calculateRiskScore() * (p1.getConfidence() != null ? p1.getConfidence() : 0.5);
                double score2 = p2.calculateRiskScore() * (p2.getConfidence() != null ? p2.getConfidence() : 0.5);
                return Double.compare(score1, score2);
            })
            .orElse(null);
    }
    
    /**
     * Check if patterns indicate sophisticated fraud operation
     */
    public boolean indicatesSophisticatedFraud() {
        return (isMultiLayerPattern != null && isMultiLayerPattern) ||
               (patternComplexityScore != null && patternComplexityScore > 0.8) ||
               (correlatedPatterns != null && correlatedPatterns.size() > 3) ||
               (highRiskPatterns != null && highRiskPatterns > 2);
    }
    
    /**
     * Calculate pattern sophistication level
     */
    public SophisticationLevel calculateSophisticationLevel() {
        double sophisticationScore = 0.0;
        
        // Multi-layer patterns indicate sophistication
        if (isMultiLayerPattern != null && isMultiLayerPattern) {
            sophisticationScore += 0.3;
        }
        
        // Pattern complexity
        if (patternComplexityScore != null) {
            sophisticationScore += patternComplexityScore * 0.25;
        }
        
        // Number of correlated patterns
        if (correlatedPatterns != null) {
            sophisticationScore += Math.min(0.2, correlatedPatterns.size() * 0.05);
        }
        
        // Persistence over time
        if (isPersistentPattern != null && isPersistentPattern) {
            sophisticationScore += 0.15;
        }
        
        // Regulatory/compliance triggers
        if (triggersRegulatory != null && triggersRegulatory) {
            sophisticationScore += 0.1;
        }
        
        if (sophisticationScore > 0.8) return SophisticationLevel.ADVANCED;
        if (sophisticationScore > 0.6) return SophisticationLevel.INTERMEDIATE;
        if (sophisticationScore > 0.3) return SophisticationLevel.BASIC;
        return SophisticationLevel.SIMPLE;
    }
    
    /**
     * Sophistication levels for fraud patterns
     */
    public enum SophisticationLevel {
        SIMPLE,       // Basic fraud attempts
        BASIC,        // Some coordination or planning
        INTERMEDIATE, // Organized with multiple vectors
        ADVANCED      // Highly sophisticated, professional
    }
    
    /**
     * Generate comprehensive pattern analysis report
     */
    public String generatePatternAnalysisReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== PATTERN RISK ANALYSIS REPORT ===\n");
        report.append("Score ID: ").append(scoreId).append("\n");
        report.append("Overall Pattern Score: ").append(String.format("%.3f", overallPatternScore)).append("\n");
        report.append("Risk Level: ").append(riskLevel).append("\n");
        report.append("Sophistication: ").append(calculateSophisticationLevel()).append("\n\n");
        
        report.append("=== PATTERN BREAKDOWN ===\n");
        if (totalPatternsDetected != null && totalPatternsDetected > 0) {
            report.append("Total Patterns Detected: ").append(totalPatternsDetected).append("\n");
            if (highRiskPatterns != null && highRiskPatterns > 0) {
                report.append("High Risk Patterns: ").append(highRiskPatterns).append("\n");
            }
            if (dominantPattern != null) {
                report.append("Dominant Pattern: ").append(dominantPattern).append("\n");
            }
        }
        
        report.append("\n=== COMPONENT SCORES ===\n");
        if (velocityPatternScore != null && velocityPatternScore > 0.1) {
            report.append("Velocity Risk: ").append(String.format("%.3f", velocityPatternScore)).append("\n");
        }
        if (behavioralPatternScore != null && behavioralPatternScore > 0.1) {
            report.append("Behavioral Risk: ").append(String.format("%.3f", behavioralPatternScore)).append("\n");
        }
        if (amountPatternScore != null && amountPatternScore > 0.1) {
            report.append("Amount Risk: ").append(String.format("%.3f", amountPatternScore)).append("\n");
        }
        if (networkPatternScore != null && networkPatternScore > 0.1) {
            report.append("Network Risk: ").append(String.format("%.3f", networkPatternScore)).append("\n");
        }
        
        if (indicatesSophisticatedFraud()) {
            report.append("\n=== SOPHISTICATION INDICATORS ===\n");
            if (isMultiLayerPattern != null && isMultiLayerPattern) {
                report.append("• Multi-layer pattern detected\n");
            }
            if (patternComplexityScore != null && patternComplexityScore > 0.8) {
                report.append("• High complexity score: ").append(String.format("%.3f", patternComplexityScore)).append("\n");
            }
            if (correlatedPatterns != null && correlatedPatterns.size() > 3) {
                report.append("• Multiple correlated patterns: ").append(correlatedPatterns.size()).append("\n");
            }
        }
        
        if (escalationTriggers != null && !escalationTriggers.isEmpty()) {
            report.append("\n=== ESCALATION TRIGGERS ===\n");
            escalationTriggers.forEach(trigger -> 
                report.append("• ").append(trigger).append("\n"));
        }
        
        return report.toString();
    }
    
    /**
     * Get recommended actions based on pattern analysis
     */
    public List<String> getRecommendedActions() {
        double score = overallPatternScore != null ? overallPatternScore : 0.0;
        
        if (triggersLawEnforcement != null && triggersLawEnforcement) {
            return List.of("NOTIFY_LAW_ENFORCEMENT", "FREEZE_ALL_ACCOUNTS", "PRESERVE_EVIDENCE", "GENERATE_SAR");
        }
        
        if (triggersCompliance != null && triggersCompliance) {
            return List.of("NOTIFY_COMPLIANCE", "GENERATE_SAR", "FREEZE_ACCOUNT", "ESCALATE_TO_ANALYST");
        }
        
        if (indicatesSophisticatedFraud() || score > 0.8) {
            return List.of("ESCALATE_TO_SPECIALIST", "FREEZE_ACCOUNT", "BLOCK_TRANSACTION", 
                          "ENABLE_ENHANCED_MONITORING", "NOTIFY_COMPLIANCE");
        }
        
        if (score > 0.6) {
            return List.of("ESCALATE_TO_ANALYST", "REQUIRE_ADDITIONAL_AUTH", 
                          "ENABLE_ENHANCED_MONITORING", "LIMIT_TRANSACTION_AMOUNT");
        }
        
        if (score > 0.4) {
            return List.of("ENABLE_ENHANCED_MONITORING", "LOG_SECURITY_EVENT", "SEND_SECURITY_ALERT");
        }
        
        return List.of("LOG_SECURITY_EVENT");
    }
    
    /**
     * Check if scoring confidence is sufficient for decision making
     */
    public boolean hasSufficientConfidence() {
        return confidence != null && confidence > 0.7;
    }
}