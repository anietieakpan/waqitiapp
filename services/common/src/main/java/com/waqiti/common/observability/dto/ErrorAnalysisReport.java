package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.waqiti.common.enums.ViolationSeverity;
import com.waqiti.common.enums.BusinessImpact;
import com.waqiti.common.enums.TrendDirection;
import com.waqiti.common.observability.dto.DataPoint;
import com.waqiti.common.observability.dto.ErrorTrend;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive error analysis report providing deep insights into system errors,
 * failure patterns, root cause analysis, and remediation recommendations for
 * the Waqiti platform with enterprise-grade error intelligence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorAnalysisReport {
    
    /**
     * Report metadata
     */
    private LocalDateTime generatedAt;
    private LocalDateTime timestamp; // Alias for generatedAt for compatibility
    private LocalDateTime analysisWindowStart;
    private LocalDateTime analysisWindowEnd;
    private String analysisWindow; // Human-readable analysis window description
    private String reportVersion;
    private String analyzedBy;
    
    /**
     * Error statistics overview
     */
    private long totalErrors;
    private long uniqueErrors;
    private double errorRate;
    private double errorGrowthRate;
    private ErrorSeverityDistribution severityDistribution;
    
    /**
     * Top error categories and patterns
     */
    private List<ErrorCategory> topErrorCategories;
    private List<ErrorInfo> topErrors; // Top error information for quick reference
    private List<ErrorPattern> identifiedPatterns;
    private List<ErrorHotspot> errorHotspots;
    private Map<String, Long> errorsByService;
    
    /**
     * Critical and high-impact errors
     */
    private List<CriticalError> criticalErrors;
    private List<FrequentError> mostFrequentErrors;
    private List<ErrorImpactAnalysis> highImpactErrors;
    private int businessCriticalErrors;
    
    /**
     * Error trends and temporal analysis
     */
    private ErrorTrendAnalysis trendAnalysis;
    private List<ErrorTrend> errorTrends; // List of error trends over time
    private Map<String, List<DataPoint>> hourlyErrorDistribution;
    private List<ErrorSpike> errorSpikes;
    private SeasonalityAnalysis seasonalityAnalysis;
    
    /**
     * Root cause analysis
     */
    private List<ErrorRootCauseAnalysis> rootCauseFindings;
    private Map<String, Integer> rootCauseCategories;
    private List<SystematicIssue> systematicIssues;
    private List<String> commonFailurePoints;
    
    /**
     * Error correlation and dependencies
     */
    private List<ErrorCorrelation> errorCorrelations;
    private List<CascadingFailure> cascadingFailures;
    private Map<String, List<String>> dependencyImpactMap;
    
    /**
     * Performance and reliability impact
     */
    private ReliabilityImpact reliabilityImpact;
    private UserExperienceImpact userExperienceImpact;
    private double meanTimeToDetection;
    private double meanTimeToResolution;
    
    /**
     * Prevention and remediation insights
     */
    private List<RemediationRecommendation> remediationRecommendations;
    private List<PreventiveMeasure> preventiveMeasures;
    private List<MonitoringImprovement> monitoringImprovements;
    private ErrorPredictiveAnalysis predictiveAnalysis;
    
    /**
     * Calculate overall error health score
     */
    public double calculateErrorHealthScore() {
        double score = 100.0;
        
        // Error rate impact (40%)
        if (errorRate > 10.0) score -= 40;
        else if (errorRate > 5.0) score -= 30;
        else if (errorRate > 2.0) score -= 20;
        else if (errorRate > 1.0) score -= 10;
        
        // Critical errors impact (30%)
        score -= Math.min(30.0, criticalErrors.size() * 5.0);
        
        // Business critical errors impact (20%)
        score -= Math.min(20.0, businessCriticalErrors * 10.0);
        
        // Error growth impact (10%)
        if (errorGrowthRate > 50.0) score -= 10;
        else if (errorGrowthRate > 25.0) score -= 7;
        else if (errorGrowthRate > 10.0) score -= 4;
        
        return Math.max(0.0, Math.min(100.0, score));
    }
    
    /**
     * Get immediate action items based on error analysis
     */
    public List<String> getImmediateActionItems() {
        List<String> actions = new java.util.ArrayList<>();
        
        // Critical errors requiring immediate attention
        if (!criticalErrors.isEmpty()) {
            actions.add(String.format("URGENT: Address %d critical errors causing system instability", 
                criticalErrors.size()));
        }
        
        // Business critical errors
        if (businessCriticalErrors > 0) {
            actions.add(String.format("PRIORITY: Resolve %d business-critical errors affecting revenue", 
                businessCriticalErrors));
        }
        
        // High error rate
        if (errorRate > 5.0) {
            actions.add(String.format("ERROR STORM: Error rate %.2f%% exceeds acceptable threshold", errorRate));
        }
        
        // Cascading failures
        if (!cascadingFailures.isEmpty()) {
            actions.add("STABILITY: Investigate cascading failures affecting multiple services");
        }
        
        // Error growth trend
        if (errorGrowthRate > 25.0) {
            actions.add(String.format("TREND: Error growth rate %.1f%% indicates deteriorating system health", 
                errorGrowthRate));
        }
        
        // Systematic issues
        if (!systematicIssues.isEmpty()) {
            actions.add("ARCHITECTURE: Address systematic issues affecting platform reliability");
        }
        
        return actions;
    }
    
    /**
     * Generate executive summary of error analysis
     */
    public String generateExecutiveSummary() {
        StringBuilder summary = new StringBuilder();
        
        double healthScore = calculateErrorHealthScore();
        
        summary.append(String.format("Error Analysis Summary - Health Score: %.1f/100\n", healthScore));
        summary.append(String.format("Analysis Period: %s to %s\n\n", analysisWindowStart, analysisWindowEnd));
        
        summary.append(String.format("Total Errors: %,d (%d unique patterns)\n", totalErrors, uniqueErrors));
        summary.append(String.format("Error Rate: %.2f%%", errorRate));
        
        if (errorGrowthRate != 0) {
            String trend = errorGrowthRate > 0 ? "increasing" : "decreasing";
            summary.append(String.format(" (%+.1f%% %s)\n", Math.abs(errorGrowthRate), trend));
        } else {
            summary.append(" (stable)\n");
        }
        
        if (criticalErrors.size() > 0) {
            summary.append(String.format("\nCRITICAL: %d critical errors requiring immediate attention\n", 
                criticalErrors.size()));
        }
        
        if (businessCriticalErrors > 0) {
            summary.append(String.format("BUSINESS IMPACT: %d errors affecting revenue or customer experience\n", 
                businessCriticalErrors));
        }
        
        if (meanTimeToDetection > 0) {
            summary.append(String.format("Detection Time: %.1f minutes average\n", meanTimeToDetection));
        }
        
        if (meanTimeToResolution > 0) {
            summary.append(String.format("Resolution Time: %.1f minutes average\n", meanTimeToResolution));
        }
        
        return summary.toString().trim();
    }
    
    /**
     * Get top error categories by frequency
     */
    public List<ErrorCategory> getTopErrorCategories(int limit) {
        if (topErrorCategories == null || topErrorCategories.isEmpty()) {
            return List.of();
        }
        
        return topErrorCategories.stream()
            .sorted((c1, c2) -> Long.compare(c2.getErrorCount(), c1.getErrorCount()))
            .limit(limit)
            .toList();
    }
    
    /**
     * Get most critical root causes requiring attention
     */
    public List<ErrorRootCauseAnalysis> getCriticalRootCauses() {
        if (rootCauseFindings == null) {
            return List.of();
        }
        
        return rootCauseFindings.stream()
            .filter(rca -> rca.getSeverity() == ViolationSeverity.CRITICAL || 
                          rca.getSeverity() == ViolationSeverity.HIGH)
            .sorted((r1, r2) -> Integer.compare(r2.getImpactScore(), r1.getImpactScore()))
            .toList();
    }
    
    /**
     * Generate detailed error analysis report
     */
    public String generateDetailedReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== ERROR ANALYSIS REPORT ===\n");
        report.append(String.format("Generated: %s\n", generatedAt));
        report.append(String.format("Analysis Window: %s to %s\n\n", analysisWindowStart, analysisWindowEnd));
        
        // Executive summary
        report.append("EXECUTIVE SUMMARY:\n");
        report.append(generateExecutiveSummary()).append("\n\n");
        
        // Error statistics
        report.append("ERROR STATISTICS:\n");
        report.append(String.format("- Total Errors: %,d\n", totalErrors));
        report.append(String.format("- Unique Error Types: %d\n", uniqueErrors));
        report.append(String.format("- Error Rate: %.2f%%\n", errorRate));
        report.append(String.format("- Growth Rate: %+.1f%%\n\n", errorGrowthRate));
        
        // Severity breakdown
        if (severityDistribution != null) {
            report.append("ERROR SEVERITY DISTRIBUTION:\n");
            report.append(String.format("- Critical: %d\n", severityDistribution.getCriticalCount()));
            report.append(String.format("- High: %d\n", severityDistribution.getHighCount()));
            report.append(String.format("- Medium: %d\n", severityDistribution.getMediumCount()));
            report.append(String.format("- Low: %d\n\n", severityDistribution.getLowCount()));
        }
        
        // Top error categories
        List<ErrorCategory> topCategories = getTopErrorCategories(5);
        if (!topCategories.isEmpty()) {
            report.append("TOP ERROR CATEGORIES:\n");
            for (int i = 0; i < topCategories.size(); i++) {
                ErrorCategory category = topCategories.get(i);
                report.append(String.format("%d. %s: %,d errors (%.1f%%)\n", 
                    i + 1, category.getName(), category.getErrorCount(), category.getPercentage()));
            }
            report.append("\n");
        }
        
        // Critical root causes
        List<ErrorRootCauseAnalysis> criticalRootCauses = getCriticalRootCauses();
        if (!criticalRootCauses.isEmpty()) {
            report.append("CRITICAL ROOT CAUSES:\n");
            for (int i = 0; i < Math.min(3, criticalRootCauses.size()); i++) {
                ErrorRootCauseAnalysis rca = criticalRootCauses.get(i);
                report.append(String.format("%d. %s (Impact: %d/100)\n", 
                    i + 1, rca.getDescription(), rca.getImpactScore()));
                report.append(String.format("   Recommendation: %s\n", rca.getRecommendation()));
            }
            report.append("\n");
        }
        
        // Immediate action items
        List<String> actions = getImmediateActionItems();
        if (!actions.isEmpty()) {
            report.append("IMMEDIATE ACTION ITEMS:\n");
            for (int i = 0; i < actions.size(); i++) {
                report.append(String.format("%d. %s\n", i + 1, actions.get(i)));
            }
        }
        
        return report.toString();
    }
    
    /**
     * Check if error situation requires escalation
     */
    public boolean requiresEscalation() {
        return criticalErrors.size() > 0 || 
               businessCriticalErrors > 0 || 
               errorRate > 10.0 || 
               errorGrowthRate > 50.0;
    }
}

