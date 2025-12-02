package com.waqiti.common.observability.dto;

import com.waqiti.common.enums.HealthCheckStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive service health information including status, metrics, and diagnostics
 * Provides detailed health assessment for individual microservices
 */
@Data
@Builder
public class ServiceHealth {
    
    private String serviceName;
    private String version;
    private String instanceId;
    private String environment;
    private LocalDateTime timestamp;
    private LocalDateTime startTime;
    private long uptimeSeconds;
    
    // Overall service status
    private ServiceStatus status;
    private double healthScore;
    private String statusMessage;
    private ServiceHealthLevel healthLevel;
    
    // Core health indicators
    private ApplicationHealth application;
    private InfrastructureHealth infrastructure;
    private DependencyHealth dependencies;
    private PerformanceHealth performance;
    
    // Service metrics
    private ServiceMetrics metrics;
    private List<HealthCheckResult> healthChecks;
    private Map<String, String> customMetrics;
    
    // Resource utilization
    private ResourceUtilization resources;
    private CapacityInfo capacity;
    private TrafficInfo traffic;
    
    // Issues and alerts
    private List<ServiceIssue> activeIssues;
    private List<ServiceAlert> activeAlerts;
    private int criticalIssueCount;
    private int warningCount;
    
    // Dependencies and integrations
    private Map<String, DependencyStatus> dependencyStatus;
    private List<String> failedDependencies;
    private List<String> degradedDependencies;
    private CircuitBreakerStatus circuitBreakers;
    
    // Configuration and deployment
    private DeploymentInfo deployment;
    private ConfigurationHealth configuration;
    private FeatureFlags featureFlags;
    
    // Diagnostics and debugging
    private DiagnosticInfo diagnostics;
    private List<String> recentErrors;
    private LoggingStatus loggingStatus;
    private TracingStatus tracingStatus;
    
