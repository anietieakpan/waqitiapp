package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enterprise-grade alerts summary providing comprehensive alerting overview
 * for the Waqiti platform with advanced alert management capabilities.
 * 
 * This class aggregates all types of alerts including security, performance,
 * business, and operational alerts with prioritization and escalation management.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertsSummary {
    
    /**
     * Summary metadata
     */
    private LocalDateTime generatedAt;
    private String serviceName;
    private String alertsVersion;
    
    /**
     * Alert counts by severity
     */
    private int totalAlerts;
    private int criticalAlerts;
    private int highPriorityAlerts;
    private int mediumPriorityAlerts;
    private int lowPriorityAlerts;
    
    /**
     * Alert counts by category
     */
    private int securityAlerts;
    private int performanceAlerts;
    private int businessAlerts;
    private int infrastructureAlerts;
    private int complianceAlerts;
    
    /**
     * Active alert lists
     */
    private List<CriticalAlert> activeCriticalAlerts;
    private List<SecurityAlert> activeSecurityAlerts;
    private List<PerformanceAlert> activePerformanceAlerts;
    private List<BusinessAlert> activeBusinessAlerts;
    
    /**
     * Alert trends and analytics
     */
    private AlertTrends trends;
    private Map<String, Integer> alertsByService;
    private Map<String, Integer> alertsByHour;
    
    /**
     * Escalation and resolution metrics
     */
    private int escalatedAlerts;
    private int resolvedAlertsLast24h;
    private double averageResolutionTime;
    private int overdueAlerts;
    
    /**
     * Alert health and system impact
     */
    private double alertSystemHealth;
    private List<String> affectedServices;
    private AlertImpactAssessment impactAssessment;
    
    /**
     * Get alerts requiring immediate attention
     */
    public List<CriticalAlert> getImmediateActionAlerts() {
        if (activeCriticalAlerts == null) {
            return List.of();
        }
        
        return activeCriticalAlerts.stream()
            .filter(alert -> alert.requiresImmediateAction())
            .sorted((a1, a2) -> a2.getSeverity().getPriority() - a1.getSeverity().getPriority())
            .limit(5)
            .toList();
    }

    /**
     * Calculate overall alert severity score
     */
    public double calculateAlertSeverityScore() {
        if (totalAlerts == 0) {
            return 100.0; // No alerts = perfect score
        }
        
        // Weighted scoring based on severity
        double score = 100.0;
        score -= (criticalAlerts * 20.0);      // 20 points per critical
        score -= (highPriorityAlerts * 10.0);  // 10 points per high
        score -= (mediumPriorityAlerts * 5.0);  // 5 points per medium
        score -= (lowPriorityAlerts * 1.0);     // 1 point per low
        
        return Math.max(0.0, score);
    }

    /**
     * Check if system is in alert storm condition
     */
    public boolean isAlertStorm() {
        // Alert storm indicators
        return totalAlerts > 100 || 
               criticalAlerts > 10 || 
               (trends != null && trends.getAlertVelocity() > 50); // More than 50 alerts per hour
    }

    /**
     * Get alert summary for executive dashboard
     */
    public String getExecutiveSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append(String.format("Alert Status: %d Total (%d Critical)", totalAlerts, criticalAlerts));
        
        if (criticalAlerts > 0) {
            summary.append(String.format(" | CRITICAL: %d alerts requiring immediate attention", criticalAlerts));
        }
        
        if (escalatedAlerts > 0) {
            summary.append(String.format(" | %d escalated to on-call teams", escalatedAlerts));
        }
        
        if (affectedServices != null && !affectedServices.isEmpty()) {
            summary.append(String.format(" | %d services affected", affectedServices.size()));
        }
        
        return summary.toString();
    }

    /**
     * Get recommended actions based on current alert status
     */
    public List<String> getRecommendedActions() {
        List<String> actions = new java.util.ArrayList<>();
        
        if (isAlertStorm()) {
            actions.add("ALERT STORM DETECTED: Implement alert throttling and noise reduction");
            actions.add("Focus on critical alerts only during storm conditions");
            actions.add("Review alert thresholds to reduce false positives");
        }
        
        if (criticalAlerts > 0) {
            actions.add(String.format("Immediate attention required for %d critical alerts", criticalAlerts));
            actions.add("Activate incident response procedures for critical issues");
        }
        
        if (overdueAlerts > 0) {
            actions.add(String.format("Review and resolve %d overdue alerts", overdueAlerts));
            actions.add("Investigate why alerts are not being addressed within SLA");
        }
        
        if (securityAlerts > 0) {
            actions.add(String.format("Security team review required for %d security alerts", securityAlerts));
        }
        
        if (averageResolutionTime > 60) { // More than 1 hour
            actions.add("Alert resolution time exceeds target - review escalation procedures");
        }
        
        return actions;
    }

    /**
     * Generate alert health report
     */
    public String generateHealthReport() {
        StringBuilder report = new StringBuilder();
        
        report.append("=== ALERT SYSTEM HEALTH REPORT ===\n");
        report.append(String.format("Generated: %s\n", generatedAt));
        report.append(String.format("Service: %s\n\n", serviceName));
        
        // Alert overview
        report.append("ALERT OVERVIEW:\n");
        report.append(String.format("- Total Alerts: %d\n", totalAlerts));
        report.append(String.format("- Critical: %d\n", criticalAlerts));
        report.append(String.format("- High Priority: %d\n", highPriorityAlerts));
        report.append(String.format("- Medium Priority: %d\n", mediumPriorityAlerts));
        report.append(String.format("- Low Priority: %d\n\n", lowPriorityAlerts));
        
        // Category breakdown
        report.append("BY CATEGORY:\n");
        report.append(String.format("- Security: %d\n", securityAlerts));
        report.append(String.format("- Performance: %d\n", performanceAlerts));
        report.append(String.format("- Business: %d\n", businessAlerts));
        report.append(String.format("- Infrastructure: %d\n", infrastructureAlerts));
        report.append(String.format("- Compliance: %d\n\n", complianceAlerts));
        
        // Performance metrics
        report.append("RESOLUTION METRICS:\n");
        report.append(String.format("- Resolved (24h): %d\n", resolvedAlertsLast24h));
        report.append(String.format("- Average Resolution: %.1f minutes\n", averageResolutionTime));
        report.append(String.format("- Escalated: %d\n", escalatedAlerts));
        report.append(String.format("- Overdue: %d\n\n", overdueAlerts));
        
        // Health assessment
        double healthScore = calculateAlertSeverityScore();
        report.append("ALERT SYSTEM HEALTH:\n");
        report.append(String.format("- Health Score: %.1f/100\n", healthScore));
        report.append(String.format("- Status: %s\n", getHealthStatus(healthScore)));
        
        if (isAlertStorm()) {
            report.append("- WARNING: Alert storm condition detected\n");
        }
        
        return report.toString();
    }

    private String getHealthStatus(double score) {
        if (score >= 90) return "Excellent";
        if (score >= 80) return "Good";
        if (score >= 70) return "Fair";
        if (score >= 60) return "Poor";
        return "Critical";
    }
}

/**
 * Supporting classes for comprehensive alert management
 * Note: SecurityAlert, CriticalAlert, PerformanceAlert are defined as standalone classes
 * in com.waqiti.common.observability.dto package and imported at the package level.
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class BusinessAlert {
    private String id;
    private String type;
    private com.waqiti.common.enums.ViolationSeverity severity;
    private String description;
    private LocalDateTime timestamp;
    private String businessImpact;
    private double impactScore;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AlertTrends {
    private double alertVelocity; // Alerts per hour
    private com.waqiti.common.enums.TrendDirection trend;
    private Map<String, Integer> alertCountsByHour;
    private List<String> trendingAlertTypes;
    private double noiseReductionEfficiency;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AlertImpactAssessment {
    private List<String> criticalSystemsAffected;
    private double businessImpactScore;
    private double customerImpactScore;
    private int estimatedUsersAffected;
    private String impactDescription;
}