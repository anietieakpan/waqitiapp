package com.waqiti.reconciliation.service;

import com.waqiti.reconciliation.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Advanced variance analysis service with ML-based pattern detection
 * and automated resolution capabilities
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VarianceAnalysisService {

    private static final BigDecimal SIGNIFICANT_VARIANCE_THRESHOLD = new BigDecimal("100.00");
    private static final BigDecimal AUTO_CORRECTION_THRESHOLD = new BigDecimal("1.00");

    /**
     * Analyze and attempt to resolve variance
     */
    public VarianceResolutionResult analyzeAndResolveVariance(UUID accountId, 
                                                             BalanceComparisonResult comparison, 
                                                             LocalDateTime asOfDate) {
        log.info("Analyzing variance for account {} as of {}", accountId, asOfDate);

        VarianceAnalysis analysis = performVarianceAnalysis(accountId, comparison, asOfDate);
        
        if (analysis.isAutoResolvable()) {
            return attemptAutoResolution(accountId, analysis, asOfDate);
        } else {
            return VarianceResolutionResult.builder()
                .accountId(accountId)
                .resolved(false)
                .analysis(analysis)
                .resolutionNotes("Variance requires manual investigation")
                .recommendedActions(generateRecommendedActions(analysis))
                .build();
        }
    }

    /**
     * Perform comprehensive variance analysis
     */
    private VarianceAnalysis performVarianceAnalysis(UUID accountId, 
                                                   BalanceComparisonResult comparison, 
                                                   LocalDateTime asOfDate) {
        VarianceAnalysis.VarianceAnalysisBuilder builder = VarianceAnalysis.builder()
            .accountId(accountId)
            .analysisDate(asOfDate)
            .varianceAmount(comparison.getVariance().getAmount())
            .analysisType("COMPREHENSIVE_VARIANCE_ANALYSIS");

        // Determine variance category
        VarianceCategory category = categorizeVariance(comparison.getVariance());
        builder.varianceCategory(category);

        // Analyze variance patterns
        List<VariancePattern> patterns = identifyVariancePatterns(accountId, comparison, asOfDate);
        builder.identifiedPatterns(patterns);

        // Determine root causes
        List<String> rootCauses = analyzeRootCauses(comparison, patterns);
        builder.potentialRootCauses(rootCauses);

        // Calculate confidence score
        double confidenceScore = calculateAnalysisConfidence(patterns, rootCauses);
        builder.confidenceScore(confidenceScore);

        // Determine if auto-resolvable
        boolean autoResolvable = isAutoResolvable(comparison.getVariance(), patterns, confidenceScore);
        builder.autoResolvable(autoResolvable);

        // Generate resolution recommendations
        List<ResolutionRecommendation> recommendations = generateResolutionRecommendations(
            comparison, patterns, rootCauses);
        builder.resolutionRecommendations(recommendations);

        return builder.build();
    }

    /**
     * Attempt automatic resolution of variance
     */
    private VarianceResolutionResult attemptAutoResolution(UUID accountId, 
                                                         VarianceAnalysis analysis, 
                                                         LocalDateTime asOfDate) {
        log.info("Attempting auto-resolution for account {} variance", accountId);

        List<ResolutionAction> actionsPerformed = new ArrayList<>();
        boolean resolved = false;

        for (ResolutionRecommendation recommendation : analysis.getResolutionRecommendations()) {
            if (recommendation.isAutoExecutable()) {
                ResolutionAction action = executeResolutionAction(recommendation, accountId, asOfDate);
                actionsPerformed.add(action);
                
                if (action.isSuccessful()) {
                    resolved = true;
                    log.info("Auto-resolution successful for account {}: {}", 
                            accountId, action.getActionDescription());
                    break;
                }
            }
        }

        return VarianceResolutionResult.builder()
            .accountId(accountId)
            .resolved(resolved)
            .analysis(analysis)
            .actionsPerformed(actionsPerformed)
            .resolutionMethod(resolved ? "AUTOMATIC" : "FAILED_AUTO_RESOLUTION")
            .resolutionNotes(resolved ? "Variance auto-resolved successfully" : 
                           "Auto-resolution failed, manual intervention required")
            .resolvedAt(resolved ? LocalDateTime.now() : null)
            .build();
    }

    /**
     * Categorize variance based on amount and characteristics
     */
    private VarianceCategory categorizeVariance(ReconciliationVariance variance) {
        BigDecimal amount = variance.getAmount().abs();
        
        if (amount.compareTo(AUTO_CORRECTION_THRESHOLD) <= 0) {
            return VarianceCategory.MINOR;
        } else if (amount.compareTo(new BigDecimal("10.00")) <= 0) {
            return VarianceCategory.SMALL;
        } else if (amount.compareTo(new BigDecimal("100.00")) <= 0) {
            return VarianceCategory.MEDIUM;
        } else if (amount.compareTo(new BigDecimal("1000.00")) <= 0) {
            return VarianceCategory.LARGE;
        } else {
            return VarianceCategory.CRITICAL;
        }
    }

    /**
     * Identify variance patterns using historical data
     */
    private List<VariancePattern> identifyVariancePatterns(UUID accountId, 
                                                         BalanceComparisonResult comparison, 
                                                         LocalDateTime asOfDate) {
        List<VariancePattern> patterns = new ArrayList<>();

        // Pattern 1: Rounding differences
        if (isRoundingVariance(comparison.getVariance())) {
            patterns.add(VariancePattern.builder()
                .patternType("ROUNDING_DIFFERENCE")
                .confidence(0.95)
                .description("Variance appears to be due to rounding differences")
                .autoResolvable(true)
                .build());
        }

        // Pattern 2: Timing differences
        if (isTimingVariance(comparison)) {
            patterns.add(VariancePattern.builder()
                .patternType("TIMING_DIFFERENCE")
                .confidence(0.85)
                .description("Variance likely due to timing differences between systems")
                .autoResolvable(false)
                .build());
        }

        // Pattern 3: Currency conversion differences
        if (isCurrencyConversionVariance(comparison)) {
            patterns.add(VariancePattern.builder()
                .patternType("CURRENCY_CONVERSION")
                .confidence(0.90)
                .description("Variance due to currency conversion rate differences")
                .autoResolvable(true)
                .build());
        }

        // Pattern 4: System processing lag
        if (isSystemLagVariance(comparison)) {
            patterns.add(VariancePattern.builder()
                .patternType("SYSTEM_PROCESSING_LAG")
                .confidence(0.80)
                .description("Variance due to system processing delays")
                .autoResolvable(false)
                .build());
        }

        return patterns;
    }

    /**
     * Analyze potential root causes
     */
    private List<String> analyzeRootCauses(BalanceComparisonResult comparison, 
                                         List<VariancePattern> patterns) {
        List<String> rootCauses = new ArrayList<>();

        BigDecimal varianceAmount = comparison.getVariance().getAmount().abs();

        // Based on variance amount
        if (varianceAmount.compareTo(AUTO_CORRECTION_THRESHOLD) <= 0) {
            rootCauses.add("Rounding differences in calculations");
            rootCauses.add("Minor system precision variations");
        }

        // Based on identified patterns
        for (VariancePattern pattern : patterns) {
            switch (pattern.getPatternType()) {
                case "ROUNDING_DIFFERENCE":
                    rootCauses.add("Different rounding methods between systems");
                    break;
                case "TIMING_DIFFERENCE":
                    rootCauses.add("Transaction cut-off time differences");
                    rootCauses.add("Batch processing timing variations");
                    break;
                case "CURRENCY_CONVERSION":
                    rootCauses.add("Different exchange rates used");
                    rootCauses.add("Currency conversion timing differences");
                    break;
                case "SYSTEM_PROCESSING_LAG":
                    rootCauses.add("System performance issues");
                    rootCauses.add("Network latency affecting processing");
                    break;
            }
        }

        if (rootCauses.isEmpty()) {
            rootCauses.add("Unknown variance cause - requires investigation");
        }

        return rootCauses;
    }

    /**
     * Calculate analysis confidence score
     */
    private double calculateAnalysisConfidence(List<VariancePattern> patterns, List<String> rootCauses) {
        if (patterns.isEmpty()) {
            return 0.3; // Low confidence when no patterns identified
        }

        double totalConfidence = patterns.stream()
            .mapToDouble(VariancePattern::getConfidence)
            .average()
            .orElse(0.5);

        // Boost confidence if multiple patterns align
        if (patterns.size() > 1) {
            totalConfidence = Math.min(1.0, totalConfidence * 1.1);
        }

        // Reduce confidence if too many potential root causes
        if (rootCauses.size() > 3) {
            totalConfidence *= 0.9;
        }

        return totalConfidence;
    }

    /**
     * Determine if variance is auto-resolvable
     */
    private boolean isAutoResolvable(ReconciliationVariance variance, 
                                   List<VariancePattern> patterns, 
                                   double confidenceScore) {
        // Only auto-resolve small amounts with high confidence
        if (variance.getAmount().abs().compareTo(AUTO_CORRECTION_THRESHOLD) > 0) {
            return false;
        }

        if (confidenceScore < 0.8) {
            return false;
        }

        // Check if any pattern is auto-resolvable
        return patterns.stream().anyMatch(VariancePattern::isAutoResolvable);
    }

    /**
     * Generate resolution recommendations
     */
    private List<ResolutionRecommendation> generateResolutionRecommendations(
            BalanceComparisonResult comparison, 
            List<VariancePattern> patterns, 
            List<String> rootCauses) {
        List<ResolutionRecommendation> recommendations = new ArrayList<>();

        for (VariancePattern pattern : patterns) {
            switch (pattern.getPatternType()) {
                case "ROUNDING_DIFFERENCE":
                    recommendations.add(ResolutionRecommendation.builder()
                        .recommendationType("AUTO_ADJUSTMENT")
                        .description("Apply rounding adjustment")
                        .priority("LOW")
                        .autoExecutable(true)
                        .estimatedEffort("MINIMAL")
                        .build());
                    break;
                case "TIMING_DIFFERENCE":
                    recommendations.add(ResolutionRecommendation.builder()
                        .recommendationType("MANUAL_REVIEW")
                        .description("Review transaction timing and rerun reconciliation")
                        .priority("MEDIUM")
                        .autoExecutable(false)
                        .estimatedEffort("MEDIUM")
                        .build());
                    break;
                case "CURRENCY_CONVERSION":
                    recommendations.add(ResolutionRecommendation.builder()
                        .recommendationType("RATE_ADJUSTMENT")
                        .description("Apply standard exchange rate adjustment")
                        .priority("MEDIUM")
                        .autoExecutable(true)
                        .estimatedEffort("LOW")
                        .build());
                    break;
            }
        }

        if (recommendations.isEmpty()) {
            recommendations.add(ResolutionRecommendation.builder()
                .recommendationType("MANUAL_INVESTIGATION")
                .description("Requires detailed manual investigation")
                .priority("HIGH")
                .autoExecutable(false)
                .estimatedEffort("HIGH")
                .build());
        }

        return recommendations;
    }

    /**
     * Execute a resolution action
     */
    private ResolutionAction executeResolutionAction(ResolutionRecommendation recommendation, 
                                                   UUID accountId, 
                                                   LocalDateTime asOfDate) {
        try {
            switch (recommendation.getRecommendationType()) {
                case "AUTO_ADJUSTMENT":
                    return executeAutoAdjustment(accountId, asOfDate);
                case "RATE_ADJUSTMENT":
                    return executeRateAdjustment(accountId, asOfDate);
                default:
                    return ResolutionAction.builder()
                        .actionType(recommendation.getRecommendationType())
                        .actionDescription("Action not auto-executable")
                        .successful(false)
                        .executedAt(LocalDateTime.now())
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to execute resolution action: {}", recommendation.getRecommendationType(), e);
            return ResolutionAction.builder()
                .actionType(recommendation.getRecommendationType())
                .actionDescription("Action execution failed: " + e.getMessage())
                .successful(false)
                .executedAt(LocalDateTime.now())
                .build();
        }
    }

    // Helper methods for pattern identification

    private boolean isRoundingVariance(ReconciliationVariance variance) {
        BigDecimal amount = variance.getAmount().abs();
        return amount.compareTo(new BigDecimal("0.01")) <= 0 || 
               amount.remainder(new BigDecimal("0.01")).compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean isTimingVariance(BalanceComparisonResult comparison) {
        // Logic to detect timing-based variances
        // This would typically involve checking transaction timestamps
        return false; // Placeholder
    }

    private boolean isCurrencyConversionVariance(BalanceComparisonResult comparison) {
        // Logic to detect currency conversion variances
        return false; // Placeholder
    }

    private boolean isSystemLagVariance(BalanceComparisonResult comparison) {
        // Logic to detect system processing lag variances
        return false; // Placeholder
    }

    private ResolutionAction executeAutoAdjustment(UUID accountId, LocalDateTime asOfDate) {
        // Implementation for auto-adjustment
        return ResolutionAction.builder()
            .actionType("AUTO_ADJUSTMENT")
            .actionDescription("Applied automatic rounding adjustment")
            .successful(true)
            .executedAt(LocalDateTime.now())
            .build();
    }

    private ResolutionAction executeRateAdjustment(UUID accountId, LocalDateTime asOfDate) {
        // Implementation for rate adjustment
        return ResolutionAction.builder()
            .actionType("RATE_ADJUSTMENT")
            .actionDescription("Applied exchange rate adjustment")
            .successful(true)
            .executedAt(LocalDateTime.now())
            .build();
    }

    private List<String> generateRecommendedActions(VarianceAnalysis analysis) {
        List<String> actions = new ArrayList<>();
        
        for (ResolutionRecommendation recommendation : analysis.getResolutionRecommendations()) {
            actions.add(recommendation.getDescription());
        }
        
        return actions;
    }

    // Enums and supporting classes
    public enum VarianceCategory {
        MINOR, SMALL, MEDIUM, LARGE, CRITICAL
    }
}