/**
 * Supporting classes for comprehensive error analysis
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorSeverityDistribution {
    private int criticalCount;
    private int highCount;
    private int mediumCount;
    private int lowCount;
    private double criticalPercentage;
    private double highPercentage;
    private double mediumPercentage;
    private double lowPercentage;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorCategory {
    private String name;
    private String description;
    private long errorCount;
    private double percentage;
    private ViolationSeverity severity;
    private List<String> commonMessages;
    private List<String> affectedServices;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorPattern {
    private String patternId;
    private String description;
    private String regex;
    private int occurrences;
    private double confidence;
    private List<String> examples;
    private String suggestedFix;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorHotspot {
    private String component;
    private String location;
    private long errorCount;
    private double errorDensity;
    private List<String> topErrorTypes;
    private String recommendation;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CriticalError {
    private String errorId;
    private String message;
    private String service;
    private LocalDateTime firstOccurrence;
    private LocalDateTime lastOccurrence;
    private int occurrenceCount;
    private ViolationSeverity severity;
    private String stackTrace;
    private BusinessImpact businessImpact;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class FrequentError {
    private String errorType;
    private String message;
    private long count;
    private double frequency;
    private List<String> affectedEndpoints;
    private String suggestedFix;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorImpactAnalysis {
    private String errorType;
    private int userImpactScore;
    private int businessImpactScore;
    private int systemImpactScore;
    private String impactDescription;
    private List<String> affectedFeatures;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorTrendAnalysis {
    private TrendDirection overallTrend;
    private double trendSlope;
    private Map<String, TrendDirection> categoryTrends;
    private List<String> improvingCategories;
    private List<String> worseningCategories;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorSpike {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long errorCount;
    private double intensity;
    private String primaryCause;
    private List<String> affectedServices;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SeasonalityAnalysis {
    private Map<String, Double> hourlyPatterns;
    private Map<String, Double> dailyPatterns;
    private List<String> peakErrorPeriods;
    private String seasonalityStrength;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorRootCauseAnalysis {
    private String id;
    private String category;
    private String description;
    private ViolationSeverity severity;
    private int impactScore;
    private String evidence;
    private String recommendation;
    private String timeline;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SystematicIssue {
    private String issueType;
    private String description;
    private List<String> affectedComponents;
    private int occurrencePattern;
    private String rootCause;
    private String systemicFix;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorCorrelation {
    private String primaryError;
    private String correlatedError;
    private double correlationStrength;
    private String relationshipType;
    private String explanation;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class CascadingFailure {
    private String triggerEvent;
    private List<String> affectedServices;
    private String propagationPath;
    private LocalDateTime startTime;
    private String mitigation;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ReliabilityImpact {
    private double availabilityImpact;
    private double performanceImpact;
    private double dataIntegrityImpact;
    private String overallAssessment;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class UserExperienceImpact {
    private int affectedUsers;
    private double satisfactionImpact;
    private List<String> impactedUserJourneys;
    private String severityAssessment;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RemediationRecommendation {
    private String priority;
    private String action;
    private String rationale;
    private String estimatedEffort;
    private String expectedImpact;
    private String timeline;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PreventiveMeasure {
    private String measureType;
    private String description;
    private String implementation;
    private double effectivenessScore;
    private String costBenefit;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MonitoringImprovement {
    private String improvementType;
    private String description;
    private String implementation;
    private String expectedBenefit;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ErrorPredictiveAnalysis {
    private List<String> predictedErrorTypes;
    private Map<String, Double> errorProbabilities;
    private List<String> earlyWarningIndicators;
    private String recommendation;
}

