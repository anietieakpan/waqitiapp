package com.waqiti.common.observability.dto;

import lombok.Builder;
import lombok.Data;

import com.waqiti.common.enums.TrendDirection;
import com.waqiti.common.observability.dto.DataPoint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Represents a performance-related alert with detailed context and analysis
 * Used for monitoring system performance degradation and optimization opportunities
 */
@Data
@Builder
public class PerformanceAlert {
    
    private String id;
    private String title;
    private String description;
    private String message; // Alert message for display/notification
    private PerformanceAlertType type;
    private PerformanceAlertSeverity severity;
    private PerformanceAlertStatus status;
    private LocalDateTime timestamp;
    private LocalDateTime lastUpdated;
    
    // Performance context
    private String metricName;
    private double currentValue;
    private double thresholdValue;
    private double threshold; // Alias for thresholdValue
    private double baselineValue;
    private String unit;
    private String component;
    private String endpoint;
    private String service;
    
    // Alert conditions
    private AlertCondition condition;
    private int evaluationWindowMinutes;
    private int consecutiveBreaches;
    private double percentageChange;
    private boolean isFlapping;
    
    // Impact assessment
    private ImpactLevel impactLevel;
    private List<String> affectedFeatures;
    private long affectedUserCount;
    private double businessImpactScore;
    private String customerFacingImpact;
    
    // Root cause analysis
    private List<String> potentialCauses;
    private List<String> correlatedMetrics;
    private Map<String, Double> relatedMetricValues;
    private String rootCauseAnalysis;
    
    // Resolution guidance
    private List<String> recommendedActions;
    private String escalationPath;
    private int estimatedResolutionTimeMinutes;
    private List<String> historicalResolutions;
    
    // Trend analysis
    private List<DataPoint> metricTrend;
    private TrendDirection trendDirection;
    private double trendSlope;
    private boolean isAnomalous;
    
    // Alert lifecycle
    private LocalDateTime acknowledgedAt;
    private String acknowledgedBy;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    private String resolutionSummary;
    private boolean autoResolved;
    
    /**
     * Check if alert requires immediate escalation based on severity and duration
     */
    public boolean requiresEscalation() {
        if (status == PerformanceAlertStatus.RESOLVED || status == PerformanceAlertStatus.SUPPRESSED) {
            return false;
        }
        
        long ageMinutes = getAgeInMinutes();
        
        return switch (severity) {
            case CRITICAL -> acknowledgedAt == null && ageMinutes > 5;
            case HIGH -> acknowledgedAt == null && ageMinutes > 15;
            case MEDIUM -> acknowledgedAt == null && ageMinutes > 30;
            case LOW -> acknowledgedAt == null && ageMinutes > 60;
        };
    }
    
    /**
     * Calculate alert priority score for sorting and triage
     */
    public int getPriorityScore() {
        int score = severity.getPriority() * 100;
        
        // Add urgency based on business impact
        score += (int) (businessImpactScore * 50);
        
        // Add urgency based on affected users
        if (affectedUserCount > 10000) score += 75;
        else if (affectedUserCount > 1000) score += 50;
        else if (affectedUserCount > 100) score += 25;
        
        // Add urgency for customer-facing issues
        if (customerFacingImpact != null && !customerFacingImpact.isEmpty()) score += 40;
        
        // Add urgency for unacknowledged alerts
        if (acknowledgedAt == null) score += 30;
        
        // Add urgency based on trend
        if (trendDirection == TrendDirection.WORSENING) score += 25;
        
        // Add urgency for flapping alerts
        if (isFlapping) score += 20;
        
        return score;
    }
    
