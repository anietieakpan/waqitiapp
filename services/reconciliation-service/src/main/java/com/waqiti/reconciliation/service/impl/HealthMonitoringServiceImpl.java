package com.waqiti.reconciliation.service.impl;

import com.waqiti.reconciliation.domain.SystemHealth;
import com.waqiti.reconciliation.domain.Alert;
import com.waqiti.reconciliation.service.HealthMonitoringService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
@RequiredArgsConstructor
public class HealthMonitoringServiceImpl implements HealthMonitoringService, HealthIndicator {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private Counter healthCheckCounter;
    private Timer healthCheckTimer;
    private Counter alertGenerationCounter;
    
    // Active alerts storage
    private final Map<String, Alert> activeAlerts = new ConcurrentHashMap<>();
    private final AtomicLong alertIdGenerator = new AtomicLong(1);
    
    // Cache configuration
    private static final String HEALTH_CACHE_PREFIX = "reconciliation:health:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(2);
    
    @PostConstruct
    public void initialize() {
        healthCheckCounter = Counter.builder("reconciliation.health.checks")
            .description("Number of health checks performed")
            .register(meterRegistry);
            
        healthCheckTimer = Timer.builder("reconciliation.health.check.time")
            .description("Time taken to perform health checks")
            .register(meterRegistry);
            
        alertGenerationCounter = Counter.builder("reconciliation.health.alerts.generated")
            .description("Number of health alerts generated")
            .register(meterRegistry);
            
        log.info("HealthMonitoringServiceImpl initialized");
    }

