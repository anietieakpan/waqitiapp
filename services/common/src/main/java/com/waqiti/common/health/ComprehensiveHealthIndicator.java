package com.waqiti.common.health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.ArrayList;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

/**
 * Comprehensive health monitoring service for all Waqiti services
 * Provides enterprise-grade health monitoring without Spring Boot Actuator dependencies
 * Monitors database, system resources, and application components
 */
@Slf4j
@Component
public class ComprehensiveHealthIndicator {

    private final DataSource dataSource;

    // Health monitoring infrastructure
    private final ExecutorService healthCheckExecutor = Executors.newFixedThreadPool(10);
    private final Queue<HealthSnapshot> healthHistory = new ConcurrentLinkedQueue<>();
    private volatile HealthStatus lastHealthStatus = HealthStatus.UNKNOWN;

    @Value("${spring.application.name:waqiti-service}")
    private String serviceName;

    @Value("${health.checks.timeout.seconds:10}")
    private int healthCheckTimeoutSeconds;

    @Value("${health.checks.database.enabled:true}")
    private boolean databaseHealthEnabled;

    @Value("${health.checks.jvm.enabled:true}")
    private boolean jvmHealthEnabled;

    @Value("${health.checks.system.enabled:true}")
    private boolean systemHealthEnabled;

    @Value("${health.checks.memory.warning.threshold:80}")
    private int memoryWarningThreshold;

    @Value("${health.checks.memory.critical.threshold:90}")
    private int memoryCriticalThreshold;

    @Value("${health.history.max.size:100}")
    private int maxHealthHistorySize;

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

    public ComprehensiveHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() {
        log.info("Initializing Comprehensive Health Indicator for service: {}", serviceName);
        recordHealthSnapshot();
    }

