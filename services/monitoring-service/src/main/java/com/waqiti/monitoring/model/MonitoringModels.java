package com.waqiti.monitoring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data models for the Comprehensive Monitoring Service
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionMetrics {
    private String id;
    private String type;
    private String status;
    private BigDecimal amount;
    private String currency;
    private long processingTime;
    private boolean successful;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ApiMetrics {
    private String endpoint;
    private String method;
    private int statusCode;
    private long responseTime;
    private String userAgent;
    private String clientIp;
    private LocalDateTime timestamp;
    private Map<String, String> headers;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SystemMetrics {
    private double cpuUsage;
    private double cpuLoad;
    private long memoryUsed;
    private long memoryFree;
    private double memoryUsagePercent;
    private long diskUsed;
    private long diskFree;
    private double diskUsagePercent;
    private int activeThreads;
    private double networkIn;
    private double networkOut;
    private LocalDateTime timestamp;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class DatabaseMetrics {
    private int activeConnections;
    private int idleConnections;
    private int maxConnections;
    private int pendingConnections;
    private double averageQueryTime;
    private int slowQueryCount;
    private int activeTransactions;
    private int lockCount;
    private int deadlockCount;
    private double cacheHitRatio;
    private LocalDateTime timestamp;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ProviderHealth {
    private String provider;
    private boolean healthy;
    private long responseTime;
    private int errorCount;
    private double successRate;
    private String lastError;
    private LocalDateTime lastCheck;
    private Map<String, Object> metadata;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SlaMetrics {
    private String component;
    private double availability;
    private double averageResponseTime;
    private double errorRate;
    private long totalRequests;
    private long failedRequests;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private boolean inCompliance;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Alert {
    private String id;
    private AlertLevel level;
    private String type;
    private String message;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
    private AlertStatus status;
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolution;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SlaBreach {
    private String id;
    private String type;
    private String component;
    private String expectedValue;
    private String actualValue;
    private LocalDateTime breachTime;
    private String impact;
    private String resolution;
    private boolean acknowledged;
}

public enum AlertLevel {
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

public enum AlertChannel {
    EMAIL,
    SMS,
    SLACK,
    PAGERDUTY,
    WEBHOOK
}

public enum MetricType {
    COUNTER,
    GAUGE,
    TIMER,
    DISTRIBUTION,
    HISTOGRAM
}