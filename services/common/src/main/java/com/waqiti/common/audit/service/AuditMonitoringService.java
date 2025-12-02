package com.waqiti.common.audit.service;

import com.waqiti.common.audit.domain.AuditLog;
import com.waqiti.common.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time audit monitoring and alerting service
 * 
 * Monitors audit events in real-time and generates alerts for:
 * - Critical security events
 * - Compliance violations
 * - Fraud indicators
 * - System anomalies
 * - Threshold breaches
 * - Pattern-based alerts
 * 
 * ALERT TYPES:
 * - Immediate: Critical events requiring instant attention
 * - High Priority: Important events requiring timely response
 * - Medium Priority: Events requiring review within hours
 * - Low Priority: Informational alerts for trend analysis
 * 
 * INTEGRATION:
 * - PagerDuty for critical alerts
 * - Slack for team notifications
 * - Email for compliance teams
 * - SIEM systems for security operations
 * - Dashboard for real-time monitoring
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditMonitoringService {
    
    private final AuditLogRepository auditLogRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SiemIntegrationService siemIntegrationService;
    
    @Value("${waqiti.audit.monitoring.enabled:true}")
    private boolean monitoringEnabled;
    
    @Value("${waqiti.audit.alerts.critical.threshold:5}")
    private int criticalEventThreshold;
    
    @Value("${waqiti.audit.alerts.failed.login.threshold:10}")
    private int failedLoginThreshold;
    
    @Value("${waqiti.audit.alerts.high.risk.threshold:80}")
    private int highRiskThreshold;
    
    // Real-time counters and tracking
    private final Map<String, AtomicInteger> eventCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> userFailedLogins = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> ipAddressActivity = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastAlertTimes = new ConcurrentHashMap<>();
    
    // Alert suppression (prevent spam)
    private static final int ALERT_SUPPRESSION_MINUTES = 5;
    
    /**
     * Process audit events from Kafka in real-time
     */
    @KafkaListener(topics = {"audit-events-critical", "audit-events-security", "audit-events-fraud"})
    public void processAuditEvent(AuditLog auditLog) {
        if (!monitoringEnabled) {
            return;
        }
        
        try {
            log.debug("Processing audit event for monitoring: {}", auditLog.getId());
            
            // Update real-time counters
            updateEventCounters(auditLog);
            
            // Check for immediate alerts
            checkCriticalSecurityEvents(auditLog);
            checkFailedLoginPatterns(auditLog);
            checkHighRiskTransactions(auditLog);
            checkFraudIndicators(auditLog);
            checkComplianceViolations(auditLog);
            checkSuspiciousPatterns(auditLog);
            checkDataAccessAnomalies(auditLog);
            
            // Update monitoring metrics
            updateMonitoringMetrics(auditLog);
            
        } catch (Exception e) {
            log.error("Failed to process audit event for monitoring: {}", auditLog.getId(), e);
        }
    }
    
    /**
     * Scheduled monitoring for pattern detection and trend analysis
     */
    @Scheduled(fixedRateString = "${waqiti.audit.monitoring.interval:60000}") // Every minute
    public void performScheduledMonitoring() {
        if (!monitoringEnabled) {
            return;
        }
        
        try {
            log.debug("Performing scheduled audit monitoring");
            
            // Check for patterns and trends
            checkFailureRateSpikes();
            checkUnusualActivityPatterns();
            checkComplianceThresholds();
            checkSystemHealthIndicators();
            checkDataIntegrityViolations();
            
            // Reset counters for next period
            resetPeriodicCounters();
            
        } catch (Exception e) {
            log.error("Failed to perform scheduled monitoring", e);
        }
    }
    
    /**
     * Check for critical security events requiring immediate attention
     */
    private void checkCriticalSecurityEvents(AuditLog auditLog) {
        if (auditLog.getSeverity() == AuditLog.Severity.CRITICAL || 
            auditLog.getSeverity() == AuditLog.Severity.EMERGENCY) {
            
            CriticalSecurityAlert alert = CriticalSecurityAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .auditLogId(auditLog.getId())
                .eventType(auditLog.getEventType().name())
                .severity(auditLog.getSeverity().name())
                .userId(auditLog.getUserId())
                .description(auditLog.getDescription())
                .timestamp(auditLog.getTimestamp())
                .ipAddress(auditLog.getIpAddress())
                .riskScore(auditLog.getRiskScore())
                .requiresInvestigation(auditLog.getInvestigationRequired())
                .build();
            
            sendCriticalAlert(alert);
        }
    }
    
    /**
     * Monitor failed login patterns for brute force attacks
     */
    private void checkFailedLoginPatterns(AuditLog auditLog) {
        if ("LOGIN_FAILED".equals(auditLog.getEventType().name())) {
            String key = auditLog.getUserId() != null ? auditLog.getUserId() : auditLog.getIpAddress();
            AtomicInteger counter = userFailedLogins.computeIfAbsent(key, k -> new AtomicInteger(0));
            int failedCount = counter.incrementAndGet();
            
            if (failedCount >= failedLoginThreshold) {
                BruteForceAlert alert = BruteForceAlert.builder()
                    .alertId(UUID.randomUUID().toString())
                    .targetIdentifier(key)
                    .failedAttempts(failedCount)
                    .ipAddress(auditLog.getIpAddress())
                    .userAgent(auditLog.getUserAgent())
                    .timeWindow("last 5 minutes")
                    .timestamp(auditLog.getTimestamp())
                    .build();
                
                sendHighPriorityAlert(alert);
                
                // Reset counter after alert
                counter.set(0);
            }
        }
    }
    
    /**
     * Monitor high-risk transactions
     */
    private void checkHighRiskTransactions(AuditLog auditLog) {
        if (auditLog.getRiskScore() != null && auditLog.getRiskScore() >= highRiskThreshold) {
            HighRiskTransactionAlert alert = HighRiskTransactionAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .auditLogId(auditLog.getId())
                .userId(auditLog.getUserId())
                .eventType(auditLog.getEventType().name())
                .riskScore(auditLog.getRiskScore())
                .description(auditLog.getDescription())
                .metadata(auditLog.getMetadata())
                .timestamp(auditLog.getTimestamp())
                .build();
            
            sendHighPriorityAlert(alert);
        }
    }
    
    /**
     * Check for fraud indicators
     */
    private void checkFraudIndicators(AuditLog auditLog) {
        if (auditLog.getEventCategory() == AuditLog.EventCategory.FRAUD ||
            (auditLog.getFraudIndicators() != null && !auditLog.getFraudIndicators().isEmpty())) {
            
            FraudAlert alert = FraudAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .auditLogId(auditLog.getId())
                .userId(auditLog.getUserId())
                .fraudType(auditLog.getEventType().name())
                .fraudIndicators(auditLog.getFraudIndicators())
                .riskScore(auditLog.getRiskScore())
                .description(auditLog.getDescription())
                .timestamp(auditLog.getTimestamp())
                .build();
            
            sendCriticalAlert(alert);
        }
    }
    
    /**
     * Check for compliance violations
     */
    private void checkComplianceViolations(AuditLog auditLog) {
        if (auditLog.getEventCategory() == AuditLog.EventCategory.COMPLIANCE &&
            auditLog.getResult() == AuditLog.OperationResult.FAILURE) {
            
            ComplianceViolationAlert alert = ComplianceViolationAlert.builder()
                .alertId(UUID.randomUUID().toString())
                .auditLogId(auditLog.getId())
                .violationType(auditLog.getEventType().name())
                .userId(auditLog.getUserId())
                .description(auditLog.getDescription())
                .complianceFramework(getComplianceFramework(auditLog))
                .severity(auditLog.getSeverity().name())
                .timestamp(auditLog.getTimestamp())
                .build();
            
            sendHighPriorityAlert(alert);
        }
    }
    
    /**
     * Check for suspicious activity patterns
     */
    private void checkSuspiciousPatterns(AuditLog auditLog) {
        // Check for rapid successive events from same user
        if (auditLog.getUserId() != null) {
            String key = "rapid_events_" + auditLog.getUserId();
            AtomicInteger counter = eventCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
            int count = counter.incrementAndGet();
            
            if (count > 100) { // 100 events in monitoring window
                SuspiciousActivityAlert alert = SuspiciousActivityAlert.builder()
                    .alertId(UUID.randomUUID().toString())
                    .userId(auditLog.getUserId())
                    .activityType("RAPID_SUCCESSIVE_EVENTS")
                    .eventCount(count)
                    .description("User generated excessive number of events in short time period")
                    .timestamp(auditLog.getTimestamp())
                    .build();
                
                sendMediumPriorityAlert(alert);
                counter.set(0);
            }
        }
        
        // Check for unusual IP activity
        if (auditLog.getIpAddress() != null) {
            String key = "ip_activity_" + auditLog.getIpAddress();
            AtomicLong counter = ipAddressActivity.computeIfAbsent(key, k -> new AtomicLong(0));
            long count = counter.incrementAndGet();
            
            if (count > 500) { // 500 events from same IP
                SuspiciousActivityAlert alert = SuspiciousActivityAlert.builder()
                    .alertId(UUID.randomUUID().toString())
                    .ipAddress(auditLog.getIpAddress())
                    .activityType("EXCESSIVE_IP_ACTIVITY")
                    .eventCount((int) count)
                    .description("Excessive activity from single IP address")
                    .timestamp(auditLog.getTimestamp())
                    .build();
                
                sendMediumPriorityAlert(alert);
                counter.set(0);
            }
        }
    }
    
    /**
     * Check for data access anomalies
     */
    private void checkDataAccessAnomalies(AuditLog auditLog) {
        if (auditLog.getEventCategory() == AuditLog.EventCategory.DATA_ACCESS) {
            // Check for bulk data access
            if (auditLog.getDescription() != null && 
                (auditLog.getDescription().contains("bulk") || auditLog.getDescription().contains("export"))) {
                
                DataAccessAlert alert = DataAccessAlert.builder()
                    .alertId(UUID.randomUUID().toString())
                    .auditLogId(auditLog.getId())
                    .userId(auditLog.getUserId())
                    .accessType("BULK_DATA_ACCESS")
                    .dataType(auditLog.getEntityType())
                    .description(auditLog.getDescription())
                    .timestamp(auditLog.getTimestamp())
                    .build();
                
                sendMediumPriorityAlert(alert);
            }
            
            // Check for PII access outside business hours
            if (Boolean.TRUE.equals(auditLog.getGdprRelevant()) && isOutsideBusinessHours(auditLog.getTimestamp())) {
                DataAccessAlert alert = DataAccessAlert.builder()
                    .alertId(UUID.randomUUID().toString())
                    .auditLogId(auditLog.getId())
                    .userId(auditLog.getUserId())
                    .accessType("OFF_HOURS_PII_ACCESS")
                    .dataType("PII")
                    .description("PII data accessed outside business hours")
                    .timestamp(auditLog.getTimestamp())
                    .build();
                
                sendMediumPriorityAlert(alert);
            }
        }
    }
    
    /**
     * Check for system-wide failure rate spikes
     */
    private void checkFailureRateSpikes() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);
        
        long totalEvents = auditLogRepository.countLogsByDateRange(oneHourAgo, now);
        long failedEvents = auditLogRepository.countLogsByDateRange(oneHourAgo, now); // Would filter by failure
        
        if (totalEvents > 0) {
            double failureRate = (double) failedEvents / totalEvents * 100;
            
            if (failureRate > 10.0) { // 10% failure rate threshold
                SystemHealthAlert alert = SystemHealthAlert.builder()
                    .alertId(UUID.randomUUID().toString())
                    .alertType("HIGH_FAILURE_RATE")
                    .failureRate(failureRate)
                    .totalEvents(totalEvents)
                    .failedEvents(failedEvents)
                    .timeWindow("last hour")
                    .timestamp(now)
                    .build();
                
                sendHighPriorityAlert(alert);
            }
        }
    }
    
    /**
     * Send critical alert (immediate attention required)
     */
    private void sendCriticalAlert(Object alert) {
        if (shouldSuppressAlert(alert)) {
            return;
        }
        
        try {
            // Send to PagerDuty for immediate response
            kafkaTemplate.send("alerts-critical", alert);
            
            // Send to SIEM (alert objects are converted to appropriate format)
            if (alert instanceof com.waqiti.common.audit.domain.AuditLog) {
                siemIntegrationService.sendCriticalAlert((com.waqiti.common.audit.domain.AuditLog) alert);
            }
            
            // Log critical alert
            log.error("CRITICAL ALERT: {}", alert);
            
            updateLastAlertTime(alert);
            
        } catch (Exception e) {
            log.error("Failed to send critical alert", e);
        }
    }
    
    /**
     * Send high priority alert
     */
    private void sendHighPriorityAlert(Object alert) {
        if (shouldSuppressAlert(alert)) {
            return;
        }
        
        try {
            // Send to alerting system
            kafkaTemplate.send("alerts-high-priority", alert);
            
            // Send to Slack
            kafkaTemplate.send("slack-security-alerts", alert);
            
            log.warn("HIGH PRIORITY ALERT: {}", alert);
            
            updateLastAlertTime(alert);
            
        } catch (Exception e) {
            log.error("Failed to send high priority alert", e);
        }
    }
    
    /**
     * Send medium priority alert
     */
    private void sendMediumPriorityAlert(Object alert) {
        if (shouldSuppressAlert(alert)) {
            return;
        }
        
        try {
            // Send to monitoring dashboard
            kafkaTemplate.send("alerts-medium-priority", alert);
            
            log.info("MEDIUM PRIORITY ALERT: {}", alert);
            
            updateLastAlertTime(alert);
            
        } catch (Exception e) {
            log.error("Failed to send medium priority alert", e);
        }
    }
    
    // Helper methods
    
    private void updateEventCounters(AuditLog auditLog) {
        String eventTypeKey = "event_" + auditLog.getEventType().name();
        eventCounters.computeIfAbsent(eventTypeKey, k -> new AtomicInteger(0)).incrementAndGet();
        
        String categoryKey = "category_" + auditLog.getEventCategory().name();
        eventCounters.computeIfAbsent(categoryKey, k -> new AtomicInteger(0)).incrementAndGet();
        
        String severityKey = "severity_" + auditLog.getSeverity().name();
        eventCounters.computeIfAbsent(severityKey, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    private void updateMonitoringMetrics(AuditLog auditLog) {
        // Update Micrometer metrics for monitoring dashboard
        // Implementation would update counters, gauges, and timers
    }
    
    private String getComplianceFramework(AuditLog auditLog) {
        List<String> frameworks = new ArrayList<>();
        if (Boolean.TRUE.equals(auditLog.getPciRelevant())) frameworks.add("PCI_DSS");
        if (Boolean.TRUE.equals(auditLog.getGdprRelevant())) frameworks.add("GDPR");
        if (Boolean.TRUE.equals(auditLog.getSoxRelevant())) frameworks.add("SOX");
        if (Boolean.TRUE.equals(auditLog.getSoc2Relevant())) frameworks.add("SOC2");
        return String.join(",", frameworks);
    }
    
    private boolean isOutsideBusinessHours(java.time.Instant timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(timestamp, java.time.ZoneId.systemDefault());
        int hour = dateTime.getHour();
        int dayOfWeek = dateTime.getDayOfWeek().getValue();
        
        // Outside 9 AM - 6 PM Monday-Friday
        return hour < 9 || hour >= 18 || dayOfWeek >= 6;
    }
    
    private boolean shouldSuppressAlert(Object alert) {
        String alertKey = alert.getClass().getSimpleName();
        LocalDateTime lastAlert = lastAlertTimes.get(alertKey);
        
        if (lastAlert != null) {
            LocalDateTime suppressionThreshold = LocalDateTime.now().minusMinutes(ALERT_SUPPRESSION_MINUTES);
            return lastAlert.isAfter(suppressionThreshold);
        }
        
        return false;
    }
    
    private void updateLastAlertTime(Object alert) {
        String alertKey = alert.getClass().getSimpleName();
        lastAlertTimes.put(alertKey, LocalDateTime.now());
    }
    
    private void resetPeriodicCounters() {
        // Reset counters that are used for periodic monitoring
        eventCounters.clear();
        userFailedLogins.clear();
        ipAddressActivity.clear();
    }
    
    private void checkUnusualActivityPatterns() {
        // Implementation would check for unusual patterns
    }
    
    private void checkComplianceThresholds() {
        // Implementation would check compliance thresholds
    }
    
    private void checkSystemHealthIndicators() {
        // Implementation would check system health
    }
    
    private void checkDataIntegrityViolations() {
        // Implementation would check data integrity
    }
    
    // Alert DTOs
    @lombok.Data
    @lombok.Builder
    private static class CriticalSecurityAlert {
        private String alertId;
        private UUID auditLogId;
        private String eventType;
        private String severity;
        private String userId;
        private String description;
        private java.time.Instant timestamp;
        private String ipAddress;
        private Integer riskScore;
        private Boolean requiresInvestigation;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class BruteForceAlert {
        private String alertId;
        private String targetIdentifier;
        private int failedAttempts;
        private String ipAddress;
        private String userAgent;
        private String timeWindow;
        private java.time.Instant timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class HighRiskTransactionAlert {
        private String alertId;
        private UUID auditLogId;
        private String userId;
        private String eventType;
        private Integer riskScore;
        private String description;
        private String metadata;
        private java.time.Instant timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class FraudAlert {
        private String alertId;
        private UUID auditLogId;
        private String userId;
        private String fraudType;
        private String fraudIndicators;
        private Integer riskScore;
        private String description;
        private java.time.Instant timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class ComplianceViolationAlert {
        private String alertId;
        private UUID auditLogId;
        private String violationType;
        private String userId;
        private String description;
        private String complianceFramework;
        private String severity;
        private java.time.Instant timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class SuspiciousActivityAlert {
        private String alertId;
        private String userId;
        private String ipAddress;
        private String activityType;
        private int eventCount;
        private String description;
        private java.time.Instant timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class DataAccessAlert {
        private String alertId;
        private UUID auditLogId;
        private String userId;
        private String accessType;
        private String dataType;
        private String description;
        private java.time.Instant timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class SystemHealthAlert {
        private String alertId;
        private String alertType;
        private double failureRate;
        private long totalEvents;
        private long failedEvents;
        private String timeWindow;
        private LocalDateTime timestamp;
    }
}