package com.waqiti.common.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive health monitoring service for all critical system components
 * Note: Actuator integration will be added once dependency issues are resolved
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HealthIndicatorService {
    
    @PersistenceContext
    private final EntityManager entityManager;
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String HEALTH_CHECK_KEY = "health:check:timestamp";
    private static final long HEALTH_CHECK_TIMEOUT_MS = 5000;
    
    public HealthStatus health() {
        Map<String, Object> details = new HashMap<>();
        boolean overallHealthy = true;
        
        // Check database health
        HealthStatus dbHealth = checkDatabaseHealth();
        details.put("database", dbHealth.getDetails());
        if (!dbHealth.isHealthy()) {
            overallHealthy = false;
        }
        
        // Check Redis health
        HealthStatus redisHealth = checkRedisHealth();
        details.put("redis", redisHealth.getDetails());
        if (!redisHealth.isHealthy()) {
            overallHealthy = false;
        }
        
        // Check Kafka health
        HealthStatus kafkaHealth = checkKafkaHealth();
        details.put("kafka", kafkaHealth.getDetails());
        if (!kafkaHealth.isHealthy()) {
            overallHealthy = false;
        }
        
        // Check application-specific health
        HealthStatus appHealth = checkApplicationHealth();
        details.put("application", appHealth.getDetails());
        if (!appHealth.isHealthy()) {
            overallHealthy = false;
        }
        
        // Add system information
        details.put("system", getSystemInfo());
        details.put("timestamp", LocalDateTime.now());
        
        String status = overallHealthy ? "UP" : "DOWN";
        
        return new HealthStatus(overallHealthy, details);
    }
    
    private HealthStatus checkDatabaseHealth() {
        try {
            long startTime = System.currentTimeMillis();
            
            // Execute a simple query
            Object result = entityManager.createNativeQuery("SELECT 1").getSingleResult();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> details = Map.of(
                    "status", "UP",
                    "responseTime", responseTime + "ms",
                    "query", "SELECT 1",
                    "result", result.toString()
            );
            
            boolean healthy = responseTime < HEALTH_CHECK_TIMEOUT_MS;
            return new HealthStatus(healthy, details);
            
        } catch (Exception e) {
            log.error("Database health check failed", e);
            Map<String, Object> details = Map.of(
                    "status", "DOWN",
                    "error", e.getMessage(),
                    "exception", e.getClass().getSimpleName()
            );
            return new HealthStatus(false, details);
        }
    }
    
    private HealthStatus checkRedisHealth() {
        try {
            long startTime = System.currentTimeMillis();
            
            // Test Redis connectivity
            String testKey = HEALTH_CHECK_KEY;
            String testValue = String.valueOf(System.currentTimeMillis());
            
            redisTemplate.opsForValue().set(testKey, testValue, Duration.ofSeconds(60));
            String retrievedValue = (String) redisTemplate.opsForValue().get(testKey);
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            boolean healthy = testValue.equals(retrievedValue) && responseTime < HEALTH_CHECK_TIMEOUT_MS;
            
            Map<String, Object> details = Map.of(
                    "status", healthy ? "UP" : "DOWN",
                    "responseTime", responseTime + "ms",
                    "operation", "SET/GET",
                    "keyExpiry", "60s"
            );
            
            return new HealthStatus(healthy, details);
            
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            Map<String, Object> details = Map.of(
                    "status", "DOWN",
                    "error", e.getMessage(),
                    "exception", e.getClass().getSimpleName()
            );
            return new HealthStatus(false, details);
        }
    }
    
    private HealthStatus checkKafkaHealth() {
        try {
            long startTime = System.currentTimeMillis();
            
            // Test Kafka producer health by sending to a health check topic
            String healthTopic = "health-check";
            String message = "health-check-" + System.currentTimeMillis();
            
            kafkaTemplate.send(healthTopic, message);
            kafkaTemplate.flush();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            boolean healthy = responseTime < HEALTH_CHECK_TIMEOUT_MS;
            
            Map<String, Object> details = Map.of(
                    "status", healthy ? "UP" : "DOWN",
                    "responseTime", responseTime + "ms",
                    "operation", "SEND",
                    "topic", healthTopic
            );
            
            return new HealthStatus(healthy, details);
            
        } catch (Exception e) {
            log.error("Kafka health check failed", e);
            Map<String, Object> details = Map.of(
                    "status", "DOWN",
                    "error", e.getMessage(),
                    "exception", e.getClass().getSimpleName()
            );
            return new HealthStatus(false, details);
        }
    }
    
    private HealthStatus checkApplicationHealth() {
        try {
            Map<String, Object> details = new HashMap<>();
            
            // Check JVM memory
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
            
            details.put("memory", Map.of(
                    "used", formatBytes(usedMemory),
                    "free", formatBytes(freeMemory),
                    "total", formatBytes(totalMemory),
                    "max", formatBytes(maxMemory),
                    "usagePercent", String.format("%.2f%%", memoryUsagePercent)
            ));
            
            // Check thread count
            ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
            int threadCount = rootGroup.activeCount();
            details.put("threads", Map.of(
                    "active", threadCount,
                    "max", "unlimited"
            ));
            
            // Check system load (if available)
            try {
                java.lang.management.OperatingSystemMXBean osBean = 
                        java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                double systemLoad = osBean.getSystemLoadAverage();
                if (systemLoad >= 0) {
                    details.put("systemLoad", String.format("%.2f", systemLoad));
                }
            } catch (Exception e) {
                details.put("systemLoad", "unavailable");
            }
            
            // Determine if application is healthy based on thresholds
            boolean healthy = memoryUsagePercent < 90.0 && threadCount < 1000;
            
            details.put("status", healthy ? "UP" : "DOWN");
            details.put("uptime", getUptime());
            
            return new HealthStatus(healthy, details);
            
        } catch (Exception e) {
            log.error("Application health check failed", e);
            Map<String, Object> details = Map.of(
                    "status", "DOWN",
                    "error", e.getMessage()
            );
            return new HealthStatus(false, details);
        }
    }
    
    private Map<String, Object> getSystemInfo() {
        Map<String, Object> systemInfo = new HashMap<>();
        
        // Java information
        systemInfo.put("java", Map.of(
                "version", System.getProperty("java.version"),
                "vendor", System.getProperty("java.vendor"),
                "runtime", System.getProperty("java.runtime.name")
        ));
        
        // Operating system information
        systemInfo.put("os", Map.of(
                "name", System.getProperty("os.name"),
                "version", System.getProperty("os.version"),
                "arch", System.getProperty("os.arch")
        ));
        
        // Application information
        systemInfo.put("application", Map.of(
                "name", "Waqiti Fintech Platform",
                "version", getApplicationVersion(),
                "profile", getActiveProfile()
        ));
        
        return systemInfo;
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    private String getUptime() {
        long uptimeMs = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        Duration uptime = Duration.ofMillis(uptimeMs);
        
        long days = uptime.toDays();
        long hours = uptime.toHours() % 24;
        long minutes = uptime.toMinutes() % 60;
        long seconds = uptime.getSeconds() % 60;
        
        return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
    }
    
    private String getApplicationVersion() {
        Package pkg = this.getClass().getPackage();
        return pkg != null && pkg.getImplementationVersion() != null 
                ? pkg.getImplementationVersion() 
                : "unknown";
    }
    
    private String getActiveProfile() {
        String profiles = System.getProperty("spring.profiles.active");
        return profiles != null ? profiles : "default";
    }
    
    /**
     * Health status wrapper
     */
    private static class HealthStatus {
        private final boolean healthy;
        private final Map<String, Object> details;
        
        public HealthStatus(boolean healthy, Map<String, Object> details) {
            this.healthy = healthy;
            this.details = details;
        }
        
        public boolean isHealthy() {
            return healthy;
        }
        
        public Map<String, Object> getDetails() {
            return details;
        }
    }
}