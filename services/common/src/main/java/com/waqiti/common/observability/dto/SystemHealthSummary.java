package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.waqiti.common.enums.HealthCheckStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive system health summary providing overall system status
 * and detailed health information for all critical components
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealthSummary {
    
    private LocalDateTime timestamp;
    private OverallHealthStatus overallStatus;
    private String overall; // String representation of overall status
    private double healthScore;
    private String statusMessage;
    
    // Core infrastructure health
    private String database;
    private String redis;
    private String messageQueue;
    private String externalServices;
    
    // Detailed component health
    private Map<String, ComponentHealth> componentHealth;
    private List<String> healthyComponents;
    private List<String> degradedComponents;
    private List<String> failedComponents;
    
    // System metrics
    private SystemMetrics systemMetrics;
    private InfrastructureHealth infrastructure;
    private NetworkHealth networkHealth;
    private SecurityHealth securityHealth;
    
    // Dependencies and integrations
    private Map<String, DependencyHealth> dependencies;
    private List<String> criticalDependenciesDown;
    private int totalDependencies;
    private int healthyDependencies;
    
    // Health checks and monitoring
    private List<HealthCheck> failedHealthChecks;
    private List<HealthCheck> warningHealthChecks;
    private LocalDateTime lastFullHealthCheck;
    private int healthCheckIntervalSeconds;
    
    // Alerts and issues
    private List<SystemIssue> activeIssues;
    private List<SystemIssue> recentlyResolvedIssues;
    private int criticalIssueCount;
    private int warningCount;
    
    // Performance indicators
    private double systemAvailability;
    private double meanTimeToDetection;
    private double meanTimeToResolution;
    private LocalDateTime lastIncident;
    
    /**
     * Calculate overall health score based on all components
     */
    public double calculateHealthScore() {
        if (healthScore > 0) {
            return healthScore;
        }
        
        try {
            double score = 100.0;
            
            // Core infrastructure weight: 40%
            score -= calculateInfrastructureDeduction() * 0.4;
            
            // Component health weight: 30%
            score -= calculateComponentHealthDeduction() * 0.3;
            
            // Dependencies weight: 20%
            score -= calculateDependencyDeduction() * 0.2;
            
            // Active issues weight: 10%
            score -= calculateIssueDeduction() * 0.1;
            
            this.healthScore = Math.max(0.0, Math.min(100.0, score));
            return healthScore;
            
        } catch (Exception e) {
            this.healthScore = 0.0;
            return 0.0;
        }
    }
    
    /**
     * Determine overall health status based on health score and critical issues
     */
    public OverallHealthStatus determineOverallStatus() {
        if (overallStatus != null) {
            return overallStatus;
        }
        
        double score = calculateHealthScore();
        
        // Critical overrides - if core infrastructure is down, status is CRITICAL
        if (!"UP".equals(database) || criticalIssueCount > 0) {
            this.overallStatus = OverallHealthStatus.CRITICAL;
            this.statusMessage = "Critical infrastructure components are down or critical issues detected";
            return overallStatus;
        }
        
        // Score-based determination
        if (score >= 95.0) {
            this.overallStatus = OverallHealthStatus.HEALTHY;
            this.statusMessage = "All systems operating normally";
        } else if (score >= 85.0) {
            this.overallStatus = OverallHealthStatus.DEGRADED;
            this.statusMessage = "Some components experiencing minor issues";
        } else if (score >= 70.0) {
            this.overallStatus = OverallHealthStatus.UNHEALTHY;
            this.statusMessage = "Multiple components experiencing issues - attention required";
        } else {
            this.overallStatus = OverallHealthStatus.CRITICAL;
            this.statusMessage = "System experiencing significant issues - immediate attention required";
        }
        
        return overallStatus;
    }
    
    /**
     * Get list of all failing components
     */
    public List<String> getFailingComponents() {
        List<String> failing = List.of();
        
        // Add core infrastructure components
        if (!"UP".equals(database)) failing.add("Database");
        if (!"UP".equals(redis)) failing.add("Redis");
        if (!"UP".equals(messageQueue)) failing.add("Message Queue");
        if (!"UP".equals(externalServices)) failing.add("External Services");
        
        // Add failed components
        if (failedComponents != null) {
            failing.addAll(failedComponents);
        }
        
        return failing;
    }
    
    /**
     * Check if system is operational for user requests
     */
    public boolean isOperational() {
        return determineOverallStatus() != OverallHealthStatus.CRITICAL && 
               "UP".equals(database) && 
               criticalIssueCount == 0;
    }
    
    /**
     * Get health summary for display
     */
    public String getHealthSummary() {
        StringBuilder summary = new StringBuilder();
        
        OverallHealthStatus status = determineOverallStatus();
        double score = calculateHealthScore();
        
        summary.append(String.format("System Health: %s (Score: %.1f/100)\n", 
            status.getDisplayName(), score));
        summary.append(String.format("Status: %s\n", statusMessage));
        
        if (totalDependencies > 0) {
            summary.append(String.format("Dependencies: %d/%d healthy\n", 
                healthyDependencies, totalDependencies));
        }
        
        if (systemAvailability > 0) {
            summary.append(String.format("Availability: %.2f%%\n", systemAvailability));
        }
        
        if (!activeIssues.isEmpty()) {
            summary.append(String.format("Active Issues: %d (%d critical)\n", 
                activeIssues.size(), criticalIssueCount));
        }
        
        return summary.toString().trim();
    }
    
    /**
     * Get recommendations based on current health status
     */
    public List<String> getHealthRecommendations() {
        List<String> recommendations = List.of();
        
        if (overallStatus == OverallHealthStatus.CRITICAL) {
            recommendations.add("URGENT: Investigate and resolve critical infrastructure issues");
            recommendations.add("Activate incident response procedures");
            recommendations.add("Consider enabling maintenance mode if user impact is severe");
        } else if (overallStatus == OverallHealthStatus.UNHEALTHY) {
            recommendations.add("Scale up resources for degraded components");
            recommendations.add("Review system logs for recurring errors");
            recommendations.add("Consider temporary workarounds for non-critical components");
        } else if (overallStatus == OverallHealthStatus.DEGRADED) {
            recommendations.add("Monitor trending metrics for early warning signs");
            recommendations.add("Review capacity planning for stressed components");
            recommendations.add("Schedule maintenance for degraded components");
        }
        
        // Dependency-specific recommendations
        if (!criticalDependenciesDown.isEmpty()) {
            recommendations.add("Implement circuit breakers for failing dependencies");
            recommendations.add("Consider fallback mechanisms for critical dependencies");
        }
        
        return recommendations;
    }
    
    private double calculateInfrastructureDeduction() {
        double deduction = 0.0;
        
        if (!"UP".equals(database)) deduction += 50.0; // Database is critical
        if (!"UP".equals(redis)) deduction += 20.0;
        if (!"UP".equals(messageQueue)) deduction += 20.0;
        if (!"UP".equals(externalServices)) deduction += 10.0;
        
        return Math.min(100.0, deduction);
    }
    
    private double calculateComponentHealthDeduction() {
        if (componentHealth == null || componentHealth.isEmpty()) return 0.0;
        
        double deduction = 0.0;
        int totalComponents = componentHealth.size();
        int unhealthyComponents = 0;
        
        for (ComponentHealth health : componentHealth.values()) {
            if (health.getStatus() == ComponentStatus.CRITICAL) {
                deduction += 20.0;
                unhealthyComponents++;
            } else if (health.getStatus() == ComponentStatus.DEGRADED) {
                deduction += 10.0;
                unhealthyComponents++;
            } else if (health.getStatus() == ComponentStatus.WARNING) {
                deduction += 5.0;
                unhealthyComponents++;
            }
        }
        
        // Additional deduction if large percentage of components are unhealthy
        double unhealthyPercentage = (double) unhealthyComponents / totalComponents;
        if (unhealthyPercentage > 0.5) {
            deduction += 20.0; // Additional penalty for widespread issues
        }
        
        return Math.min(100.0, deduction);
    }
    
    private double calculateDependencyDeduction() {
        if (totalDependencies == 0) return 0.0;
        
        int unhealthyDependencies = totalDependencies - healthyDependencies;
        double unhealthyPercentage = (double) unhealthyDependencies / totalDependencies;
        
        double deduction = unhealthyPercentage * 50.0; // Up to 50 point deduction
        
        // Critical dependencies down get extra penalty
        deduction += criticalDependenciesDown.size() * 15.0;
        
        return Math.min(100.0, deduction);
    }
    
    private double calculateIssueDeduction() {
        double deduction = criticalIssueCount * 25.0; // 25 points per critical issue
        deduction += warningCount * 5.0; // 5 points per warning
        
        return Math.min(100.0, deduction);
    }
    
    /**
     * Create a healthy system health summary
     */
    public static SystemHealthSummary healthy() {
        return SystemHealthSummary.builder()
            .timestamp(LocalDateTime.now())
            .database("UP")
            .redis("UP") 
            .messageQueue("UP")
            .externalServices("UP")
            .overallStatus(OverallHealthStatus.HEALTHY)
            .healthScore(100.0)
            .statusMessage("All systems operating normally")
            .systemAvailability(99.9)
            .criticalIssueCount(0)
            .warningCount(0)
            .totalDependencies(10)
            .healthyDependencies(10)
            .healthyComponents(List.of("API Gateway", "Authentication Service", "Payment Service"))
            .degradedComponents(List.of())
            .failedComponents(List.of())
            .criticalDependenciesDown(List.of())
            .activeIssues(List.of())
            .build();
    }
}

