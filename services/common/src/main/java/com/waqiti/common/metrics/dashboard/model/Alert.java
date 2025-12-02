package com.waqiti.common.metrics.dashboard.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Individual alert information with severity levels and context
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    private String id;
    private String severity;
    private String category;
    private String message;
    private LocalDateTime timestamp;
    private Map<String, Object> context;
    private String source;
    private String status;
    private String assignee;
    private LocalDateTime resolvedAt;
    
    /**
     * Alert severity levels
     */
    public enum Severity {
        CRITICAL, HIGH, MEDIUM, LOW, INFO
    }
    
    /**
     * Alert categories
     */
    public enum Category {
        SYSTEM, DATABASE, SECURITY, PERFORMANCE, BUSINESS, NETWORK, EXTERNAL_SERVICE
    }
    
    /**
     * Alert status
     */
    public enum Status {
        OPEN, ACKNOWLEDGED, INVESTIGATING, RESOLVED, CLOSED
    }
    
    /**
     * Check if alert is resolved
     */
    public boolean isResolved() {
        return resolvedAt != null && ("RESOLVED".equals(status) || "CLOSED".equals(status));
    }
    
    /**
     * Get alert age in minutes
     */
    public long getAgeInMinutes() {
        return java.time.Duration.between(timestamp, LocalDateTime.now()).toMinutes();
    }
    
    /**
     * Check if alert is critical and unresolved
     */
    public boolean isCriticalAndUnresolved() {
        return "CRITICAL".equals(severity) && !isResolved();
    }
}