    /**
     * Calculate overall health score based on all health indicators (immutable calculation)
     */
    public double calculateHealthScore() {
        if (healthScore > 0) {
            return healthScore;
        }
        
        try {
            double score = 100.0;
            
            // Application health (30%)
            if (application != null) {
                score -= calculateApplicationHealthDeduction() * 0.30;
            }
            
            // Performance health (25%)
            if (performance != null) {
                score -= calculatePerformanceHealthDeduction() * 0.25;
            }
            
            // Dependency health (20%)
            if (dependencies != null) {
                score -= calculateDependencyHealthDeduction() * 0.20;
            }
            
            // Infrastructure health (15%)
            if (infrastructure != null) {
                score -= calculateInfrastructureHealthDeduction() * 0.15;
            }
            
            // Issues and alerts (10%)
            score -= calculateIssueDeduction() * 0.10;
            
            // Return calculated score without modifying object state
            return Math.max(0.0, Math.min(100.0, score));
            
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * Determine service health level based on health score and critical issues (immutable calculation)
     */
    public ServiceHealthLevel determineHealthLevel() {
        if (healthLevel != null) {
            return healthLevel;
        }
        
        double score = calculateHealthScore();
        
        // Critical overrides
        if (criticalIssueCount > 0 || status == ServiceStatus.DOWN) {
            return ServiceHealthLevel.CRITICAL;
        }
        
        // Score-based determination without modifying object state
        if (score >= 90.0) {
            return ServiceHealthLevel.EXCELLENT;
        } else if (score >= 75.0) {
            return ServiceHealthLevel.GOOD;
        } else if (score >= 60.0) {
            return ServiceHealthLevel.FAIR;
        } else if (score >= 40.0) {
            return ServiceHealthLevel.POOR;
        } else {
            return ServiceHealthLevel.CRITICAL;
        }
    }
    
    /**
     * Check if service is operational and can handle requests
     */
    public boolean isOperational() {
        return status == ServiceStatus.UP || status == ServiceStatus.DEGRADED;
    }
    
    /**
     * Check if service requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return determineHealthLevel() == ServiceHealthLevel.CRITICAL ||
               criticalIssueCount > 0 ||
               status == ServiceStatus.DOWN ||
               (performance != null && performance.getErrorRate() > 10.0) ||
               !failedDependencies.isEmpty();
    }
    
    /**
     * Get service uptime in a human-readable format
     */
    public String getFormattedUptime() {
        if (uptimeSeconds < 60) {
            return String.format("%d seconds", uptimeSeconds);
        } else if (uptimeSeconds < 3600) {
            return String.format("%d minutes", uptimeSeconds / 60);
        } else if (uptimeSeconds < 86400) {
            return String.format("%.1f hours", uptimeSeconds / 3600.0);
        } else {
            return String.format("%.1f days", uptimeSeconds / 86400.0);
        }
    }
    
    /**
     * Get comprehensive health summary for display
     */
    public String getHealthSummary() {
        StringBuilder summary = new StringBuilder();
        
        ServiceHealthLevel level = determineHealthLevel();
        double score = calculateHealthScore();
        
        summary.append(String.format("Service: %s v%s\n", serviceName, version));
        summary.append(String.format("Status: %s (%s)\n", status.getDisplayName(), level.getDisplayName()));
        summary.append(String.format("Health Score: %.1f/100\n", score));
        summary.append(String.format("Uptime: %s\n", getFormattedUptime()));
        
        if (performance != null) {
            summary.append(String.format("Response Time: %.0fms | Error Rate: %.2f%%\n", 
                performance.getAverageResponseTime(), performance.getErrorRate()));
        }
        
        if (!failedDependencies.isEmpty()) {
            summary.append(String.format("Failed Dependencies: %s\n", 
                String.join(", ", failedDependencies)));
        }
        
        if (activeIssues != null && !activeIssues.isEmpty()) {
            summary.append(String.format("Active Issues: %d (%d critical)\n", 
                activeIssues.size(), criticalIssueCount));
        }
        
        return summary.toString().trim();
    }
    
    /**
     * Get health recommendations based on current state
     */
    public List<String> getHealthRecommendations() {
        List<String> recommendations = new java.util.ArrayList<>();
        
        ServiceHealthLevel level = determineHealthLevel();
        
        if (level == ServiceHealthLevel.CRITICAL) {
            recommendations.add("URGENT: Service requires immediate investigation");
            recommendations.add("Check application logs and error rates");
            recommendations.add("Verify all critical dependencies are operational");
            recommendations.add("Consider scaling up resources or implementing circuit breakers");
        } else if (level == ServiceHealthLevel.POOR) {
            recommendations.add("Service health is degraded - investigation recommended");
            recommendations.add("Review performance metrics and resource utilization");
            recommendations.add("Check for recent deployments or configuration changes");
        } else if (level == ServiceHealthLevel.FAIR) {
            recommendations.add("Monitor service closely for potential issues");
            recommendations.add("Consider optimizing performance bottlenecks");
            recommendations.add("Review capacity planning for anticipated load");
        }
        
        // Dependency-specific recommendations
        if (!failedDependencies.isEmpty()) {
            recommendations.add("Implement fallback mechanisms for failed dependencies");
            recommendations.add("Review circuit breaker configurations");
        }
        
        // Performance-specific recommendations
        if (performance != null && performance.getErrorRate() > 5.0) {
            recommendations.add("Investigate high error rates and implement error handling improvements");
        }
        
        if (resources != null && resources.getCpuUsage() > 80.0) {
            recommendations.add("High CPU usage detected - consider horizontal scaling");
        }
        
        return recommendations;
    }
    
    /**
     * Get top critical issues requiring attention
     */
    public List<ServiceIssue> getCriticalIssues() {
        if (activeIssues == null) {
            return List.of();
        }
        
        return activeIssues.stream()
            .filter(issue -> issue.getSeverity() == IssueSeverity.CRITICAL)
            .limit(5)
            .toList();
    }
    
    private double calculateApplicationHealthDeduction() {
        if (application == null) return 10.0; // Penalty for missing application health
        
        double deduction = 0.0;
        
        if (application.getStatus() == ApplicationStatus.DOWN) deduction += 100.0;
        else if (application.getStatus() == ApplicationStatus.DEGRADED) deduction += 30.0;
        else if (application.getStatus() == ApplicationStatus.STARTING) deduction += 20.0;
        
        return Math.min(100.0, deduction);
    }
    
    private double calculatePerformanceHealthDeduction() {
        double deduction = 0.0;
        
        if (performance.getErrorRate() > 10.0) deduction += 40.0;
        else if (performance.getErrorRate() > 5.0) deduction += 20.0;
        else if (performance.getErrorRate() > 2.0) deduction += 10.0;
        
        if (performance.getAverageResponseTime() > 5000) deduction += 30.0;
        else if (performance.getAverageResponseTime() > 2000) deduction += 15.0;
        else if (performance.getAverageResponseTime() > 1000) deduction += 8.0;
        
        return Math.min(100.0, deduction);
    }
    
    private double calculateDependencyHealthDeduction() {
        double deduction = 0.0;
        
        deduction += failedDependencies.size() * 20.0; // 20 points per failed dependency
        deduction += degradedDependencies.size() * 10.0; // 10 points per degraded dependency
        
        return Math.min(100.0, deduction);
    }
    
    private double calculateInfrastructureHealthDeduction() {
        if (infrastructure == null) return 5.0;
        
        double deduction = 0.0;
        
        if (infrastructure.getContainerStatus() == ContainerStatus.UNHEALTHY) deduction += 25.0;
        if (infrastructure.getNetworkStatus() == NetworkStatus.DEGRADED) deduction += 15.0;
        if (infrastructure.getStorageStatus() == StorageStatus.FULL) deduction += 20.0;
        
        return Math.min(100.0, deduction);
    }
    
    private double calculateIssueDeduction() {
        double deduction = criticalIssueCount * 30.0; // 30 points per critical issue
        deduction += warningCount * 5.0; // 5 points per warning
        
        return Math.min(100.0, deduction);
    }
    
    /**
     * Create a healthy service health instance
     */
    public static ServiceHealth healthy(String serviceName, String version) {
        return ServiceHealth.builder()
            .serviceName(serviceName)
            .version(version)
            .timestamp(LocalDateTime.now())
            .startTime(LocalDateTime.now().minusHours(24))
            .uptimeSeconds(86400) // 24 hours
            .status(ServiceStatus.UP)
            .healthScore(100.0)
            .statusMessage("Service operating normally")
            .healthLevel(ServiceHealthLevel.EXCELLENT)
            .criticalIssueCount(0)
            .warningCount(0)
            .failedDependencies(List.of())
            .degradedDependencies(List.of())
            .activeIssues(List.of())
            .activeAlerts(List.of())
            .build();
    }
}

enum ServiceStatus {
    UP("Up", "#28a745"),
    DOWN("Down", "#dc3545"),
    DEGRADED("Degraded", "#ffc107"),
    STARTING("Starting", "#17a2b8"),
    STOPPING("Stopping", "#fd7e14"),
    HEALTHY("Healthy", "#28a745"),
    UNKNOWN("Unknown", "#6c757d");
    
    private final String displayName;
    private final String colorCode;
    
    ServiceStatus(String displayName, String colorCode) {
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

enum ServiceHealthLevel {
    EXCELLENT("Excellent", "#28a745"),
    GOOD("Good", "#20c997"),
    FAIR("Fair", "#ffc107"),
    POOR("Poor", "#fd7e14"),
    CRITICAL("Critical", "#dc3545");
    
    private final String displayName;
    private final String colorCode;
    
    ServiceHealthLevel(String displayName, String colorCode) {
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

// Supporting classes for comprehensive service health assessment

@Data
@Builder
class ApplicationHealth {
    private ApplicationStatus status;
    private String version;
    private LocalDateTime lastDeployment;
    private int restartCount;
    private String lastError;
    private LocalDateTime lastErrorTime;
}

@Data
@Builder
class PerformanceHealth {
    private double averageResponseTime;
    private double p95ResponseTime;
    private double errorRate;
    private long requestsPerSecond;
    private double cpuUsage;
    private double memoryUsage;
}

@Data
@Builder
class ServiceMetrics {
    private long totalRequests;
    private long totalErrors;
    private double uptime;
    private Map<String, Long> endpointCounts;
    private Map<String, Double> customMetrics;
}

@Data
@Builder
class ResourceUtilization {
    private double cpuUsage;
    private double memoryUsage;
    private double diskUsage;
    private double networkUsage;
    private int activeConnections;
}

@Data
@Builder
class CapacityInfo {
    private int maxConnections;
    private int currentConnections;
    private double connectionUtilization;
    private long maxMemory;
    private long usedMemory;
}

@Data
@Builder
class TrafficInfo {
    private long requestsPerMinute;
    private long peakRequests;
    private LocalDateTime peakTime;
    private Map<String, Long> trafficByEndpoint;
}

@Data
@Builder
class ServiceIssue {
    private String id;
    private String title;
    private IssueSeverity severity;
    private LocalDateTime detected;
    private String description;
}

@Data
@Builder
class ServiceAlert {
    private String id;
    private String name;
    private AlertSeverity severity;
    private LocalDateTime timestamp;
    private String condition;
}

@Data
@Builder
class CircuitBreakerStatus {
    private Map<String, String> circuitStates; // CLOSED, OPEN, HALF_OPEN
    private Map<String, Integer> failureCounts;
    private Map<String, LocalDateTime> lastFailures;
}

@Data
@Builder
class DeploymentInfo {
    private String version;
    private LocalDateTime deployedAt;
    private String deployedBy;
    private String commitHash;
    private String environment;
    private Map<String, String> deploymentTags;
}

@Data
@Builder
class ConfigurationHealth {
    private boolean isValid;
    private List<String> missingConfigs;
    private List<String> invalidConfigs;
    private LocalDateTime lastConfigUpdate;
}

@Data
@Builder
class FeatureFlags {
    private Map<String, Boolean> flags;
    private LocalDateTime lastUpdate;
    private int totalFlags;
    private int enabledFlags;
}

@Data
@Builder
class DiagnosticInfo {
    private String buildVersion;
    private String javaVersion;
    private String springBootVersion;
    private Map<String, String> systemProperties;
    private List<String> activeProfiles;
}

@Data
@Builder
class HealthCheckResult {
    private String name;
    private HealthCheckStatus status;
    private String message;
    private LocalDateTime timestamp;
    private long responseTime;
}

@Data
@Builder
class LoggingStatus {
    private String level;
    private boolean isHealthy;
    private long errorLogCount;
    private LocalDateTime lastErrorLog;
}

@Data
@Builder
class TracingStatus {
    private boolean enabled;
    private double samplingRate;
    private String traceId;
    private boolean isHealthy;
}

enum ApplicationStatus {
    RUNNING, STARTING, STOPPING, DOWN, DEGRADED, RESTARTING
}

enum IssueSeverity {
    CRITICAL, HIGH, MEDIUM, LOW, INFO
}