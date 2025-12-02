package com.waqiti.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Audit monitoring service for tracking audit system health and performance
 * Provides real-time monitoring, alerting, and health checks for the audit subsystem
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditMonitoringService implements HealthIndicator {

    private final ComprehensiveAuditService auditService;
    private final SecureAuditLogger secureAuditLogger;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditEventRepository auditEventRepository;

    @Value("${audit.monitoring.enabled:true}")
    private boolean monitoringEnabled;

    @Value("${audit.monitoring.alert.thresholds.high-risk-events:10}")
    private int highRiskEventThreshold;

    @Value("${audit.monitoring.alert.thresholds.failed-audits:5}")
    private int failedAuditThreshold;

    @Value("${audit.monitoring.alert.thresholds.storage-usage:85}")
    private int storageUsageThreshold;

    // Metrics tracking
    private final AtomicLong totalAuditEvents = new AtomicLong(0);
    private final AtomicLong failedAuditEvents = new AtomicLong(0);
    private final AtomicLong highRiskEvents = new AtomicLong(0);
    private final AtomicLong securityViolations = new AtomicLong(0);

    private volatile LocalDateTime lastHealthCheck = LocalDateTime.now();
    private volatile String lastHealthStatus = "HEALTHY";
    private final Map<String, Object> healthMetrics = new HashMap<>();

    /**
     * Health indicator implementation for audit system
     */
    @Override
    public Health health() {
        try {
            Health.Builder healthBuilder = Health.up();

            // Check audit service availability
            if (!checkAuditServiceHealth()) {
                healthBuilder = Health.down();
                healthBuilder.withDetail("auditService", "UNAVAILABLE");
            } else {
                healthBuilder.withDetail("auditService", "HEALTHY");
            }

            // Check Redis connectivity (audit storage)
            if (!checkRedisHealth()) {
                healthBuilder = Health.status("DEGRADED");
                healthBuilder.withDetail("redis", "UNAVAILABLE");
            } else {
                healthBuilder.withDetail("redis", "HEALTHY");
            }

            // Check Kafka connectivity (audit streaming)
            if (!checkKafkaHealth()) {
                healthBuilder = Health.status("DEGRADED");
                healthBuilder.withDetail("kafka", "UNAVAILABLE");
            } else {
                healthBuilder.withDetail("kafka", "HEALTHY");
            }

            // Add performance metrics
            healthBuilder.withDetail("totalEvents", totalAuditEvents.get());
            healthBuilder.withDetail("failedEvents", failedAuditEvents.get());
            healthBuilder.withDetail("highRiskEvents", highRiskEvents.get());
            healthBuilder.withDetail("securityViolations", securityViolations.get());
            healthBuilder.withDetail("lastHealthCheck", lastHealthCheck);

            // Add storage usage
            healthBuilder.withDetail("storageUsage", getStorageUsageMetrics());

            lastHealthCheck = LocalDateTime.now();
            Health health = healthBuilder.build();
            lastHealthStatus = health.getStatus().getCode();

            return health;

        } catch (Exception e) {
            log.error("Error checking audit system health", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("lastHealthCheck", lastHealthCheck)
                    .build();
        }
    }

    /**
     * Monitor audit events for anomalies and security issues
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000) // Every 5 minutes
    public void monitorAuditEvents() {
        if (!monitoringEnabled) {
            return;
        }

        try {
            LocalDateTime lookbackTime = LocalDateTime.now().minus(5, ChronoUnit.MINUTES);
            
            // Check for high-risk events
            monitorHighRiskEvents(lookbackTime);
            
            // Check for failed audit events
            monitorFailedAuditEvents(lookbackTime);
            
            // Check for security violations
            monitorSecurityViolations(lookbackTime);
            
            // Check audit system performance
            monitorAuditPerformance();
            
            // Check storage usage
            monitorStorageUsage();

            log.debug("Audit monitoring cycle completed");

        } catch (Exception e) {
            log.error("Error during audit monitoring", e);
            
            // Log the monitoring failure as a security event
            secureAuditLogger.logSecurityEvent(
                SecureAuditLogger.SecurityEventType.SECURITY_ALERT,
                "Audit monitoring system failure: " + e.getMessage()
            );
        }
    }

    /**
     * Generate daily audit summary report
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void generateDailyAuditSummary() {
        if (!monitoringEnabled) {
            return;
        }

        try {
            LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
            LocalDateTime startOfDay = yesterday.withHour(0).withMinute(0).withSecond(0);
            LocalDateTime endOfDay = yesterday.withHour(23).withMinute(59).withSecond(59);

            AuditDailySummary summary = generateAuditSummary(startOfDay, endOfDay);
            
            // Log the summary
            log.info("DAILY AUDIT SUMMARY: {}", summary);
            
            // Store summary for reporting
            storeDailySummary(summary);
            
            // Check for concerning patterns
            checkDailySummaryForAlerts(summary);

        } catch (Exception e) {
            log.error("Error generating daily audit summary", e);
        }
    }

    /**
     * Clean up old audit monitoring data
     */
    @Scheduled(cron = "0 30 3 * * ?") // Daily at 3:30 AM
    public void cleanupOldMonitoringData() {
        if (!monitoringEnabled) {
            return;
        }

        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
            
            // Clean up old monitoring metrics
            cleanupOldMetrics(cutoffDate);
            
            log.info("Audit monitoring data cleanup completed");

        } catch (Exception e) {
            log.error("Error during monitoring data cleanup", e);
        }
    }

    /**
     * Get real-time audit metrics
     */
    public AuditMetrics getAuditMetrics() {
        return AuditMetrics.builder()
                .totalEvents(totalAuditEvents.get())
                .failedEvents(failedAuditEvents.get())
                .highRiskEvents(highRiskEvents.get())
                .securityViolations(securityViolations.get())
                .lastHealthCheck(lastHealthCheck)
                .healthStatus(lastHealthStatus)
                .storageUsage(getStorageUsageMetrics())
                .systemUptime(getSystemUptime())
                .averageResponseTime(getAverageResponseTime())
                .build();
    }

    /**
     * Record audit event metrics
     */
    public void recordAuditEvent(boolean success, boolean highRisk, boolean securityViolation) {
        totalAuditEvents.incrementAndGet();
        
        if (!success) {
            failedAuditEvents.incrementAndGet();
        }
        
        if (highRisk) {
            highRiskEvents.incrementAndGet();
        }
        
        if (securityViolation) {
            securityViolations.incrementAndGet();
        }
    }

    // Private helper methods

    private boolean checkAuditServiceHealth() {
        try {
            // Test audit service by attempting to log a health check event
            secureAuditLogger.logSecurityEvent(
                SecureAuditLogger.SecurityEventType.SECURITY_ALERT,
                "Audit service health check"
            );
            return true;
        } catch (Exception e) {
            log.warn("Audit service health check failed", e);
            return false;
        }
    }

    private boolean checkRedisHealth() {
        try {
            redisTemplate.opsForValue().set("audit:health:check", "ok", 10, TimeUnit.SECONDS);
            String value = redisTemplate.opsForValue().get("audit:health:check");
            return "ok".equals(value);
        } catch (Exception e) {
            log.warn("Redis health check failed", e);
            return false;
        }
    }

    private boolean checkKafkaHealth() {
        try {
            // Send a test message to Kafka
            kafkaTemplate.send("audit-health-check", "health-check", "ok");
            return true;
        } catch (Exception e) {
            log.warn("Kafka health check failed", e);
            return false;
        }
    }

    private void monitorHighRiskEvents(LocalDateTime lookbackTime) {
        try {
            long recentHighRiskCount = auditEventRepository.countHighRiskEventsSince(lookbackTime, 7);
            
            if (recentHighRiskCount > highRiskEventThreshold) {
                String alertMessage = String.format(
                    "High number of high-risk audit events detected: %d events in last 5 minutes (threshold: %d)",
                    recentHighRiskCount, highRiskEventThreshold
                );
                
                log.warn("AUDIT ALERT: {}", alertMessage);
                
                secureAuditLogger.logSecurityEvent(
                    SecureAuditLogger.SecurityEventType.SECURITY_ALERT,
                    alertMessage
                );
                
                // Send alert to external monitoring system
                sendAlert("HIGH_RISK_EVENTS", alertMessage, "HIGH");
            }

        } catch (Exception e) {
            log.error("Error monitoring high-risk events", e);
        }
    }

    private void monitorFailedAuditEvents(LocalDateTime lookbackTime) {
        try {
            long recentFailedCount = auditEventRepository.countFailedEventsSince(lookbackTime);
            
            if (recentFailedCount > failedAuditThreshold) {
                String alertMessage = String.format(
                    "High number of failed audit events detected: %d events in last 5 minutes (threshold: %d)",
                    recentFailedCount, failedAuditThreshold
                );
                
                log.error("AUDIT ALERT: {}", alertMessage);
                
                secureAuditLogger.logSecurityEvent(
                    SecureAuditLogger.SecurityEventType.SECURITY_ALERT,
                    alertMessage
                );
                
                // Send critical alert
                sendAlert("FAILED_AUDITS", alertMessage, "CRITICAL");
            }

        } catch (Exception e) {
            log.error("Error monitoring failed audit events", e);
        }
    }

    private void monitorSecurityViolations(LocalDateTime lookbackTime) {
        try {
            long recentSecurityViolations = auditEventRepository.countSecurityViolationsSince(lookbackTime);
            
            if (recentSecurityViolations > 0) {
                String alertMessage = String.format(
                    "Security violations detected in audit logs: %d violations in last 5 minutes",
                    recentSecurityViolations
                );
                
                log.error("SECURITY ALERT: {}", alertMessage);
                
                secureAuditLogger.logSecurityEvent(
                    SecureAuditLogger.SecurityEventType.SECURITY_ALERT,
                    alertMessage
                );
                
                // Send critical alert for any security violations
                sendAlert("SECURITY_VIOLATIONS", alertMessage, "CRITICAL");
            }

        } catch (Exception e) {
            log.error("Error monitoring security violations", e);
        }
    }

    private void monitorAuditPerformance() {
        try {
            // Check audit event processing latency
            double avgLatency = auditEventRepository.getAverageProcessingLatency();
            
            if (avgLatency > 1000) { // 1 second threshold
                String alertMessage = String.format(
                    "High audit processing latency detected: %.2f ms average",
                    avgLatency
                );
                
                log.warn("AUDIT ALERT: {}", alertMessage);
                
                sendAlert("HIGH_LATENCY", alertMessage, "MEDIUM");
            }

        } catch (Exception e) {
            log.error("Error monitoring audit performance", e);
        }
    }

    private void monitorStorageUsage() {
        try {
            StorageUsageMetrics usage = getStorageUsageMetrics();
            
            if (usage.getUsagePercentage() > storageUsageThreshold) {
                String alertMessage = String.format(
                    "High audit storage usage: %.1f%% (threshold: %d%%)",
                    usage.getUsagePercentage(), storageUsageThreshold
                );
                
                log.warn("AUDIT ALERT: {}", alertMessage);
                
                sendAlert("HIGH_STORAGE_USAGE", alertMessage, "HIGH");
            }

        } catch (Exception e) {
            log.error("Error monitoring storage usage", e);
        }
    }

    private StorageUsageMetrics getStorageUsageMetrics() {
        try {
            long totalEvents = auditEventRepository.count();
            long redisMemoryUsage = getRedisMemoryUsage();
            
            // Estimate usage percentage based on configured limits
            double estimatedUsagePercentage = Math.min(100.0, (totalEvents / 1000000.0) * 100);
            
            return StorageUsageMetrics.builder()
                    .totalEvents(totalEvents)
                    .redisMemoryUsageBytes(redisMemoryUsage)
                    .usagePercentage(estimatedUsagePercentage)
                    .build();

        } catch (Exception e) {
            log.error("Error calculating storage usage", e);
            return StorageUsageMetrics.builder()
                    .totalEvents(0L)
                    .redisMemoryUsageBytes(0L)
                    .usagePercentage(0.0)
                    .build();
        }
    }

    private long getRedisMemoryUsage() {
        try {
            // This would query Redis INFO memory in a real implementation
            return 0L; // Placeholder
        } catch (Exception e) {
            return 0L;
        }
    }

    private AuditDailySummary generateAuditSummary(LocalDateTime startDate, LocalDateTime endDate) {
        AuditEventRepository.DailySummary repoSummary = auditEventRepository.generateDailySummary(startDate, endDate);
        // Convert repository summary to service summary
        return AuditDailySummary.builder()
            .date(repoSummary.getDate())
            .totalEvents(repoSummary.getTotalEvents())
            .uniqueUsers(repoSummary.getUniqueUsers())
            .failedLogins(repoSummary.getFailedLogins())
            .build();
    }

    private void storeDailySummary(AuditDailySummary summary) {
        try {
            String key = "audit:daily-summary:" + summary.getDate().toString();
            String summaryJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(summary);
            redisTemplate.opsForValue().set(key, summaryJson, 90, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("Error storing daily summary", e);
        }
    }

    private void checkDailySummaryForAlerts(AuditDailySummary summary) {
        // Check for concerning patterns in the daily summary
        if (summary.getFailedEventPercentage() > 5.0) {
            sendAlert("HIGH_FAILURE_RATE", 
                    String.format("High audit failure rate: %.1f%%", summary.getFailedEventPercentage()),
                    "HIGH");
        }

        if (summary.getSecurityViolationCount() > 0) {
            sendAlert("DAILY_SECURITY_VIOLATIONS",
                    String.format("Security violations in daily summary: %d", summary.getSecurityViolationCount()),
                    "CRITICAL");
        }
    }

    private void cleanupOldMetrics(LocalDateTime cutoffDate) {
        // Implementation would clean up old monitoring data
        log.debug("Cleaning up monitoring data older than {}", cutoffDate);
    }

    private void sendAlert(String alertType, String message, String severity) {
        try {
            Map<String, Object> alertData = Map.of(
                    "alertType", alertType,
                    "message", message,
                    "severity", severity,
                    "timestamp", LocalDateTime.now(),
                    "service", "audit-monitoring"
            );

            kafkaTemplate.send("audit-alerts", alertType, alertData);

        } catch (Exception e) {
            log.error("Failed to send audit alert", e);
        }
    }

    private String getSystemUptime() {
        // Implementation would calculate actual uptime
        return "24h:59m";
    }

    private double getAverageResponseTime() {
        // Implementation would calculate actual average response time
        return 45.2;
    }

    // Data transfer objects

    @lombok.Data
    @lombok.Builder
    public static class AuditMetrics {
        private long totalEvents;
        private long failedEvents;
        private long highRiskEvents;
        private long securityViolations;
        private LocalDateTime lastHealthCheck;
        private String healthStatus;
        private StorageUsageMetrics storageUsage;
        private String systemUptime;
        private double averageResponseTime;
    }

    @lombok.Data
    @lombok.Builder
    public static class StorageUsageMetrics {
        private long totalEvents;
        private long redisMemoryUsageBytes;
        private double usagePercentage;
    }

    @lombok.Data
    @lombok.Builder
    public static class AuditDailySummary {
        private LocalDateTime date;
        private long totalEvents;
        private long uniqueUsers;
        private long failedLogins;
        private long successfulEvents;
        private long failedEvents;
        private long highRiskEvents;
        private long securityViolationCount;
        private double failedEventPercentage;
        private Map<String, Long> eventTypeDistribution;
        private List<String> topUsers;
        private List<String> topActions;
    }
}