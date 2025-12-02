package com.waqiti.common.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive blacklist risk scoring with detailed analysis
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistRiskScore {
    
    private String scoreId;
    private String transactionId;
    private String userId;
    private String sessionId;
    
    // Overall risk assessment
    private Double overallRiskScore;
    private FraudRiskLevel riskLevel;
    private String riskCategory;
    private String primaryReason;
    
    // Component scores
    private Double ipBlacklistScore;
    private Double emailBlacklistScore;
    private Double deviceBlacklistScore;
    private Double accountBlacklistScore;
    private Double merchantBlacklistScore;
    private Double phoneBlacklistScore;
    private Double addressBlacklistScore;
    
    // Match details
    private List<BlacklistMatch> matches;
    private Integer totalMatches;
    private Integer highRiskMatches;
    private Integer mediumRiskMatches;
    private Integer lowRiskMatches;
    
    // Scoring metadata
    private LocalDateTime calculatedAt;
    private String calculationMethod;
    private String modelVersion;
    private Long calculationDurationMs;
    private Double confidence;
    
    // Blacklist coverage
    private List<String> checkedBlacklists;
    private List<String> availableBlacklists;
    private List<String> failedBlacklists;
    private Double coveragePercentage;
    
    // Historical context
    private Integer previousViolations;
    private LocalDateTime lastViolation;
    private Double reputationScore;
    private Integer daysSinceLastViolation;
    
    // Geographic and temporal factors
    private String sourceCountry;
    private Boolean isHighRiskCountry;
    private Boolean isUnusualTimePattern;
    private Boolean isUnusualLocationPattern;
    
    // Additional risk factors
    private Map<String, Double> additionalFactors;
    private List<String> riskIndicators;
    private List<String> protectiveFactors;
    
    /**
     * Get the overall risk score
     */
    public Double getScore() {
        return overallRiskScore;
    }
    
    /**
     * Calculate weighted overall risk score from component scores
     */
    public double calculateWeightedRiskScore() {
        double score = 0.0;
        double totalWeight = 0.0;
        
        // Weight assignments for different blacklist types
        if (ipBlacklistScore != null) {
            score += ipBlacklistScore * 0.25;
            totalWeight += 0.25;
        }
        
        if (emailBlacklistScore != null) {
            score += emailBlacklistScore * 0.2;
            totalWeight += 0.2;
        }
        
        if (deviceBlacklistScore != null) {
            score += deviceBlacklistScore * 0.15;
            totalWeight += 0.15;
        }
        
        if (accountBlacklistScore != null) {
            score += accountBlacklistScore * 0.15;
            totalWeight += 0.15;
        }
        
        if (merchantBlacklistScore != null) {
            score += merchantBlacklistScore * 0.1;
            totalWeight += 0.1;
        }
        
        if (phoneBlacklistScore != null) {
            score += phoneBlacklistScore * 0.08;
            totalWeight += 0.08;
        }
        
        if (addressBlacklistScore != null) {
            score += addressBlacklistScore * 0.07;
            totalWeight += 0.07;
        }
        
        // Normalize by total weight
        if (totalWeight > 0) {
            score = score / totalWeight;
        }
        
        // Apply historical context adjustments
        if (previousViolations != null && previousViolations > 0) {
            double historyMultiplier = 1.0 + (previousViolations * 0.1);
            score *= historyMultiplier;
        }
        
        // Apply geographic risk adjustments
        if (isHighRiskCountry != null && isHighRiskCountry) {
            score *= 1.2;
        }
        
        // Apply temporal pattern adjustments
        if (isUnusualTimePattern != null && isUnusualTimePattern) {
            score *= 1.1;
        }
        
        if (isUnusualLocationPattern != null && isUnusualLocationPattern) {
            score *= 1.15;
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Get the highest risk match from all matches
     */
    public BlacklistMatch getHighestRiskMatch() {
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        
        return matches.stream()
            .max((m1, m2) -> Double.compare(
                m1.calculateOverallScore(), 
                m2.calculateOverallScore()
            ))
            .orElse(null);
    }
    
    /**
     * Check if score indicates immediate action required
     */
    public boolean requiresImmediateAction() {
        return overallRiskScore != null && overallRiskScore > 0.8 ||
               riskLevel == FraudRiskLevel.CRITICAL ||
               (highRiskMatches != null && highRiskMatches > 0);
    }
    
    /**
     * Generate detailed risk assessment summary
     */
    public String generateRiskSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append("Blacklist Risk Assessment:\n");
        summary.append("Overall Score: ").append(String.format("%.3f", overallRiskScore)).append("\n");
        summary.append("Risk Level: ").append(riskLevel).append("\n");
        
        if (totalMatches != null && totalMatches > 0) {
            summary.append("Total Matches: ").append(totalMatches);
            if (highRiskMatches != null && highRiskMatches > 0) {
                summary.append(" (").append(highRiskMatches).append(" high-risk)");
            }
            summary.append("\n");
        }
        
        if (primaryReason != null) {
            summary.append("Primary Risk Factor: ").append(primaryReason).append("\n");
        }
        
        // Component breakdowns
        if (ipBlacklistScore != null && ipBlacklistScore > 0.3) {
            summary.append("IP Risk: ").append(String.format("%.3f", ipBlacklistScore)).append("\n");
        }
        
        if (emailBlacklistScore != null && emailBlacklistScore > 0.3) {
            summary.append("Email Risk: ").append(String.format("%.3f", emailBlacklistScore)).append("\n");
        }
        
        if (deviceBlacklistScore != null && deviceBlacklistScore > 0.3) {
            summary.append("Device Risk: ").append(String.format("%.3f", deviceBlacklistScore)).append("\n");
        }
        
        // Historical context
        if (previousViolations != null && previousViolations > 0) {
            summary.append("Previous Violations: ").append(previousViolations);
            if (daysSinceLastViolation != null) {
                summary.append(" (").append(daysSinceLastViolation).append(" days ago)");
            }
            summary.append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * Get recommended actions based on risk score and matches
     */
    public List<String> getRecommendedActions() {
        if (requiresImmediateAction()) {
            return List.of("BLOCK_TRANSACTION", "FREEZE_ACCOUNT", "ESCALATE_TO_ANALYST", "NOTIFY_COMPLIANCE");
        }
        
        if (overallRiskScore != null && overallRiskScore > 0.6) {
            return List.of("REQUIRE_ADDITIONAL_AUTH", "ENABLE_ENHANCED_MONITORING", "LOG_SECURITY_EVENT");
        }
        
        if (overallRiskScore != null && overallRiskScore > 0.4) {
            return List.of("ENABLE_ENHANCED_MONITORING", "LOG_SECURITY_EVENT");
        }
        
        return List.of("LOG_SECURITY_EVENT");
    }
    
    /**
     * Check if blacklist coverage is adequate for reliable scoring
     */
    public boolean hasAdequateCoverage() {
        return coveragePercentage != null && coveragePercentage > 0.8;
    }
    
    /**
     * Get confidence level in the risk assessment
     */
    public String getConfidenceLevel() {
        if (confidence == null) return "UNKNOWN";
        
        if (confidence > 0.9) return "VERY_HIGH";
        if (confidence > 0.8) return "HIGH";
        if (confidence > 0.6) return "MEDIUM";
        if (confidence > 0.4) return "LOW";
        return "VERY_LOW";
    }
}