    /**
     * Get age of alert in minutes
     */
    public long getAgeInMinutes() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).toMinutes();
    }
    
    /**
     * Check if alert represents a significant performance degradation
     */
    public boolean isSignificantDegradation() {
        if (baselineValue == 0) return false;
        
        double degradationPercentage = Math.abs(percentageChange);
        
        return switch (type) {
            case RESPONSE_TIME -> degradationPercentage > 50 && currentValue > 1000; // >50% increase and >1s
            case ERROR_RATE -> degradationPercentage > 100 && currentValue > 5; // >100% increase and >5%
            case THROUGHPUT -> degradationPercentage > 30 && trendDirection == TrendDirection.DECREASING; // >30% decrease
            case CPU_USAGE, MEMORY_USAGE -> currentValue > 80; // >80% utilization
            case DATABASE_QUERY_TIME -> degradationPercentage > 100 && currentValue > 500; // >100% increase and >500ms
            case QUEUE_DEPTH -> currentValue > 1000; // >1000 queued items
            default -> degradationPercentage > 25; // Default 25% threshold
        };
    }
    
    /**
     * Generate contextual alert message with detailed information
     */
    public String getContextualMessage() {
        StringBuilder message = new StringBuilder();
        message.append(String.format("%s alert: %s\n", severity.getDisplayName(), title));
        message.append(String.format("Current: %s %s (Threshold: %s %s)\n", 
            formatValue(currentValue), unit != null ? unit : "", 
            formatValue(thresholdValue), unit != null ? unit : ""));
        
        if (baselineValue > 0) {
            message.append(String.format("Change from baseline: %.1f%%\n", percentageChange));
        }
        
        if (affectedUserCount > 0) {
            message.append(String.format("Affected users: %,d\n", affectedUserCount));
        }
        
        if (customerFacingImpact != null && !customerFacingImpact.isEmpty()) {
            message.append(String.format("Customer impact: %s\n", customerFacingImpact));
        }
        
        if (!potentialCauses.isEmpty()) {
            message.append("Potential causes: ").append(String.join(", ", potentialCauses)).append("\n");
        }
        
        if (!recommendedActions.isEmpty()) {
            message.append("Recommended actions: ").append(String.join(", ", recommendedActions));
        }
        
        return message.toString().trim();
    }
    
    /**
     * Check if alert should be auto-resolved based on current conditions
     */
    public boolean shouldAutoResolve() {
        if (status != PerformanceAlertStatus.ACTIVE) return false;
        if (autoResolved) return false; // Already auto-resolved once
        
        // Auto-resolve if metric has been below threshold for evaluation window
        if (condition == AlertCondition.ABOVE_THRESHOLD && currentValue < thresholdValue) {
            return getAgeInMinutes() >= evaluationWindowMinutes;
        }
        
        if (condition == AlertCondition.BELOW_THRESHOLD && currentValue > thresholdValue) {
            return getAgeInMinutes() >= evaluationWindowMinutes;
        }
        
        return false;
    }
    
    /**
     * Get performance impact summary
     */
    public String getImpactSummary() {
        if (impactLevel == null) return "Impact assessment pending";
        
        return switch (impactLevel) {
            case CRITICAL -> "Critical impact - Service unavailable or severely degraded";
            case HIGH -> "High impact - Significant performance degradation affecting users";
            case MEDIUM -> "Medium impact - Noticeable performance issues for some users";
            case LOW -> "Low impact - Minor performance degradation";
            case NONE -> "No user-facing impact detected";
        };
    }
    
    private String formatValue(double value) {
        if (value >= 1000000) {
            return String.format("%.1fM", value / 1000000);
        } else if (value >= 1000) {
            return String.format("%.1fK", value / 1000);
        } else {
            return String.format("%.2f", value);
        }
    }
    
    /**
     * Create a high-severity performance alert
     */
    public static PerformanceAlert critical(String title, String metricName, double currentValue, 
                                          double threshold, String unit) {
        return PerformanceAlert.builder()
            .title(title)
            .metricName(metricName)
            .currentValue(currentValue)
            .thresholdValue(threshold)
            .unit(unit)
            .severity(PerformanceAlertSeverity.CRITICAL)
            .status(PerformanceAlertStatus.ACTIVE)
            .timestamp(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .impactLevel(ImpactLevel.HIGH)
            .evaluationWindowMinutes(5)
            .consecutiveBreaches(1)
            .autoResolved(false)
            .isFlapping(false)
            .isAnomalous(true)
            .build();
    }
}

enum PerformanceAlertType {
    RESPONSE_TIME("Response Time"),
    ERROR_RATE("Error Rate"),
    THROUGHPUT("Throughput"),
    CPU_USAGE("CPU Usage"),
    MEMORY_USAGE("Memory Usage"),
    DATABASE_QUERY_TIME("Database Query Time"),
    QUEUE_DEPTH("Queue Depth"),
    CONNECTION_POOL("Connection Pool"),
    CACHE_HIT_RATE("Cache Hit Rate"),
    DISK_USAGE("Disk Usage"),
    NETWORK_LATENCY("Network Latency"),
    AVAILABILITY("Availability"),
    CONCURRENCY("Concurrency"),
    RESOURCE_UTILIZATION("Resource Utilization");
    
    private final String displayName;
    
    PerformanceAlertType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}

enum PerformanceAlertSeverity {
    CRITICAL(4, "Critical"),
    HIGH(3, "High"),
    MEDIUM(2, "Medium"),
    LOW(1, "Low");
    
    private final int priority;
    private final String displayName;
    
    PerformanceAlertSeverity(int priority, String displayName) {
        this.priority = priority;
        this.displayName = displayName;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}

enum PerformanceAlertStatus {
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED,
    SUPPRESSED,
    AUTO_RESOLVED
}

enum AlertCondition {
    ABOVE_THRESHOLD,
    BELOW_THRESHOLD,
    EQUALS,
    NOT_EQUALS,
    RATE_OF_CHANGE,
    ANOMALY_DETECTION
}

enum ImpactLevel {
    CRITICAL,
    HIGH, 
    MEDIUM,
    LOW,
    NONE
}

