package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.waqiti.common.enums.ViolationSeverity;
import com.waqiti.common.enums.TrendDirection;
import com.waqiti.common.enums.SLAPriority;
import com.waqiti.common.observability.dto.DataPoint;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.security.SecureRandom;

/**
 * Comprehensive SLA metric tracking and analysis for individual service level agreements
 * within the Waqiti platform with detailed performance monitoring and compliance assessment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SLAMetric {
    
    private String metricId;
    private String metricName;
    private String description;
    private String serviceName;
    private String componentName;
    
    // Metric definition
    private SLAMetricType metricType;
    private String unit;
    private SLAThreshold threshold;
    private double weight;
    private SLAPriority priority;
    
    // Current performance
    private double currentValue;
    private double targetValue;
    private double target; // Alias for targetValue for builder compatibility
    private double actual; // Alias for currentValue for builder compatibility
    private double previousValue;
    private LocalDateTime lastMeasured;
    private LocalDateTime lastUpdated;
    
    // Compliance tracking
    private boolean isTargetMet;
    private double compliancePercentage;
    private String status; // Current status: "COMPLIANT", "VIOLATED", "AT_RISK", etc.
    private Duration complianceDuration;
    private Duration violationDuration;
    private int violationCount;
    private LocalDateTime lastViolation;
    
    // Performance metrics
    private SLAPerformanceStats performanceStats;
    private List<DataPoint> historicalData;
    private Map<String, Double> percentileValues;
    private SLATrendDirection trend;
    private double trendSlope;
    
    // Risk assessment
    private boolean isAtRisk;
    private double riskScore;
    private List<SLARiskFactor> riskFactors;
    private String riskAssessment;
    private SLAHealthStatus healthStatus;
    
    // Measurement configuration
    private String measurementMethod;
    private Duration measurementInterval;
    private String dataSource;
    private List<String> measurementCriteria;
    private boolean isRealTimeMonitored;
    
    // Business context
    private String businessImpact;
    private double businessValue;
    private List<String> affectedFeatures;
    private List<String> stakeholders;
    private String contractualObligation;
    
    // Alerting and notifications
    private SLAAlertConfiguration alertConfiguration;
    private List<SLAAlert> activeAlerts;
    private LocalDateTime nextAlertCheck;
    private boolean alertsEnabled;
    
    // Improvement tracking
    private List<SLAImprovementAction> improvementActions;
    private double improvementTarget;
    private LocalDateTime improvementDeadline;
    private String improvementOwner;
    
    /**
     * Calculate compliance percentage based on target vs actual performance
     */
    public double calculateCompliancePercentage() {
        if (targetValue == 0.0) {
            return isTargetMet ? 100.0 : 0.0;
        }
        
        try {
            double compliance;
            
            switch (metricType) {
                case AVAILABILITY:
                case SUCCESS_RATE:
                case UPTIME:
                    // Higher is better metrics
                    compliance = Math.min(100.0, (currentValue / targetValue) * 100.0);
                    break;
                    
                case RESPONSE_TIME:
                case ERROR_RATE:
                case LATENCY:
                    // Lower is better metrics
                    if (currentValue <= targetValue) {
                        compliance = 100.0;
                    } else {
                        compliance = Math.max(0.0, 100.0 - ((currentValue - targetValue) / targetValue * 100.0));
                    }
                    break;
                    
                case THROUGHPUT:
                case CAPACITY:
                    // Target-based metrics
                    compliance = Math.min(100.0, (currentValue / targetValue) * 100.0);
                    break;
                    
                default:
                    // Generic calculation
                    compliance = isTargetMet ? 100.0 : 
                        Math.max(0.0, 100.0 - Math.abs(currentValue - targetValue) / targetValue * 100.0);
            }
            
            this.compliancePercentage = Math.max(0.0, Math.min(100.0, compliance));
            return compliancePercentage;
            
        } catch (Exception e) {
            this.compliancePercentage = 0.0;
            return 0.0;
        }
    }
    
    /**
     * Determine if the current value meets the SLA target
     */
    public boolean evaluateTargetCompliance() {
        try {
            boolean targetMet;
            
            switch (metricType) {
                case AVAILABILITY:
                case SUCCESS_RATE:
                case THROUGHPUT:
                case UPTIME:
                    // Higher is better - current should be >= target
                    targetMet = currentValue >= targetValue;
                    break;
                    
                case RESPONSE_TIME:
                case ERROR_RATE:
                case LATENCY:
                    // Lower is better - current should be <= target
                    targetMet = currentValue <= targetValue;
                    break;
                    
                case CAPACITY:
                    // Capacity metrics - current should be close to target (within 10%)
                    double tolerance = targetValue * 0.1;
                    targetMet = Math.abs(currentValue - targetValue) <= tolerance;
                    break;
                    
                default:
                    // Generic comparison
                    targetMet = currentValue >= targetValue;
            }
            
            this.isTargetMet = targetMet;
            return isTargetMet;
            
        } catch (Exception e) {
            this.isTargetMet = false;
            return false;
        }
    }
    
    /**
     * Calculate risk score based on multiple factors
     */
    public double calculateRiskScore() {
        try {
            double risk = 0.0;
            
            // Compliance risk (40% weight)
            double compliance = calculateCompliancePercentage();
            risk += (100.0 - compliance) * 0.4;
            
            // Trend risk (30% weight)
            if (trend == SLATrendDirection.DECLINING) {
                risk += Math.abs(trendSlope) * 0.3;
            }
            
            // Historical violations (20% weight)
            if (violationCount > 0) {
                risk += Math.min(20.0, violationCount * 2.0);
            }
            
            // Business impact (10% weight)
            if (businessValue > 0.8) { // High business value
                risk += (100.0 - compliance) * 0.1;
            }
            
            this.riskScore = Math.max(0.0, Math.min(100.0, risk));
            return riskScore;
            
        } catch (Exception e) {
            this.riskScore = 0.0;
            return 0.0;
        }
    }
    
    /**
     * Determine if metric is at risk of SLA violation
     */
    public boolean assessRiskStatus() {
        double risk = calculateRiskScore();
        double compliance = calculateCompliancePercentage();
        
        // At risk if:
        // - Risk score > 60
        // - Compliance < 95% and declining trend
        // - Recent violations
        // - Critical business impact with poor performance
        
        boolean atRisk = risk > 60.0 ||
                        (compliance < 95.0 && trend == SLATrendDirection.DECLINING) ||
                        (lastViolation != null && 
                         Duration.between(lastViolation, LocalDateTime.now()).toHours() < 24) ||
                        (businessValue > 0.8 && compliance < 90.0);
        
        this.isAtRisk = atRisk;
        return isAtRisk;
    }
    
    /**
     * Determine overall health status of the SLA metric
     */
    public SLAHealthStatus determineHealthStatus() {
        double compliance = calculateCompliancePercentage();
        boolean targetMet = evaluateTargetCompliance();
        boolean atRisk = assessRiskStatus();
        
        if (!targetMet || compliance < 90.0) {
            this.healthStatus = SLAHealthStatus.CRITICAL;
        } else if (atRisk || compliance < 95.0) {
            this.healthStatus = SLAHealthStatus.WARNING;
        } else if (compliance < 99.0) {
            this.healthStatus = SLAHealthStatus.HEALTHY;
        } else {
            this.healthStatus = SLAHealthStatus.EXCELLENT;
        }
        
        return healthStatus;
    }
    
    /**
     * Get performance change compared to previous measurement
     */
    public double getPerformanceChange() {
        if (previousValue == 0.0) {
            return 0.0;
        }
        
        return ((currentValue - previousValue) / previousValue) * 100.0;
    }
    
    /**
     * Get human-readable metric summary
     */
    public String getMetricSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append(String.format("%s: %.2f %s", metricName, currentValue, unit));
        
        if (targetValue > 0) {
            summary.append(String.format(" (Target: %.2f %s)", targetValue, unit));
        }
        
        double compliance = calculateCompliancePercentage();
        summary.append(String.format(" | Compliance: %.1f%%", compliance));
        
        if (!isTargetMet) {
            summary.append(" | ⚠️ VIOLATION");
        }
        
        if (isAtRisk) {
            summary.append(" | 🔶 AT RISK");
        }
        
        return summary.toString();
    }
    
    /**
     * Get detailed metric information for reporting
     */
    public String getDetailedInfo() {
        StringBuilder info = new StringBuilder();
        
        info.append(String.format("SLA Metric: %s\n", metricName));
        info.append(String.format("Service: %s | Component: %s\n", serviceName, componentName));
        info.append(String.format("Type: %s | Priority: %s | Weight: %.2f\n", 
            metricType.getDisplayName(), priority.getDisplayName(), weight));
        
        info.append(String.format("Current Value: %.2f %s\n", currentValue, unit));
        info.append(String.format("Target Value: %.2f %s\n", targetValue, unit));
        info.append(String.format("Compliance: %.1f%% | Target Met: %s\n", 
            calculateCompliancePercentage(), isTargetMet ? "Yes" : "No"));
        
        info.append(String.format("Health Status: %s | Risk Score: %.1f\n", 
            determineHealthStatus().getDisplayName(), calculateRiskScore()));
        
        if (violationCount > 0) {
            info.append(String.format("Violations: %d | Last Violation: %s\n", 
                violationCount, lastViolation));
        }
        
        if (trend != null) {
            info.append(String.format("Trend: %s (Slope: %.2f)\n", 
                trend.getDisplayName(), trendSlope));
        }
        
        info.append(String.format("Last Measured: %s\n", lastMeasured));
        
        return info.toString();
    }
    
    /**
     * Check if metric requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return !isTargetMet ||
               calculateCompliancePercentage() < 90.0 ||
               healthStatus == SLAHealthStatus.CRITICAL ||
               (isAtRisk && businessValue > 0.8);
    }
    
    /**
     * Get recommended actions based on current status
     */
    public List<String> getRecommendedActions() {
        List<String> actions = new java.util.ArrayList<>();
        
        if (!isTargetMet) {
            actions.add("URGENT: Address SLA violation - target not met");
        }
        
        double compliance = calculateCompliancePercentage();
        if (compliance < 90.0) {
            actions.add("CRITICAL: Improve performance to achieve minimum compliance");
        } else if (compliance < 95.0) {
            actions.add("WARNING: Performance below standard - implement improvements");
        }
        
        if (isAtRisk) {
            actions.add("PREVENTIVE: Address risk factors to prevent future violations");
        }
        
        if (trend == SLATrendDirection.DECLINING) {
            actions.add("TREND: Investigate and reverse declining performance trend");
        }
        
        if (violationCount > 3) {
            actions.add("PATTERN: Analyze recurring violations and implement systemic fixes");
        }
        
        if (businessValue > 0.8 && compliance < 99.0) {
            actions.add("BUSINESS: Prioritize improvements for high-value business metric");
        }
        
        return actions;
    }
    
    /**
     * Create a healthy SLA metric
     */
    public static SLAMetric createHealthyMetric(String name, String service, SLAMetricType type) {
        return SLAMetric.builder()
            .metricId(generateMetricId())
            .metricName(name)
            .serviceName(service)
            .metricType(type)
            .unit(getDefaultUnit(type))
            .currentValue(getHealthyValue(type))
            .targetValue(getTargetValue(type))
            .weight(1.0)
            .priority(SLAPriority.HIGH)
            .isTargetMet(true)
            .compliancePercentage(99.5)
            .violationCount(0)
            .isAtRisk(false)
            .riskScore(5.0)
            .trend(SLATrendDirection.STABLE)
            .healthStatus(SLAHealthStatus.EXCELLENT)
            .lastMeasured(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .businessValue(0.8)
            .isRealTimeMonitored(true)
            .alertsEnabled(true)
            .build();
    }
    
    /**
     * Generate cryptographically secure metric ID for SLA tracking
     * 
     * SECURITY FIX: Replaced Math.random() with SecureRandom
     * This ensures unpredictable IDs that cannot be guessed or manipulated
     */
    private static String generateMetricId() {
        SecureRandom secureRandom = new SecureRandom();
        int randomSuffix = secureRandom.nextInt(10000);
        
        // Add process ID for additional uniqueness
        long pid = ProcessHandle.current().pid();
        
        return String.format("SLA-%d-%04d-P%d", System.currentTimeMillis(), randomSuffix, pid);
    }
    
    private static String getDefaultUnit(SLAMetricType type) {
        return switch (type) {
            case AVAILABILITY, SUCCESS_RATE -> "%";
            case RESPONSE_TIME, LATENCY -> "ms";
            case THROUGHPUT -> "req/s";
            case ERROR_RATE -> "%";
            case CAPACITY -> "units";
            case UPTIME -> "hours";
        };
    }
    
    private static double getHealthyValue(SLAMetricType type) {
        return switch (type) {
            case AVAILABILITY -> 99.9;
            case SUCCESS_RATE -> 99.8;
            case RESPONSE_TIME, LATENCY -> 150.0;
            case THROUGHPUT -> 1000.0;
            case ERROR_RATE -> 0.1;
            case CAPACITY -> 80.0;
            case UPTIME -> 720.0; // 30 days
        };
    }
    
    private static double getTargetValue(SLAMetricType type) {
        return switch (type) {
            case AVAILABILITY -> 99.5;
            case SUCCESS_RATE -> 99.0;
            case RESPONSE_TIME, LATENCY -> 200.0;
            case THROUGHPUT -> 800.0;
            case ERROR_RATE -> 0.5;
            case CAPACITY -> 75.0;
            case UPTIME -> 720.0; // 30 days
        };
    }
}

