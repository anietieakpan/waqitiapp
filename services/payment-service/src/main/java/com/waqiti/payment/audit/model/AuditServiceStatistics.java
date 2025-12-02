package com.waqiti.payment.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Enterprise Audit Service Statistics
 * 
 * Real-time statistics and health metrics for the audit service including:
 * - Performance metrics and throughput
 * - Storage utilization and capacity
 * - Alert and violation statistics
 * - System health indicators
 * 
 * @version 2.0.0
 * @since 2025-01-18
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuditServiceStatistics {
    
    // Service identification
    private String serviceId;
    private String serviceVersion;
    private LocalDateTime startupTime;
    private long uptimeSeconds;
    private LocalDateTime statisticsTimestamp;
    
    // Performance metrics
    private long totalEventsProcessed;
    private long eventsProcessedLast24Hours;
    private double averageProcessingTimeMs;
    private double p95ProcessingTimeMs;
    private double p99ProcessingTimeMs;
    private long currentThroughputPerSecond;
    private long peakThroughputPerSecond;
    
    // Storage metrics
    private long totalAuditRecords;
    private long totalStorageBytesUsed;
    private long storageCapacityBytes;
    private double storageUtilizationPercent;
    private long oldestRecordAgeDays;
    private long recordsPendingArchival;
    private long recordsArchived;
    
    // Event type distribution
    private Map<String, Long> eventCountsByType;
    private Map<String, Long> eventCountsBySeverity;
    private Map<String, Long> eventCountsByCategory;
    
    // Security metrics
    private long totalSecurityViolations;
    private long securityViolationsLast24Hours;
    private long criticalAlertsLast24Hours;
    private long suspiciousPatternDetections;
    private long fraudAttemptsDetected;
    private double fraudDetectionAccuracy;
    
    // Compliance metrics
    private long complianceViolations;
    private long sarFilingsGenerated;
    private long regulatoryReportsGenerated;
    private double complianceScore;
    private Map<String, Double> complianceScoresByStandard;
    
    // System health
    private ServiceHealth overallHealth;
    private Map<String, ComponentHealth> componentHealth;
    private long failedWriteAttempts;
    private long failedReadAttempts;
    private double errorRate;
    private long queuedEvents;
    private long droppedEvents;
    
    // Integration metrics
    private Map<String, IntegrationStatus> integrationStatuses;
    private long externalApiCalls;
    private double externalApiSuccessRate;
    private long kafkaMessagesPublished;
    private long kafkaMessagesFailed;
    
    // Resource utilization
    private double cpuUsagePercent;
    private double memoryUsagePercent;
    private long heapMemoryUsedBytes;
    private long diskIOOperationsPerSecond;
    private long networkBandwidthBytesPerSecond;
    
    // Alerting metrics
    private long alertsGenerated;
    private long alertsAcknowledged;
    private long alertsEscalated;
    private double averageAlertResponseTimeMinutes;
    private Map<String, Long> alertsByChannel;
    
    // Enums
    public enum ServiceHealth {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        CRITICAL,
        UNKNOWN
    }
    
    // Supporting classes
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentHealth {
        private String componentName;
        private ServiceHealth health;
        private String statusMessage;
        private LocalDateTime lastCheckTime;
        private double availabilityPercent;
        private long consecutiveFailures;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntegrationStatus {
        private String integrationName;
        private boolean connected;
        private LocalDateTime lastSuccessfulConnection;
        private long totalRequests;
        private long failedRequests;
        private double averageResponseTimeMs;
        private String lastError;
    }
    
    // Helper methods
    public double getStorageUtilization() {
        if (storageCapacityBytes == 0) return 0;
        return (double) totalStorageBytesUsed / storageCapacityBytes * 100;
    }
    
    public double getSuccessRate() {
        if (totalEventsProcessed == 0) return 100;
        return (1 - errorRate) * 100;
    }
    
    public boolean isHealthy() {
        return overallHealth == ServiceHealth.HEALTHY;
    }
    
    public boolean requiresAttention() {
        return overallHealth == ServiceHealth.DEGRADED || 
               overallHealth == ServiceHealth.UNHEALTHY ||
               storageUtilizationPercent > 80 ||
               errorRate > 0.05 ||
               queuedEvents > 10000;
    }
    
    public boolean isCritical() {
        return overallHealth == ServiceHealth.CRITICAL ||
               storageUtilizationPercent > 95 ||
               errorRate > 0.10 ||
               droppedEvents > 0;
    }
}