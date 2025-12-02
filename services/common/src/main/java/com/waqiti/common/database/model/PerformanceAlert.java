package com.waqiti.common.database.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Database performance alert
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceAlert {
    
    /**
     * Alert ID
     */
    private String alertId;
    
    /**
     * Alert type
     */
    private AlertType alertType;
    
    /**
     * Severity level
     */
    private SeverityLevel severity;
    
    /**
     * Alert title
     */
    private String title;
    
    /**
     * Alert description
     */
    private String description;
    
    /**
     * Affected resource
     */
    private String affectedResource;
    
    /**
     * Metric that triggered alert
     */
    private TriggerMetric triggerMetric;
    
    /**
     * Timestamp when alert was triggered
     */
    private Instant triggeredAt;
    
    /**
     * Alert status
     */
    private AlertStatus status;
    
    /**
     * Recommended actions
     */
    private String[] recommendedActions;
    
    /**
     * Additional context
     */
    private Map<String, Object> context;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerMetric {
        private String metricName;
        private double currentValue;
        private double threshold;
        private String comparisonOperator;
        private int consecutiveBreaches;
    }
    
    public enum AlertType {
        HIGH_CPU_USAGE,
        HIGH_MEMORY_USAGE,
        LOW_DISK_SPACE,
        SLOW_QUERIES,
        DEADLOCK_DETECTED,
        REPLICATION_LAG,
        CONNECTION_POOL_EXHAUSTED,
        CACHE_HIT_RATIO_LOW,
        INDEX_FRAGMENTATION,
        LONG_RUNNING_TRANSACTION,
        LOCK_ESCALATION,
        IO_BOTTLENECK,
        NETWORK_LATENCY,
        BACKUP_FAILURE,
        SECURITY_VIOLATION
    }
    
    public enum SeverityLevel {
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }
    
    public enum AlertStatus {
        ACTIVE,
        ACKNOWLEDGED,
        RESOLVED,
        SUPPRESSED
    }
}