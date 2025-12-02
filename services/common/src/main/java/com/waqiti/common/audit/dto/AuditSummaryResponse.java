package com.waqiti.common.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for audit log summary and statistics
 * 
 * Provides comprehensive summary of audit events with breakdowns by type,
 * category, severity, compliance relevance, and security metrics.
 * 
 * Used for:
 * - Executive dashboards
 * - Compliance reporting
 * - Security monitoring
 * - Operational analytics
 * - Performance tracking
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditSummaryResponse {
    
    // ========================================
    // OVERALL STATISTICS
    // ========================================
    
    /**
     * Total number of audit events in the period
     */
    private long totalEvents;
    
    /**
     * Number of successful operations
     */
    private long successfulEvents;
    
    /**
     * Number of failed operations
     */
    private long failedEvents;
    
    /**
     * Number of critical severity events
     */
    private long criticalEvents;
    
    /**
     * Number of warning severity events
     */
    private long warningEvents;
    
    // ========================================
    // DISTRIBUTION BREAKDOWNS
    // ========================================
    
    /**
     * Distribution of events by type (top 10)
     * Key: Event type name
     * Value: Count
     */
    private Map<String, Long> eventsByType;
    
    /**
     * Distribution of events by category
     * Key: Category name
     * Value: Count
     */
    private Map<String, Long> eventsByCategory;
    
    /**
     * Distribution of events by severity level
     * Key: Severity level
     * Value: Count
     */
    private Map<String, Long> eventsBySeverity;
    
    /**
     * Distribution of events by operation result
     * Key: Result (SUCCESS, FAILURE, etc.)
     * Value: Count
     */
    private Map<String, Long> eventsByResult;
    
    // ========================================
    // TOP ENTITIES
    // ========================================
    
    /**
     * Top users by activity (top 10)
     * Key: User ID
     * Value: Event count
     */
    private Map<String, Long> topUsers;
    
    /**
     * Top IP addresses by activity (top 10)
     * Key: IP address
     * Value: Event count
     */
    private Map<String, Long> topIpAddresses;
    
    // ========================================
    // TIME PERIOD
    // ========================================
    
    /**
     * Start of the analysis period
     */
    private LocalDateTime periodStart;
    
    /**
     * End of the analysis period
     */
    private LocalDateTime periodEnd;
    
    // ========================================
    // COMPLIANCE SUMMARY
    // ========================================
    
    /**
     * Compliance-specific event summary
     */
    private ComplianceSummary complianceSummary;
    
    /**
     * Security-specific event summary
     */
    private SecuritySummary securitySummary;
    
    /**
     * Performance metrics and trends
     */
    private PerformanceMetrics performanceMetrics;
    
    // ========================================
    // NESTED DTOs
    // ========================================
    
    /**
     * Summary of compliance-relevant events
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceSummary {
        
        /**
         * Number of PCI DSS relevant events
         */
        private long pciRelevantCount;
        
        /**
         * Number of GDPR relevant events
         */
        private long gdprRelevantCount;
        
        /**
         * Number of SOX relevant events
         */
        private long soxRelevantCount;
        
        /**
         * Number of SOC 2 relevant events
         */
        private long soc2RelevantCount;
        
        /**
         * Number of KYC/AML events
         */
        private long kycAmlCount;
        
        /**
         * Number of events requiring notification
         */
        private long notificationRequiredCount;
        
        /**
         * Number of events requiring investigation
         */
        private long investigationRequiredCount;
        
        /**
         * Compliance score (0-100)
         * Based on ratio of compliant to non-compliant events
         */
        private Double complianceScore;
        
        /**
         * List of compliance violations or issues
         */
        private List<String> complianceIssues;
        
        /**
         * Get total compliance-relevant events
         */
        public long getTotalComplianceEvents() {
            return pciRelevantCount + gdprRelevantCount + 
                   soxRelevantCount + soc2RelevantCount;
        }
        
        /**
         * Check if any compliance issues exist
         */
        public boolean hasComplianceIssues() {
            return complianceIssues != null && !complianceIssues.isEmpty();
        }
    }
    
    /**
     * Summary of security-related events
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecuritySummary {
        
        /**
         * Number of fraud detection events
         */
        private long fraudDetections;
        
        /**
         * Number of suspicious activity alerts
         */
        private long suspiciousActivities;
        
        /**
         * Number of security alerts
         */
        private long securityAlerts;
        
        /**
         * Number of authentication failures
         */
        private long authenticationFailures;
        
        /**
         * Number of authorization denials
         */
        private long authorizationDenials;
        
        /**
         * Number of high-risk events (risk score > 70)
         */
        private long highRiskEvents;
        
        /**
         * Number of medium-risk events (risk score 40-70)
         */
        private long mediumRiskEvents;
        
        /**
         * Number of low-risk events (risk score < 40)
         */
        private long lowRiskEvents;
        
        /**
         * Number of blocked IP addresses
         */
        private long blockedIps;
        
        /**
         * Number of blacklisted entities
         */
        private long blacklistedEntities;
        
        /**
         * Average risk score across all events
         */
        private Double averageRiskScore;
        
        /**
         * Peak risk score in the period
         */
        private Integer peakRiskScore;
        
        /**
         * Security posture score (0-100)
         * Higher is better
         */
        private Double securityPostureScore;
        
        /**
         * List of critical security events requiring immediate attention
         */
        private List<SecurityIncident> criticalIncidents;
        
        /**
         * Get total security events
         */
        public long getTotalSecurityEvents() {
            return fraudDetections + suspiciousActivities + securityAlerts +
                   authenticationFailures + authorizationDenials;
        }
        
        /**
         * Get total risk events
         */
        public long getTotalRiskEvents() {
            return highRiskEvents + mediumRiskEvents + lowRiskEvents;
        }
        
        /**
         * Check if critical incidents exist
         */
        public boolean hasCriticalIncidents() {
            return criticalIncidents != null && !criticalIncidents.isEmpty();
        }
    }
    
    /**
     * Security incident details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityIncident {
        
        /**
         * Incident ID
         */
        private String incidentId;
        
        /**
         * Incident type
         */
        private String incidentType;
        
        /**
         * Incident severity
         */
        private String severity;
        
        /**
         * Affected user or entity
         */
        private String affectedEntity;
        
        /**
         * Incident timestamp
         */
        private LocalDateTime timestamp;
        
        /**
         * Risk score
         */
        private Integer riskScore;
        
        /**
         * Brief description
         */
        private String description;
        
        /**
         * Resolution status
         */
        private String status;
    }
    
    /**
     * Performance metrics and operational statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        
        /**
         * Average number of events per day
         */
        private long averageEventsPerDay;
        
        /**
         * Peak hour of activity (0-23)
         */
        private Integer peakHour;
        
        /**
         * Peak day of week (1=Monday, 7=Sunday)
         */
        private Integer peakDayOfWeek;
        
        /**
         * Average events per hour
         */
        private Double averageEventsPerHour;
        
        /**
         * Peak events in single hour
         */
        private Long peakEventsInHour;
        
        /**
         * Success rate percentage (0-100)
         */
        private Double successRate;
        
        /**
         * Failure rate percentage (0-100)
         */
        private Double failureRate;
        
        /**
         * List of slowest operations
         */
        private List<String> slowestOperations;
        
        /**
         * List of most frequently failed operations
         */
        private List<String> mostFailedOperations;
        
        /**
         * Storage size of audit logs (in bytes)
         */
        private Long storageSize;
        
        /**
         * Growth rate percentage compared to previous period
         */
        private Double growthRate;
        
        /**
         * Trend indicator (INCREASING, DECREASING, STABLE)
         */
        private String trend;
        
        /**
         * System health score (0-100)
         * Based on success rate, failure patterns, and response times
         */
        private Double healthScore;
        
        /**
         * Calculate derived metrics
         */
        public void calculateDerivedMetrics(long totalEvents) {
            if (totalEvents > 0) {
                this.successRate = (double) totalEvents / totalEvents * 100.0;
                this.failureRate = 100.0 - this.successRate;
            }
        }
        
        /**
         * Get formatted storage size
         */
        public String getFormattedStorageSize() {
            if (storageSize == null) {
                return "N/A";
            }
            if (storageSize < 1024) {
                return storageSize + " B";
            } else if (storageSize < 1024 * 1024) {
                return String.format("%.2f KB", storageSize / 1024.0);
            } else if (storageSize < 1024 * 1024 * 1024) {
                return String.format("%.2f MB", storageSize / (1024.0 * 1024.0));
            } else {
                return String.format("%.2f GB", storageSize / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }
    
    // ========================================
    // UTILITY METHODS
    // ========================================
    
    /**
     * Calculate overall success rate
     */
    public double getSuccessRate() {
        if (totalEvents == 0) return 0.0;
        return (double) successfulEvents / totalEvents * 100.0;
    }
    
    /**
     * Calculate overall failure rate
     */
    public double getFailureRate() {
        if (totalEvents == 0) return 0.0;
        return (double) failedEvents / totalEvents * 100.0;
    }
    
    /**
     * Calculate critical event percentage
     */
    public double getCriticalEventPercentage() {
        if (totalEvents == 0) return 0.0;
        return (double) criticalEvents / totalEvents * 100.0;
    }
    
    /**
     * Get period duration in days
     */
    public long getPeriodDurationDays() {
        if (periodStart == null || periodEnd == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd);
    }
    
    /**
     * Get period duration in hours
     */
    public long getPeriodDurationHours() {
        if (periodStart == null || periodEnd == null) return 0;
        return java.time.temporal.ChronoUnit.HOURS.between(periodStart, periodEnd);
    }
    
    /**
     * Check if summary indicates healthy system
     */
    public boolean isHealthy() {
        double successRate = getSuccessRate();
        double criticalPercentage = getCriticalEventPercentage();
        
        return successRate > 95.0 && criticalPercentage < 1.0;
    }
    
    /**
     * Get health status
     */
    public String getHealthStatus() {
        if (isHealthy()) {
            return "HEALTHY";
        } else if (getSuccessRate() > 90.0) {
            return "WARNING";
        } else {
            return "CRITICAL";
        }
    }
    
    /**
     * Get formatted success rate
     */
    public String getFormattedSuccessRate() {
        return String.format("%.2f%%", getSuccessRate());
    }
    
    /**
     * Get formatted failure rate
     */
    public String getFormattedFailureRate() {
        return String.format("%.2f%%", getFailureRate());
    }
}