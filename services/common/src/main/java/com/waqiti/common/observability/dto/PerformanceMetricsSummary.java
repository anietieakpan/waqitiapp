package com.waqiti.common.observability.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.waqiti.common.observability.dto.DataPoint;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive performance metrics summary with enterprise-grade monitoring capabilities.
 * Provides detailed insights into system performance, response times, error rates, and
 * resource utilization across the Waqiti platform.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceMetricsSummary {
    private double averageResponseTime;
    private double p95ResponseTime;
    private double p99ResponseTime;
    private long totalRequests;
    private long totalErrors;
    private double errorRate;
    private double throughput;
    private double cpuUsage;
    private double memoryUsage;
    private double diskUsage;
    private long activeConnections;
    private Map<String, Double> endpointMetrics;
    private List<ErrorMetric> topErrors;
    private Map<String, List<DataPoint>> errorTrends;
    private double systemAvailability;
    private double averageDatabaseQueryTime;
    private double paymentSuccessRate;
    private Instant timestamp;
    private LocalDateTime recordedAt; // Alternative timestamp format for compatibility
    
    public List<ErrorMetric> getTopErrors(int limit) {
        if (topErrors == null || topErrors.isEmpty()) {
            return List.of();
        }
        return topErrors.stream().limit(limit).toList();
    }
    
    public Map<String, List<DataPoint>> getErrorTrends(int hours) {
        // Filter trends by hours
        return errorTrends;
    }
    
    public List<PerformanceAlert> getActivePerformanceAlerts() {
        List<PerformanceAlert> alerts = new java.util.ArrayList<>();
        
        // High response time alert
        if (averageResponseTime > 2000) {
            alerts.add(PerformanceAlert.builder()
                .type(PerformanceAlertType.RESPONSE_TIME)
                .severity(PerformanceAlertSeverity.HIGH)
                .message(String.format("Average response time %.2fms exceeds threshold", averageResponseTime))
                .threshold(2000.0)
                .currentValue(averageResponseTime)
                .timestamp(LocalDateTime.now())
                .build());
        }
        
        // High error rate alert
        if (errorRate > 5.0) {
            alerts.add(PerformanceAlert.builder()
                .type(PerformanceAlertType.ERROR_RATE)
                .severity(PerformanceAlertSeverity.CRITICAL)
                .message(String.format("Error rate %.2f%% exceeds threshold", errorRate))
                .threshold(5.0)
                .currentValue(errorRate)
                .timestamp(LocalDateTime.now())
                .build());
        }
        
        // High CPU usage alert
        if (cpuUsage > 80.0) {
            alerts.add(PerformanceAlert.builder()
                .type(PerformanceAlertType.CPU_USAGE)
                .severity(PerformanceAlertSeverity.HIGH)
                .message(String.format("CPU usage %.2f%% exceeds threshold", cpuUsage))
                .threshold(80.0)
                .currentValue(cpuUsage)
                .timestamp(LocalDateTime.now())
                .build());
        }
        
        return alerts;
    }
    
    /**
     * Calculate overall performance score (0-100)
     */
    public double getPerformanceScore() {
        double score = 100.0;
        
        // Response time impact (30%)
        if (averageResponseTime > 5000) score -= 30;
        else if (averageResponseTime > 2000) score -= 20;
        else if (averageResponseTime > 1000) score -= 10;
        
        // Error rate impact (25%)
        if (errorRate > 10.0) score -= 25;
        else if (errorRate > 5.0) score -= 15;
        else if (errorRate > 2.0) score -= 8;
        
        // System availability impact (20%)
        if (systemAvailability < 99.0) score -= 20;
        else if (systemAvailability < 99.5) score -= 10;
        else if (systemAvailability < 99.9) score -= 5;
        
        // Resource utilization impact (25%)
        if (cpuUsage > 90) score -= 15;
        else if (cpuUsage > 80) score -= 10;
        else if (cpuUsage > 70) score -= 5;
        
        if (memoryUsage > 90) score -= 10;
        else if (memoryUsage > 80) score -= 5;
        
        return Math.max(0.0, Math.min(100.0, score));
    }
    
    /**
     * Get critical performance issues requiring immediate attention
     */
    public List<String> getCriticalPerformanceIssues() {
        List<String> issues = new java.util.ArrayList<>();
        
        if (systemAvailability < 99.0) {
            issues.add(String.format("System availability critical: %.2f%%", systemAvailability));
        }
        
        if (errorRate > 10.0) {
            issues.add(String.format("Error rate critical: %.2f%%", errorRate));
        }
        
        if (averageResponseTime > 5000) {
            issues.add(String.format("Response time critical: %.0fms", averageResponseTime));
        }
        
        if (cpuUsage > 90) {
            issues.add(String.format("CPU usage critical: %.1f%%", cpuUsage));
        }
        
        if (memoryUsage > 90) {
            issues.add(String.format("Memory usage critical: %.1f%%", memoryUsage));
        }
        
        return issues;
    }
    
    /**
     * Check if performance is within acceptable thresholds
     */
    public boolean isPerformanceHealthy() {
        return averageResponseTime < 2000 &&
               errorRate < 5.0 &&
               systemAvailability >= 99.5 &&
               cpuUsage < 80 &&
               memoryUsage < 80;
    }
}