    @PreDestroy
    public void cleanup() {
        healthCheckExecutor.shutdown();
        try {
            if (!healthCheckExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                healthCheckExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthCheckExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Perform comprehensive health check
     */
    public ComprehensiveHealthReport performHealthCheck() {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Starting comprehensive health check for service: {}", serviceName);
            
            Map<String, ComponentHealth> componentHealthMap = new HashMap<>();
            HealthStatus overallStatus = HealthStatus.HEALTHY;

            // Run all health checks concurrently
            List<CompletableFuture<Void>> healthChecks = new ArrayList<>();

            // Database health check
            if (databaseHealthEnabled && dataSource != null) {
                healthChecks.add(CompletableFuture.runAsync(() -> {
                    ComponentHealth dbHealth = checkDatabaseHealth();
                    componentHealthMap.put("database", dbHealth);
                    if (dbHealth.getStatus() == HealthStatus.UNHEALTHY) {
                        lastHealthStatus = HealthStatus.UNHEALTHY;
                    }
                }, healthCheckExecutor));
            }

            // JVM health check
            if (jvmHealthEnabled) {
                healthChecks.add(CompletableFuture.runAsync(() -> {
                    ComponentHealth jvmHealth = checkJvmHealth();
                    componentHealthMap.put("jvm", jvmHealth);
                    if (jvmHealth.getStatus() == HealthStatus.UNHEALTHY) {
                        lastHealthStatus = HealthStatus.UNHEALTHY;
                    }
                }, healthCheckExecutor));
            }

            // System health check
            if (systemHealthEnabled) {
                healthChecks.add(CompletableFuture.runAsync(() -> {
                    ComponentHealth systemHealth = checkSystemHealth();
                    componentHealthMap.put("system", systemHealth);
                    if (systemHealth.getStatus() == HealthStatus.UNHEALTHY) {
                        lastHealthStatus = HealthStatus.UNHEALTHY;
                    }
                }, healthCheckExecutor));
            }

            // Wait for all checks to complete with timeout
            CompletableFuture<Void> allChecks = CompletableFuture.allOf(
                healthChecks.toArray(new CompletableFuture[0])
            );

            allChecks.get(healthCheckTimeoutSeconds, TimeUnit.SECONDS);

            // Determine overall status
            for (ComponentHealth componentHealth : componentHealthMap.values()) {
                if (componentHealth.getStatus() == HealthStatus.UNHEALTHY) {
                    overallStatus = HealthStatus.UNHEALTHY;
                    break;
                } else if (componentHealth.getStatus() == HealthStatus.DEGRADED && 
                          overallStatus == HealthStatus.HEALTHY) {
                    overallStatus = HealthStatus.DEGRADED;
                }
            }

            long endTime = System.currentTimeMillis();
            
            ComprehensiveHealthReport report = ComprehensiveHealthReport.builder()
                .serviceName(serviceName)
                .overallStatus(overallStatus)
                .componentHealth(componentHealthMap)
                .checkTimestamp(Instant.now())
                .checkDurationMs(endTime - startTime)
                .uptime(runtimeBean.getUptime())
                .build();

            // Record this health check
            recordHealthSnapshot();
            lastHealthStatus = overallStatus;

            log.debug("Health check completed in {}ms with status: {}", 
                     endTime - startTime, overallStatus);

            return report;

        } catch (Exception e) {
            log.error("Health check failed for service: {}", serviceName, e);
            lastHealthStatus = HealthStatus.UNHEALTHY;
            
            return ComprehensiveHealthReport.builder()
                .serviceName(serviceName)
                .overallStatus(HealthStatus.UNHEALTHY)
                .componentHealth(Map.of("error", ComponentHealth.builder()
                    .status(HealthStatus.UNHEALTHY)
                    .message("Health check execution failed: " + e.getMessage())
                    .checkTimestamp(Instant.now())
                    .build()))
                .checkTimestamp(Instant.now())
                .checkDurationMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    /**
     * Check database connectivity and performance
     */
    private ComponentHealth checkDatabaseHealth() {
        if (dataSource == null) {
            return ComponentHealth.builder()
                .status(HealthStatus.UNKNOWN)
                .message("DataSource not configured")
                .checkTimestamp(Instant.now())
                .build();
        }

        long startTime = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection()) {
            // Test basic connectivity
            boolean isValid = connection.isValid(5);
            long responseTime = System.currentTimeMillis() - startTime;

            Map<String, Object> details = new HashMap<>();
            details.put("responseTimeMs", responseTime);
            details.put("databaseProductName", connection.getMetaData().getDatabaseProductName());
            details.put("databaseProductVersion", connection.getMetaData().getDatabaseProductVersion());

            HealthStatus status = HealthStatus.HEALTHY;
            String message = "Database connection healthy";

            if (!isValid) {
                status = HealthStatus.UNHEALTHY;
                message = "Database connection invalid";
            } else if (responseTime > 1000) {
                status = HealthStatus.DEGRADED;
                message = "Database response time degraded: " + responseTime + "ms";
            }

            return ComponentHealth.builder()
                .status(status)
                .message(message)
                .details(details)
                .checkTimestamp(Instant.now())
                .responseTimeMs(responseTime)
                .build();

        } catch (SQLException e) {
            log.warn("Database health check failed", e);
            return ComponentHealth.builder()
                .status(HealthStatus.UNHEALTHY)
                .message("Database connection failed: " + e.getMessage())
                .details(Map.of("error", e.getClass().getSimpleName()))
                .checkTimestamp(Instant.now())
                .build();
        }
    }

    /**
     * Check JVM health including memory usage
     */
    private ComponentHealth checkJvmHealth() {
        try {
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long heapMax = memoryBean.getHeapMemoryUsage().getMax();
            long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();
            
            double heapUsagePercent = (double) heapUsed / heapMax * 100;
            
            Map<String, Object> details = new HashMap<>();
            details.put("heapUsedMB", heapUsed / (1024 * 1024));
            details.put("heapMaxMB", heapMax / (1024 * 1024));
            details.put("heapUsagePercent", Math.round(heapUsagePercent * 100.0) / 100.0);
            details.put("nonHeapUsedMB", nonHeapUsed / (1024 * 1024));
            details.put("gcCollections", memoryBean.getObjectPendingFinalizationCount());
            details.put("uptime", runtimeBean.getUptime());

            HealthStatus status = HealthStatus.HEALTHY;
            String message = "JVM health normal";

            if (heapUsagePercent >= memoryCriticalThreshold) {
                status = HealthStatus.UNHEALTHY;
                message = String.format("Critical memory usage: %.1f%%", heapUsagePercent);
            } else if (heapUsagePercent >= memoryWarningThreshold) {
                status = HealthStatus.DEGRADED;
                message = String.format("High memory usage: %.1f%%", heapUsagePercent);
            }

            return ComponentHealth.builder()
                .status(status)
                .message(message)
                .details(details)
                .checkTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.warn("JVM health check failed", e);
            return ComponentHealth.builder()
                .status(HealthStatus.UNHEALTHY)
                .message("JVM health check failed: " + e.getMessage())
                .checkTimestamp(Instant.now())
                .build();
        }
    }

    /**
     * Check system-level health
     */
    private ComponentHealth checkSystemHealth() {
        try {
            Map<String, Object> details = new HashMap<>();
            details.put("availableProcessors", osBean.getAvailableProcessors());
            details.put("osName", osBean.getName());
            details.put("osVersion", osBean.getVersion());
            details.put("osArch", osBean.getArch());

            // Add load average if available
            if (osBean.getSystemLoadAverage() >= 0) {
                details.put("systemLoadAverage", osBean.getSystemLoadAverage());
            }

            HealthStatus status = HealthStatus.HEALTHY;
            String message = "System health normal";

            // Check if system load is high (if available)
            double loadAverage = osBean.getSystemLoadAverage();
            if (loadAverage >= 0) {
                double normalizedLoad = loadAverage / osBean.getAvailableProcessors();
                if (normalizedLoad > 2.0) {
                    status = HealthStatus.DEGRADED;
                    message = String.format("High system load: %.2f", loadAverage);
                }
                details.put("normalizedLoad", Math.round(normalizedLoad * 100.0) / 100.0);
            }

            return ComponentHealth.builder()
                .status(status)
                .message(message)
                .details(details)
                .checkTimestamp(Instant.now())
                .build();

        } catch (Exception e) {
            log.warn("System health check failed", e);
            return ComponentHealth.builder()
                .status(HealthStatus.UNHEALTHY)
                .message("System health check failed: " + e.getMessage())
                .checkTimestamp(Instant.now())
                .build();
        }
    }

    /**
     * Record a health snapshot for trend analysis
     */
    private void recordHealthSnapshot() {
        try {
            HealthSnapshot snapshot = HealthSnapshot.builder()
                .timestamp(Instant.now())
                .status(lastHealthStatus)
                .heapUsagePercent(getHeapUsagePercent())
                .uptime(runtimeBean.getUptime())
                .build();

            healthHistory.offer(snapshot);

            // Maintain history size limit
            while (healthHistory.size() > maxHealthHistorySize) {
                healthHistory.poll();
            }

        } catch (Exception e) {
            log.debug("Failed to record health snapshot", e);
        }
    }

    /**
     * Get current heap usage percentage
     */
    private double getHeapUsagePercent() {
        try {
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long heapMax = memoryBean.getHeapMemoryUsage().getMax();
            return (double) heapUsed / heapMax * 100;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Get current health status
     */
    public HealthStatus getCurrentHealthStatus() {
        return lastHealthStatus;
    }

    /**
     * Get health history for trend analysis
     */
    public List<HealthSnapshot> getHealthHistory() {
        return new ArrayList<>(healthHistory);
    }

    /**
     * Get quick health summary
     */
    public String getHealthSummary() {
        return String.format("Service: %s, Status: %s, Uptime: %d mins", 
            serviceName, lastHealthStatus, runtimeBean.getUptime() / 60000);
    }

    /**
     * Health status enumeration
     */
    public enum HealthStatus {
        HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN
    }

    /**
     * Component health information
     */
    public static class ComponentHealth {
        private final HealthStatus status;
        private final String message;
        private final Map<String, Object> details;
        private final Instant checkTimestamp;
        private final Long responseTimeMs;

        private ComponentHealth(Builder builder) {
            this.status = builder.status;
            this.message = builder.message;
            this.details = builder.details != null ? builder.details : new HashMap<>();
            this.checkTimestamp = builder.checkTimestamp;
            this.responseTimeMs = builder.responseTimeMs;
        }

        public static Builder builder() {
            return new Builder();
        }

        public HealthStatus getStatus() { return status; }
        public String getMessage() { return message; }
        public Map<String, Object> getDetails() { return details; }
        public Instant getCheckTimestamp() { return checkTimestamp; }
        public Long getResponseTimeMs() { return responseTimeMs; }

        public static class Builder {
            private HealthStatus status;
            private String message;
            private Map<String, Object> details;
            private Instant checkTimestamp;
            private Long responseTimeMs;

            public Builder status(HealthStatus status) { this.status = status; return this; }
            public Builder message(String message) { this.message = message; return this; }
            public Builder details(Map<String, Object> details) { this.details = details; return this; }
            public Builder checkTimestamp(Instant checkTimestamp) { this.checkTimestamp = checkTimestamp; return this; }
            public Builder responseTimeMs(Long responseTimeMs) { this.responseTimeMs = responseTimeMs; return this; }
            public ComponentHealth build() { return new ComponentHealth(this); }
        }
    }

    /**
     * Comprehensive health report
     */
    public static class ComprehensiveHealthReport {
        private final String serviceName;
        private final HealthStatus overallStatus;
        private final Map<String, ComponentHealth> componentHealth;
        private final Instant checkTimestamp;
        private final long checkDurationMs;
        private final long uptime;

        private ComprehensiveHealthReport(Builder builder) {
            this.serviceName = builder.serviceName;
            this.overallStatus = builder.overallStatus;
            this.componentHealth = builder.componentHealth != null ? builder.componentHealth : new HashMap<>();
            this.checkTimestamp = builder.checkTimestamp;
            this.checkDurationMs = builder.checkDurationMs;
            this.uptime = builder.uptime;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getServiceName() { return serviceName; }
        public HealthStatus getOverallStatus() { return overallStatus; }
        public Map<String, ComponentHealth> getComponentHealth() { return componentHealth; }
        public Instant getCheckTimestamp() { return checkTimestamp; }
        public long getCheckDurationMs() { return checkDurationMs; }
        public long getUptime() { return uptime; }

        public static class Builder {
            private String serviceName;
            private HealthStatus overallStatus;
            private Map<String, ComponentHealth> componentHealth;
            private Instant checkTimestamp;
            private long checkDurationMs;
            private long uptime;

            public Builder serviceName(String serviceName) { this.serviceName = serviceName; return this; }
            public Builder overallStatus(HealthStatus overallStatus) { this.overallStatus = overallStatus; return this; }
            public Builder componentHealth(Map<String, ComponentHealth> componentHealth) { this.componentHealth = componentHealth; return this; }
            public Builder checkTimestamp(Instant checkTimestamp) { this.checkTimestamp = checkTimestamp; return this; }
            public Builder checkDurationMs(long checkDurationMs) { this.checkDurationMs = checkDurationMs; return this; }
            public Builder uptime(long uptime) { this.uptime = uptime; return this; }
            public ComprehensiveHealthReport build() { return new ComprehensiveHealthReport(this); }
        }
    }

    /**
     * Health snapshot for historical tracking
     */
    public static class HealthSnapshot {
        private final Instant timestamp;
        private final HealthStatus status;
        private final double heapUsagePercent;
        private final long uptime;

        private HealthSnapshot(Builder builder) {
            this.timestamp = builder.timestamp;
            this.status = builder.status;
            this.heapUsagePercent = builder.heapUsagePercent;
            this.uptime = builder.uptime;
        }

        public static Builder builder() {
            return new Builder();
        }

        public Instant getTimestamp() { return timestamp; }
        public HealthStatus getStatus() { return status; }
        public double getHeapUsagePercent() { return heapUsagePercent; }
        public long getUptime() { return uptime; }

        public static class Builder {
            private Instant timestamp;
            private HealthStatus status;
            private double heapUsagePercent;
            private long uptime;

            public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
            public Builder status(HealthStatus status) { this.status = status; return this; }
            public Builder heapUsagePercent(double heapUsagePercent) { this.heapUsagePercent = heapUsagePercent; return this; }
            public Builder uptime(long uptime) { this.uptime = uptime; return this; }
            public HealthSnapshot build() { return new HealthSnapshot(this); }
        }
    }

    /**
     * Get overall health status
     * @return Health object with current system health
     */
    public org.springframework.boot.actuate.health.Health health() {
        try {
            ComprehensiveHealthReport report = performHealthCheck();

            if (report.getOverallStatus() == HealthStatus.HEALTHY) {
                return org.springframework.boot.actuate.health.Health.up()
                        .withDetail("serviceName", report.getServiceName())
                        .withDetail("checkDurationMs", report.getCheckDurationMs())
                        .withDetail("uptime", report.getUptime())
                        .withDetail("components", report.getComponentHealth())
                        .build();
            } else if (report.getOverallStatus() == HealthStatus.DEGRADED) {
                return org.springframework.boot.actuate.health.Health.status("DEGRADED")
                        .withDetail("serviceName", report.getServiceName())
                        .withDetail("checkDurationMs", report.getCheckDurationMs())
                        .withDetail("components", report.getComponentHealth())
                        .build();
            } else {
                return org.springframework.boot.actuate.health.Health.down()
                        .withDetail("serviceName", report.getServiceName())
                        .withDetail("status", report.getOverallStatus())
                        .withDetail("components", report.getComponentHealth())
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to get health status", e);
            return org.springframework.boot.actuate.health.Health.down()
                    .withException(e)
                    .build();
        }
    }
}