    @Override
    @CircuitBreaker(name = "health-monitoring", fallbackMethod = "getCurrentHealthFallback")
    public SystemHealth getCurrentHealth() {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.debug("Performing system health check");
        
        try {
            healthCheckCounter.increment();
            
            // Check cache first
            String cacheKey = HEALTH_CACHE_PREFIX + "current";
            SystemHealth cached = (SystemHealth) redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                sample.stop(healthCheckTimer);
                return cached;
            }
            
            // Perform comprehensive health checks
            List<SystemHealth.HealthCheck> healthChecks = performHealthChecks();
            Map<String, String> systemMetrics = collectSystemMetrics();
            
            // Calculate overall health status and score
            SystemHealth.HealthStatus overallStatus = determineOverallStatus(healthChecks);
            BigDecimal healthScore = calculateHealthScore(healthChecks, systemMetrics);
            String statusMessage = generateStatusMessage(overallStatus, healthChecks);
            
            SystemHealth health = SystemHealth.builder()
                .overallStatus(overallStatus)
                .healthScore(healthScore)
                .healthChecks(healthChecks)
                .systemMetrics(systemMetrics)
                .lastUpdated(LocalDateTime.now())
                .statusMessage(statusMessage)
                .build();
            
            // Cache the health status
            redisTemplate.opsForValue().set(cacheKey, health, CACHE_TTL);
            
            sample.stop(healthCheckTimer);
            log.debug("System health check completed with status: {}", overallStatus);
            
            return health;
            
        } catch (Exception e) {
            sample.stop(healthCheckTimer);
            log.error("Failed to get current health status", e);
            throw new HealthMonitoringException("Failed to get system health", e);
        }
    }

    @Override
    public List<Alert> getActiveAlerts() {
        log.debug("Getting active alerts, current count: {}", activeAlerts.size());
        
        // Filter out expired or resolved alerts
        long currentTime = System.currentTimeMillis();
        List<Alert> validAlerts = new ArrayList<>();
        
        activeAlerts.entrySet().removeIf(entry -> {
            Alert alert = entry.getValue();
            // Remove if resolved or older than 24 hours
            boolean shouldRemove = alert.getStatus() == Alert.AlertStatus.RESOLVED ||
                (alert.getCreatedAt() != null && 
                 alert.getCreatedAt().isBefore(LocalDateTime.now().minusDays(1)));
            return shouldRemove;
        });
        
        // Return active alerts sorted by severity and creation time
        return activeAlerts.values().stream()
            .filter(Alert::isActive)
            .sorted((a, b) -> {
                int severityCompare = b.getSeverity().compareTo(a.getSeverity());
                if (severityCompare != 0) {
                    return severityCompare;
                }
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            })
            .toList();
    }

    @Override
    public SystemHealth.HealthCheck checkServiceHealth(String serviceName) {
        log.debug("Checking health for service: {}", serviceName);
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Simulate service health check based on service name
            SystemHealth.HealthStatus status = performServiceHealthCheck(serviceName);
            String message = generateServiceHealthMessage(serviceName, status);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            return SystemHealth.HealthCheck.builder()
                .serviceName(serviceName)
                .status(status)
                .message(message)
                .responseTimeMs(responseTime)
                .checkedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to check health for service: {}", serviceName, e);
            return SystemHealth.HealthCheck.builder()
                .serviceName(serviceName)
                .status(SystemHealth.HealthStatus.UNHEALTHY)
                .message("Health check failed: " + e.getMessage())
                .responseTimeMs(0L)
                .checkedAt(LocalDateTime.now())
                .build();
        }
    }

    @Override
    public List<Alert> generateHealthAlerts(SystemHealth health) {
        log.debug("Generating health alerts based on system health");
        
        List<Alert> newAlerts = new ArrayList<>();
        
        try {
            // Generate alerts for critical health issues
            if (health.isCritical()) {
                Alert criticalAlert = createHealthAlert(
                    Alert.AlertType.SYSTEM_ERROR,
                    Alert.AlertSeverity.CRITICAL,
                    "System Health Critical",
                    "System health is in critical state: " + health.getStatusMessage(),
                    health.getHealthScore()
                );
                newAlerts.add(criticalAlert);
            }
            
            // Generate alerts for degraded performance
            if (health.getOverallStatus() == SystemHealth.HealthStatus.DEGRADED) {
                Alert degradedAlert = createHealthAlert(
                    Alert.AlertType.PERFORMANCE_DEGRADATION,
                    Alert.AlertSeverity.HIGH,
                    "System Performance Degraded",
                    "System performance is degraded: " + health.getStatusMessage(),
                    health.getHealthScore()
                );
                newAlerts.add(degradedAlert);
            }
            
            // Generate alerts for unhealthy services
            if (health.getHealthChecks() != null) {
                for (SystemHealth.HealthCheck healthCheck : health.getHealthChecks()) {
                    if (healthCheck.getStatus() == SystemHealth.HealthStatus.UNHEALTHY) {
                        Alert serviceAlert = createHealthAlert(
                            Alert.AlertType.SYSTEM_ERROR,
                            Alert.AlertSeverity.HIGH,
                            "Service Unhealthy",
                            "Service " + healthCheck.getServiceName() + " is unhealthy: " + 
                                healthCheck.getMessage(),
                            BigDecimal.ZERO
                        );
                        newAlerts.add(serviceAlert);
                    }
                }
            }
            
            // Add new alerts to active alerts
            for (Alert alert : newAlerts) {
                String alertKey = alert.getType() + "_" + alert.getTitle().replaceAll("\\s", "_");
                if (!activeAlerts.containsKey(alertKey)) {
                    activeAlerts.put(alertKey, alert);
                    alertGenerationCounter.increment();
                    log.info("Generated health alert: {}", alert.getTitle());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to generate health alerts", e);
        }
        
        return newAlerts;
    }

    @Override
    public Health health() {
        try {
            SystemHealth systemHealth = getCurrentHealth();
            
            return switch (systemHealth.getOverallStatus()) {
                case HEALTHY -> Health.up()
                    .withDetail("status", "healthy")
                    .withDetail("score", systemHealth.getHealthScore())
                    .build();
                case DEGRADED -> Health.up()
                    .withDetail("status", "degraded")
                    .withDetail("score", systemHealth.getHealthScore())
                    .withDetail("message", systemHealth.getStatusMessage())
                    .build();
                case UNHEALTHY -> Health.down()
                    .withDetail("status", "unhealthy")
                    .withDetail("score", systemHealth.getHealthScore())
                    .withDetail("message", systemHealth.getStatusMessage())
                    .build();
                default -> Health.unknown()
                    .withDetail("status", "unknown")
                    .withDetail("message", "Unable to determine health status")
                    .build();
            };
        } catch (Exception e) {
            return Health.down()
                .withDetail("status", "error")
                .withDetail("message", "Health check failed: " + e.getMessage())
                .build();
        }
    }

    private List<SystemHealth.HealthCheck> performHealthChecks() {
        List<SystemHealth.HealthCheck> healthChecks = new ArrayList<>();
        
        // Define services to check
        String[] services = {
            "database", "redis", "message-queue", "external-api", 
            "payment-service", "account-service", "crypto-service"
        };
        
        for (String service : services) {
            SystemHealth.HealthCheck healthCheck = checkServiceHealth(service);
            healthChecks.add(healthCheck);
        }
        
        return healthChecks;
    }

    private Map<String, String> collectSystemMetrics() {
        Map<String, String> metrics = new HashMap<>();
        
        // Collect JVM metrics
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        metrics.put("jvm.memory.used", String.valueOf(usedMemory / (1024 * 1024)) + "MB");
        metrics.put("jvm.memory.total", String.valueOf(totalMemory / (1024 * 1024)) + "MB");
        metrics.put("jvm.memory.usage", 
            String.format("%.2f%%", (double) usedMemory / totalMemory * 100));
        
        // System uptime
        long uptime = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        metrics.put("system.uptime", String.valueOf(uptime / 1000) + "s");
        
        // Thread count
        metrics.put("jvm.threads", String.valueOf(Thread.activeCount()));
        
        // Current timestamp
        metrics.put("health.check.timestamp", LocalDateTime.now().toString());
        
        return metrics;
    }

    private SystemHealth.HealthStatus determineOverallStatus(List<SystemHealth.HealthCheck> healthChecks) {
        if (healthChecks.isEmpty()) {
            return SystemHealth.HealthStatus.UNKNOWN;
        }
        
        long unhealthyCount = healthChecks.stream()
            .mapToLong(check -> check.getStatus() == SystemHealth.HealthStatus.UNHEALTHY ? 1 : 0)
            .sum();
            
        long degradedCount = healthChecks.stream()
            .mapToLong(check -> check.getStatus() == SystemHealth.HealthStatus.DEGRADED ? 1 : 0)
            .sum();
        
        // If more than 25% are unhealthy, system is unhealthy
        if ((double) unhealthyCount / healthChecks.size() > 0.25) {
            return SystemHealth.HealthStatus.UNHEALTHY;
        }
        
        // If any are unhealthy or more than 50% are degraded, system is degraded
        if (unhealthyCount > 0 || (double) degradedCount / healthChecks.size() > 0.5) {
            return SystemHealth.HealthStatus.DEGRADED;
        }
        
        return SystemHealth.HealthStatus.HEALTHY;
    }

    private BigDecimal calculateHealthScore(List<SystemHealth.HealthCheck> healthChecks, 
                                         Map<String, String> systemMetrics) {
        if (healthChecks.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        // Calculate base score from health checks
        double totalScore = 0.0;
        for (SystemHealth.HealthCheck check : healthChecks) {
            totalScore += switch (check.getStatus()) {
                case HEALTHY -> 100.0;
                case DEGRADED -> 60.0;
                case UNHEALTHY -> 0.0;
                case UNKNOWN -> 25.0;
            };
        }
        
        double averageScore = totalScore / healthChecks.size();
        
        // Adjust score based on system metrics
        String memoryUsage = systemMetrics.get("jvm.memory.usage");
        if (memoryUsage != null) {
            double usage = Double.parseDouble(memoryUsage.replace("%", ""));
            if (usage > 90) {
                averageScore *= 0.8; // Reduce score for high memory usage
            } else if (usage > 80) {
                averageScore *= 0.9;
            }
        }
        
        return BigDecimal.valueOf(averageScore).setScale(2, RoundingMode.HALF_UP);
    }

    private String generateStatusMessage(SystemHealth.HealthStatus status, 
                                       List<SystemHealth.HealthCheck> healthChecks) {
        return switch (status) {
            case HEALTHY -> "All systems operational";
            case DEGRADED -> {
                long degradedCount = healthChecks.stream()
                    .mapToLong(check -> check.getStatus() == SystemHealth.HealthStatus.DEGRADED ? 1 : 0)
                    .sum();
                yield String.format("System performance degraded (%d services affected)", degradedCount);
            }
            case UNHEALTHY -> {
                long unhealthyCount = healthChecks.stream()
                    .mapToLong(check -> check.getStatus() == SystemHealth.HealthStatus.UNHEALTHY ? 1 : 0)
                    .sum();
                yield String.format("System unhealthy (%d services down)", unhealthyCount);
            }
            case UNKNOWN -> "Unable to determine system health";
        };
    }

    private SystemHealth.HealthStatus performServiceHealthCheck(String serviceName) {
        // Simulate different health states based on service name
        Random random = new Random(serviceName.hashCode());
        double healthProbability = random.nextDouble();
        
        if (healthProbability > 0.9) {
            return SystemHealth.HealthStatus.UNHEALTHY;
        } else if (healthProbability > 0.8) {
            return SystemHealth.HealthStatus.DEGRADED;
        } else {
            return SystemHealth.HealthStatus.HEALTHY;
        }
    }

    private String generateServiceHealthMessage(String serviceName, SystemHealth.HealthStatus status) {
        return switch (status) {
            case HEALTHY -> serviceName + " is responding normally";
            case DEGRADED -> serviceName + " is experiencing performance issues";
            case UNHEALTHY -> serviceName + " is not responding or has failed health checks";
            case UNKNOWN -> serviceName + " health status could not be determined";
        };
    }

    private Alert createHealthAlert(Alert.AlertType type, Alert.AlertSeverity severity, 
                                   String title, String message, BigDecimal currentValue) {
        return Alert.builder()
            .id(alertIdGenerator.getAndIncrement())
            .type(type)
            .severity(severity)
            .title(title)
            .message(message)
            .source("HealthMonitoringService")
            .currentValue(currentValue)
            .createdAt(LocalDateTime.now())
            .status(Alert.AlertStatus.ACTIVE)
            .build();
    }

    // Fallback methods
    
    public SystemHealth getCurrentHealthFallback(Exception ex) {
        log.warn("Health monitoring fallback triggered: {}", ex.getMessage());
        
        return SystemHealth.builder()
            .overallStatus(SystemHealth.HealthStatus.UNKNOWN)
            .healthScore(BigDecimal.ZERO)
            .healthChecks(Collections.emptyList())
            .systemMetrics(Collections.emptyMap())
            .lastUpdated(LocalDateTime.now())
            .statusMessage("Health monitoring service temporarily unavailable")
            .build();
    }

    /**
     * Exception for health monitoring failures
     */
    public static class HealthMonitoringException extends RuntimeException {
        public HealthMonitoringException(String message) {
            super(message);
        }
        
        public HealthMonitoringException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}