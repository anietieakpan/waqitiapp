package com.waqiti.monitoring.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Monitoring domain event model for system performance, alert management, log analytics,
 * health checks, metrics collection, incident management, capacity planning, and compliance monitoring
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String monitoringId;
    private String serviceId;
    private String userId;
    private String metricName;
    private String metricType;
    private BigDecimal metricValue;
    private BigDecimal threshold;
    private String alertId;
    private String alertType;
    private String alertSeverity;
    private String alertStatus;
    private String logLevel;
    private String logMessage;
    private String logSource;
    private String healthCheckType;
    private String healthStatus;
    private String incidentId;
    private String incidentType;
    private String incidentStatus;
    private String incidentSeverity;
    private String capacityMetric;
    private BigDecimal capacityUtilization;
    private BigDecimal capacityThreshold;
    private String resourceType;
    private String complianceRule;
    private String complianceStatus;
    private String violationType;
    private String nodeId;
    private String clusterId;
    private String region;
    private String environment;
    private BigDecimal responseTime;
    private BigDecimal throughput;
    private BigDecimal errorRate;
    private String systemComponent;
    private String performanceMetric;
    private Instant measurementTime;
    private Instant timestamp;
    private String correlationId;
    private String causationId;
    private String version;
    private String status;
    private String description;
    private Long sequenceNumber;
    private Integer retryCount;
    private Map<String, Object> metadata;
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
    
    /**
     * Check if this is a system performance event
     */
    public boolean isSystemPerformanceEvent() {
        return "SYSTEM_PERFORMANCE".equals(eventType);
    }
    
    /**
     * Check if this is a critical alert event
     */
    public boolean isCriticalEvent() {
        return "CRITICAL".equalsIgnoreCase(alertSeverity) || "CRITICAL".equalsIgnoreCase(incidentSeverity);
    }
    
    /**
     * Check if this is a high-priority event
     */
    public boolean isHighPriorityEvent() {
        return "INCIDENT_MANAGEMENT".equals(eventType) || 
               "ALERT_MANAGEMENT".equals(eventType) ||
               isCriticalEvent();
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        if (timestamp == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
}