enum OverallHealthStatus {
    HEALTHY("Healthy", "#28a745"),
    DEGRADED("Degraded", "#ffc107"),
    UNHEALTHY("Unhealthy", "#fd7e14"),
    CRITICAL("Critical", "#dc3545");
    
    private final String displayName;
    private final String colorCode;
    
    OverallHealthStatus(String displayName, String colorCode) {
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
class ComponentHealth {
    private String name;
    private ComponentStatus status;
    private String message;
    private LocalDateTime lastChecked;
    private double responseTime;
    private String version;
    private Map<String, Object> metrics;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class DependencyHealth {
    private String name;
    private String type;
    private DependencyStatus status;
    private double responseTime;
    private LocalDateTime lastChecked;
    private String endpoint;
    private boolean isCritical;
    private String failureReason;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SystemMetrics {
    private double cpuUsage;
    private double memoryUsage;
    private double diskUsage;
    private double networkLatency;
    private long activeConnections;
    private double loadAverage;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class NetworkHealth {
    private double latency;
    private double packetLoss;
    private double bandwidth;
    private List<String> routingIssues;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SecurityHealth {
    private int activeThreats;
    private int blockedAttacks;
    private double authenticationSuccess;
    private LocalDateTime lastSecurityScan;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class HealthCheck {
    private String name;
    private String component;
    private HealthCheckStatus status;
    private String message;
    private LocalDateTime timestamp;
    private long executionTime;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SystemIssue {
    private String id;
    private String title;
    private IssuesSeverity severity;
    private String component;
    private LocalDateTime detected;
    private String description;
    private List<String> affectedFeatures;
}

enum ComponentStatus {
    HEALTHY, WARNING, DEGRADED, CRITICAL, UNKNOWN
}

enum DependencyStatus {
    UP, DOWN, DEGRADED, TIMEOUT, UNKNOWN
}


enum IssuesSeverity {
    CRITICAL, HIGH, MEDIUM, LOW, INFO
}