package com.waqiti.payment.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * System Health Status
 * 
 * Comprehensive health status of the payment system including
 * all components, providers, and critical metrics.
 * 
 * @author Waqiti Payment Team
 * @version 3.0.0
 * @since 2025-01-16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealthStatus {
    
    /**
     * Overall system health indicator
     */
    private Boolean overallHealthy;
    
    /**
     * Success rate health check
     */
    private Boolean successRateHealthy;
    
    /**
     * Current system success rate
     */
    private Double currentSuccessRate;
    
    /**
     * Response time health check
     */
    private Boolean responseTimeHealthy;
    
    /**
     * Current average response time
     */
    private Double averageResponseTime;
    
    /**
     * Provider-specific health status
     */
    private Map<String, Boolean> providerHealth;
    
    /**
     * Database connectivity health
     */
    private Boolean databaseHealthy;
    
    /**
     * Message queue health
     */
    private Boolean messageQueueHealthy;
    
    /**
     * External service dependencies health
     */
    private Map<String, Boolean> externalServicesHealth;
    
    /**
     * Circuit breaker states
     */
    private Map<String, String> circuitBreakerStates;
    
    /**
     * Active alerts
     */
    private List<SystemAlert> activeAlerts;
    
    /**
     * System load metrics
     */
    private SystemLoadMetrics loadMetrics;
    
    /**
     * Memory usage percentage
     */
    private Double memoryUsagePercent;
    
    /**
     * CPU usage percentage
     */
    private Double cpuUsagePercent;
    
    /**
     * Disk usage percentage
     */
    private Double diskUsagePercent;
    
    /**
     * Number of active connections
     */
    private Long activeConnections;
    
    /**
     * When this health check was performed
     */
    private LocalDateTime timestamp;
    
    /**
     * Health check duration in milliseconds
     */
    private Long healthCheckDurationMs;
    
    /**
     * System uptime in milliseconds
     */
    private Long systemUptimeMs;
    
    /**
     * Version information
     */
    private String systemVersion;
    
    /**
     * Environment (development, staging, production)
     */
    private String environment;
    
    /**
     * Get overall health grade
     */
    public HealthGrade getHealthGrade() {
        if (overallHealthy == null || !overallHealthy) {
            return HealthGrade.CRITICAL;
        }
        
        int healthyComponents = 0;
        int totalComponents = 0;
        
        // Count provider health
        if (providerHealth != null) {
            totalComponents += providerHealth.size();
            healthyComponents += (int) providerHealth.values().stream().mapToLong(healthy -> healthy ? 1 : 0).sum();
        }
        
        // Count system components
        if (databaseHealthy != null) {
            totalComponents++;
            if (databaseHealthy) healthyComponents++;
        }
        
        if (messageQueueHealthy != null) {
            totalComponents++;
            if (messageQueueHealthy) healthyComponents++;
        }
        
        if (successRateHealthy != null) {
            totalComponents++;
            if (successRateHealthy) healthyComponents++;
        }
        
        if (responseTimeHealthy != null) {
            totalComponents++;
            if (responseTimeHealthy) healthyComponents++;
        }
        
        if (totalComponents == 0) {
            return HealthGrade.UNKNOWN;
        }
        
        double healthPercentage = (healthyComponents / (double) totalComponents) * 100;
        
        if (healthPercentage >= 95) {
            return HealthGrade.EXCELLENT;
        } else if (healthPercentage >= 90) {
            return HealthGrade.GOOD;
        } else if (healthPercentage >= 80) {
            return HealthGrade.FAIR;
        } else if (healthPercentage >= 70) {
            return HealthGrade.POOR;
        } else {
            return HealthGrade.CRITICAL;
        }
    }
    
    /**
     * Check if system needs immediate attention
     */
    public boolean needsImmediateAttention() {
        return !overallHealthy ||
               (successRateHealthy != null && !successRateHealthy) ||
               (responseTimeHealthy != null && !responseTimeHealthy) ||
               (databaseHealthy != null && !databaseHealthy) ||
               hasHighSeverityAlerts();
    }
    
    /**
     * Check if there are high severity alerts
     */
    public boolean hasHighSeverityAlerts() {
        return activeAlerts != null && 
               activeAlerts.stream().anyMatch(alert -> 
                   "HIGH".equals(alert.getSeverity()) || "CRITICAL".equals(alert.getSeverity()));
    }
    
    /**
     * Get number of unhealthy providers
     */
    public long getUnhealthyProviderCount() {
        if (providerHealth == null) {
            return 0;
        }
        
        return providerHealth.values().stream().mapToLong(healthy -> healthy ? 0 : 1).sum();
    }
    
    /**
     * Check if resource usage is high
     */
    public boolean isResourceUsageHigh() {
        return (memoryUsagePercent != null && memoryUsagePercent > 80) ||
               (cpuUsagePercent != null && cpuUsagePercent > 80) ||
               (diskUsagePercent != null && diskUsagePercent > 90);
    }
    
    /**
     * System alert information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemAlert {
        private String alertId;
        private String severity;
        private String component;
        private String message;
        private LocalDateTime timestamp;
        private String status;
    }
    
    /**
     * System load metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SystemLoadMetrics {
        private Double requestsPerSecond;
        private Double averageRequestDuration;
        private Long queueSize;
        private Long activeThreads;
        private Double throughput;
    }
    
    public enum HealthGrade {
        EXCELLENT,
        GOOD,
        FAIR,
        POOR,
        CRITICAL,
        UNKNOWN
    }
}