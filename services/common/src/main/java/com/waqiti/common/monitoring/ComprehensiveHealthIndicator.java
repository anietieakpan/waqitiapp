package com.waqiti.common.monitoring;

import com.waqiti.common.health.ComprehensiveHealthIndicator.ComprehensiveHealthReport;
import com.waqiti.common.health.ComprehensiveHealthIndicator.HealthStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import javax.annotation.PostConstruct;

/**
 * Monitoring-focused health indicator that integrates with the main health system
 * Provides monitoring and alerting capabilities without Spring Boot Actuator dependencies
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComprehensiveHealthIndicator {

    @Autowired(required = false)
    private final DataSource dataSource;
    
    @Autowired(required = false)
    private final RedisConnectionFactory redisConnectionFactory;
    
    @Autowired(required = false)
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Autowired(required = false)
    private final JdbcTemplate jdbcTemplate;

    // Delegate to the main health indicator
    @Autowired(required = false)
    private com.waqiti.common.health.ComprehensiveHealthIndicator mainHealthIndicator;

    @Value("${spring.application.name:waqiti-service}")
    private String serviceName;

    @Value("${monitoring.health.check.interval.seconds:30}")
    private int healthCheckIntervalSeconds;

    @Value("${monitoring.alerts.enabled:true}")
    private boolean alertingEnabled;

    @Value("${monitoring.metrics.enabled:true}")
    private boolean metricsEnabled;

    private volatile MonitoringHealthStatus lastMonitoringStatus = MonitoringHealthStatus.UNKNOWN;
    private volatile Instant lastHealthCheck = Instant.now();

    @PostConstruct
    public void initialize() {
        log.info("Initializing Monitoring Health Indicator for service: {}", serviceName);
        
        // Start periodic health monitoring if main health indicator is available
        if (mainHealthIndicator != null) {
            startPeriodicHealthMonitoring();
        } else {
            log.warn("Main health indicator not available, monitoring features will be limited");
        }
    }

    /**
     * Perform monitoring-focused health check
     */
    public MonitoringHealthReport performMonitoringHealthCheck() {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Starting monitoring health check for service: {}", serviceName);
            
            MonitoringHealthStatus status = MonitoringHealthStatus.HEALTHY;
            Map<String, Object> monitoringDetails = new HashMap<>();
            List<String> alerts = new ArrayList<>();

            if (mainHealthIndicator != null) {
                // Delegate to main health indicator
                ComprehensiveHealthReport mainReport = mainHealthIndicator.performHealthCheck();
                
                // Convert main health status to monitoring status
                status = convertHealthStatus(mainReport.getOverallStatus());
                
                // Extract monitoring-relevant details
                monitoringDetails.put("mainHealthReport", Map.of(
                    "overallStatus", mainReport.getOverallStatus().toString(),
                    "checkDurationMs", mainReport.getCheckDurationMs(),
                    "uptime", mainReport.getUptime(),
                    "componentCount", mainReport.getComponentHealth().size()
                ));

                // Generate alerts if needed
                if (alertingEnabled && status != MonitoringHealthStatus.HEALTHY) {
                    alerts.add(String.format("Service %s health degraded: %s", 
                        serviceName, mainReport.getOverallStatus()));
                }
                
            } else {
                // Fallback monitoring checks
                status = performBasicMonitoringChecks(monitoringDetails, alerts);
            }

            // Add monitoring-specific metrics
            addMonitoringMetrics(monitoringDetails);

            long endTime = System.currentTimeMillis();
            lastMonitoringStatus = status;
            lastHealthCheck = Instant.now();

            MonitoringHealthReport report = MonitoringHealthReport.builder()
                .serviceName(serviceName)
                .status(status)
                .details(monitoringDetails)
                .alerts(alerts)
                .checkTimestamp(lastHealthCheck)
                .checkDurationMs(endTime - startTime)
                .build();

            // Log monitoring events
            logMonitoringEvent(report);

            return report;

        } catch (Exception e) {
            log.error("Monitoring health check failed for service: {}", serviceName, e);
            lastMonitoringStatus = MonitoringHealthStatus.UNHEALTHY;
            
            return MonitoringHealthReport.builder()
                .serviceName(serviceName)
                .status(MonitoringHealthStatus.UNHEALTHY)
                .details(Map.of("error", e.getMessage()))
                .alerts(List.of("Monitoring health check failed: " + e.getMessage()))
                .checkTimestamp(Instant.now())
                .checkDurationMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    /**
     * Start periodic health monitoring
     */
    private void startPeriodicHealthMonitoring() {
        CompletableFuture.runAsync(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(healthCheckIntervalSeconds * 1000L);
                    performMonitoringHealthCheck();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("Error in periodic health monitoring", e);
                }
            }
        });
    }

    /**
     * Convert main health status to monitoring status
     */
    private MonitoringHealthStatus convertHealthStatus(HealthStatus healthStatus) {
        switch (healthStatus) {
            case HEALTHY:
                return MonitoringHealthStatus.HEALTHY;
            case DEGRADED:
                return MonitoringHealthStatus.DEGRADED;
            case UNHEALTHY:
                return MonitoringHealthStatus.UNHEALTHY;
            default:
                return MonitoringHealthStatus.UNKNOWN;
        }
    }

    /**
     * Perform basic monitoring checks when main health indicator is not available
     */
    private MonitoringHealthStatus performBasicMonitoringChecks(Map<String, Object> details, List<String> alerts) {
        MonitoringHealthStatus status = MonitoringHealthStatus.HEALTHY;

        // Basic database check
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection()) {
                if (!connection.isValid(5)) {
                    status = MonitoringHealthStatus.UNHEALTHY;
                    alerts.add("Database connection invalid");
                }
                details.put("database", "available");
            } catch (Exception e) {
                status = MonitoringHealthStatus.UNHEALTHY;
                alerts.add("Database connection failed: " + e.getMessage());
                details.put("database", "unavailable");
            }
        }

        // Basic Redis check
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().get("health-check");
                details.put("redis", "available");
            } catch (Exception e) {
                if (status == MonitoringHealthStatus.HEALTHY) {
                    status = MonitoringHealthStatus.DEGRADED;
                }
                alerts.add("Redis connection issues: " + e.getMessage());
                details.put("redis", "degraded");
            }
        }

        return status;
    }

    /**
     * Add monitoring-specific metrics
     */
    private void addMonitoringMetrics(Map<String, Object> details) {
        if (!metricsEnabled) {
            return;
        }

        try {
            details.put("monitoring", Map.of(
                "lastCheckTime", lastHealthCheck.toString(),
                "lastStatus", lastMonitoringStatus.toString(),
                "hostInfo", getHostInfo(),
                "jvmInfo", getJvmInfo()
            ));
        } catch (Exception e) {
            log.debug("Failed to add monitoring metrics", e);
        }
    }

    /**
     * Get host information
     */
    private Map<String, String> getHostInfo() {
        Map<String, String> hostInfo = new HashMap<>();
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            hostInfo.put("hostname", localhost.getHostName());
            hostInfo.put("ip", localhost.getHostAddress());
        } catch (Exception e) {
            hostInfo.put("error", "Could not retrieve host info");
        }
        return hostInfo;
    }

    /**
     * Get JVM information
     */
    private Map<String, Object> getJvmInfo() {
        Runtime runtime = Runtime.getRuntime();
        return Map.of(
            "totalMemoryMB", runtime.totalMemory() / (1024 * 1024),
            "freeMemoryMB", runtime.freeMemory() / (1024 * 1024),
            "usedMemoryMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
            "maxMemoryMB", runtime.maxMemory() / (1024 * 1024),
            "processors", runtime.availableProcessors()
        );
    }

    /**
     * Log monitoring events
     */
    private void logMonitoringEvent(MonitoringHealthReport report) {
        if (report.getStatus() != MonitoringHealthStatus.HEALTHY) {
            log.warn("Service {} monitoring status: {} - Alerts: {}", 
                serviceName, report.getStatus(), report.getAlerts());
        } else {
            log.debug("Service {} monitoring status: {} - Duration: {}ms", 
                serviceName, report.getStatus(), report.getCheckDurationMs());
        }
    }

    /**
     * Get current monitoring status
     */
    public MonitoringHealthStatus getCurrentStatus() {
        return lastMonitoringStatus;
    }

    /**
     * Get monitoring summary
     */
    public String getMonitoringSummary() {
        return String.format("Service: %s, Status: %s, Last Check: %s", 
            serviceName, lastMonitoringStatus, lastHealthCheck);
    }

    /**
     * Check if service is healthy from monitoring perspective
     */
    public boolean isHealthy() {
        return lastMonitoringStatus == MonitoringHealthStatus.HEALTHY;
    }

    /**
     * Monitoring health status enumeration
     */
    public enum MonitoringHealthStatus {
        HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN
    }

    /**
     * Monitoring health report
     */
    public static class MonitoringHealthReport {
        private final String serviceName;
        private final MonitoringHealthStatus status;
        private final Map<String, Object> details;
        private final List<String> alerts;
        private final Instant checkTimestamp;
        private final long checkDurationMs;

        private MonitoringHealthReport(Builder builder) {
            this.serviceName = builder.serviceName;
            this.status = builder.status;
            this.details = builder.details != null ? builder.details : new HashMap<>();
            this.alerts = builder.alerts != null ? builder.alerts : new ArrayList<>();
            this.checkTimestamp = builder.checkTimestamp;
            this.checkDurationMs = builder.checkDurationMs;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getServiceName() { return serviceName; }
        public MonitoringHealthStatus getStatus() { return status; }
        public Map<String, Object> getDetails() { return details; }
        public List<String> getAlerts() { return alerts; }
        public Instant getCheckTimestamp() { return checkTimestamp; }
        public long getCheckDurationMs() { return checkDurationMs; }

        public static class Builder {
            private String serviceName;
            private MonitoringHealthStatus status;
            private Map<String, Object> details;
            private List<String> alerts;
            private Instant checkTimestamp;
            private long checkDurationMs;

            public Builder serviceName(String serviceName) { this.serviceName = serviceName; return this; }
            public Builder status(MonitoringHealthStatus status) { this.status = status; return this; }
            public Builder details(Map<String, Object> details) { this.details = details; return this; }
            public Builder alerts(List<String> alerts) { this.alerts = alerts; return this; }
            public Builder checkTimestamp(Instant checkTimestamp) { this.checkTimestamp = checkTimestamp; return this; }
            public Builder checkDurationMs(long checkDurationMs) { this.checkDurationMs = checkDurationMs; return this; }
            public MonitoringHealthReport build() { return new MonitoringHealthReport(this); }
        }
    }
}