// Supporting enums and classes

enum SLAMetricType {
    AVAILABILITY("Availability"),
    RESPONSE_TIME("Response Time"),
    THROUGHPUT("Throughput"),
    ERROR_RATE("Error Rate"),
    SUCCESS_RATE("Success Rate"),
    LATENCY("Latency"),
    CAPACITY("Capacity"),
    UPTIME("Uptime");
    
    private final String displayName;
    
    SLAMetricType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}


enum SLATrendDirection {
    IMPROVING("Improving", "↗️"),
    STABLE("Stable", "→"),
    DECLINING("Declining", "↘️");
    
    private final String displayName;
    private final String icon;
    
    SLATrendDirection(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getIcon() {
        return icon;
    }
}

enum SLAHealthStatus {
    EXCELLENT("Excellent", "#28a745"),
    HEALTHY("Healthy", "#6f42c1"),
    WARNING("Warning", "#ffc107"),
    CRITICAL("Critical", "#dc3545");
    
    private final String displayName;
    private final String colorCode;
    
    SLAHealthStatus(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getColorCode() {
        return colorCode;
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SLAThreshold {
    private double warningThreshold;
    private double criticalThreshold;
    private String thresholdType; // "min", "max", "range"
    private String description;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SLAPerformanceStats {
    private double averageValue;
    private double minimumValue;
    private double maximumValue;
    private double standardDeviation;
    private double p50Value;
    private double p95Value;
    private double p99Value;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SLARiskFactor {
    private String factor;
    private double riskWeight;
    private String description;
    private String mitigation;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SLAAlertConfiguration {
    private boolean enabledWarningAlerts;
    private boolean enabledCriticalAlerts;
    private Duration alertCooldown;
    private List<String> alertRecipients;
    private String alertTemplate;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SLAAlert {
    private String alertId;
    private ViolationSeverity severity;
    private LocalDateTime triggeredAt;
    private String message;
    private boolean isResolved;
    private LocalDateTime resolvedAt;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SLAImprovementAction {
    private String actionId;
    private String description;
    private String assignee;
    private LocalDateTime dueDate;
    private String status;
    private double expectedImprovement;
}
