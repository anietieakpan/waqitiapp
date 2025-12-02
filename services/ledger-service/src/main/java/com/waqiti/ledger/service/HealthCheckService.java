package com.waqiti.ledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.Status;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Health Check Service
 * 
 * Provides comprehensive health checking capabilities for the ledger service,
 * including database, cache, and system resource monitoring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealthCheckService {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    private final LocalDateTime startupTime = LocalDateTime.now();

    /**
     * Perform detailed health check of all service components
     */
    public Health performDetailedHealthCheck() {
        Map<String, Object> details = new HashMap<>();
        boolean allHealthy = true;

        // Database health
        Map<String, Object> dbHealth = checkDatabaseHealth();
        details.put("database", dbHealth);
        if (!(Boolean) dbHealth.get("healthy")) {
            allHealthy = false;
        }

        // Cache health
        Map<String, Object> cacheHealth = checkCacheHealth();
        details.put("cache", cacheHealth);
        if (!(Boolean) cacheHealth.get("healthy")) {
            allHealthy = false;
        }

        // Memory usage
        Map<String, Object> memoryInfo = getMemoryUsage();
        details.put("memory", memoryInfo);
        
        // Disk usage
        details.put("disk", getDiskUsage());
        
        // System info
        details.put("system", getSystemInfo());
        
        // Timestamp
        details.put("timestamp", LocalDateTime.now());

        Status status = allHealthy ? Status.UP : Status.DOWN;
        return Health.status(status).withDetails(details).build();
    }

    /**
     * Check database connectivity and performance
     */
    public Map<String, Object> checkDatabaseHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Test basic connectivity
            try (Connection connection = dataSource.getConnection()) {
                boolean isValid = connection.isValid(5); // 5 second timeout
                
                if (isValid) {
                    // Test query execution
                    Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                    long responseTime = System.currentTimeMillis() - startTime;
                    
                    health.put("healthy", result != null && result == 1);
                    health.put("responseTime", responseTime + "ms");
                    health.put("connectionValid", true);
                    
                    // Additional database metrics
                    health.put("activeConnections", getActiveConnections());
                    health.put("maxConnections", getMaxConnections());
                    
                } else {
                    health.put("healthy", false);
                    health.put("error", "Database connection is not valid");
                }
            }
            
        } catch (Exception e) {
            log.error("Database health check failed", e);
            health.put("healthy", false);
            health.put("error", e.getMessage());
        }
        
        health.put("timestamp", LocalDateTime.now());
        return health;
    }

    /**
     * Check Redis cache connectivity and performance
     */
    public Map<String, Object> checkCacheHealth() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Test Redis connectivity with ping
            String pong = redisTemplate.getConnectionFactory()
                .getConnection()
                .ping();
            
            if ("PONG".equals(pong)) {
                // Test basic operations
                String testKey = "health-check-" + System.currentTimeMillis();
                String testValue = "test-value";
                
                redisTemplate.opsForValue().set(testKey, testValue, 30, TimeUnit.SECONDS);
                Object retrievedValue = redisTemplate.opsForValue().get(testKey);
                redisTemplate.delete(testKey);
                
                long responseTime = System.currentTimeMillis() - startTime;
                
                health.put("healthy", testValue.equals(retrievedValue));
                health.put("responseTime", responseTime + "ms");
                health.put("pingResponse", pong);
                
            } else {
                health.put("healthy", false);
                health.put("error", "Redis ping failed");
            }
            
        } catch (Exception e) {
            log.error("Cache health check failed", e);
            health.put("healthy", false);
            health.put("error", e.getMessage());
        }
        
        health.put("timestamp", LocalDateTime.now());
        return health;
    }

    /**
     * Check if service is ready to accept requests
     */
    public boolean isServiceReady() {
        try {
            // Check if all critical dependencies are available
            Map<String, Object> dbHealth = checkDatabaseHealth();
            boolean dbReady = (Boolean) dbHealth.getOrDefault("healthy", false);
            
            // Cache is not critical for readiness, but we check anyway
            Map<String, Object> cacheHealth = checkCacheHealth();
            boolean cacheReady = (Boolean) cacheHealth.getOrDefault("healthy", false);
            
            // Service is ready if database is healthy (cache failure is non-critical)
            return dbReady;
            
        } catch (Exception e) {
            log.error("Readiness check failed", e);
            return false;
        }
    }

    /**
     * Get readiness check details
     */
    public Map<String, Object> getReadinessChecks() {
        Map<String, Object> checks = new HashMap<>();
        
        Map<String, Object> dbHealth = checkDatabaseHealth();
        checks.put("database", dbHealth.get("healthy"));
        
        Map<String, Object> cacheHealth = checkCacheHealth();
        checks.put("cache", cacheHealth.get("healthy"));
        
        return checks;
    }

    /**
     * Check if service is alive (liveness probe)
     */
    public boolean isServiceAlive() {
        try {
            // Basic checks for service liveness
            Runtime runtime = Runtime.getRuntime();
            
            // Check if we have enough free memory
            long freeMemory = runtime.freeMemory();
            long totalMemory = runtime.totalMemory();
            double memoryUsagePercent = ((double) (totalMemory - freeMemory) / totalMemory) * 100;
            
            // Service is considered alive if memory usage is below 90%
            return memoryUsagePercent < 90.0;
            
        } catch (Exception e) {
            log.error("Liveness check failed", e);
            return false;
        }
    }

    /**
     * Get service uptime
     */
    public String getUptime() {
        long uptime = ChronoUnit.SECONDS.between(startupTime, LocalDateTime.now());
        
        long hours = uptime / 3600;
        long minutes = (uptime % 3600) / 60;
        long seconds = uptime % 60;
        
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /**
     * Get memory usage information
     */
    public Map<String, Object> getMemoryUsage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemory = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemory = memoryBean.getNonHeapMemoryUsage();
        
        Map<String, Object> memory = new HashMap<>();
        
        // Heap memory
        Map<String, Object> heap = new HashMap<>();
        heap.put("used", formatBytes(heapMemory.getUsed()));
        heap.put("max", formatBytes(heapMemory.getMax()));
        heap.put("committed", formatBytes(heapMemory.getCommitted()));
        heap.put("usagePercent", String.format("%.2f%%", 
            (double) heapMemory.getUsed() / heapMemory.getMax() * 100));
        memory.put("heap", heap);
        
        // Non-heap memory
        Map<String, Object> nonHeap = new HashMap<>();
        nonHeap.put("used", formatBytes(nonHeapMemory.getUsed()));
        nonHeap.put("max", nonHeapMemory.getMax() > 0 ? formatBytes(nonHeapMemory.getMax()) : "unlimited");
        nonHeap.put("committed", formatBytes(nonHeapMemory.getCommitted()));
        memory.put("nonHeap", nonHeap);
        
        return memory;
    }

    /**
     * Get application metrics
     */
    public Map<String, Object> getApplicationMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // System metrics
        Runtime runtime = Runtime.getRuntime();
        metrics.put("availableProcessors", runtime.availableProcessors());
        metrics.put("uptime", getUptime());
        metrics.put("startTime", startupTime);
        
        // Memory metrics
        metrics.put("memory", getMemoryUsage());
        
        // Thread metrics
        metrics.put("activeThreads", Thread.activeCount());
        
        // GC metrics
        metrics.put("gcCollections", getGCMetrics());
        
        return metrics;
    }

    /**
     * Check health of all external dependencies
     */
    public Map<String, Object> checkDependenciesHealth() {
        Map<String, Object> dependencies = new HashMap<>();
        
        // Database
        dependencies.put("postgresql", checkDatabaseHealth());
        
        // Cache
        dependencies.put("redis", checkCacheHealth());
        
        // Add more dependencies as needed (e.g., external APIs)
        
        return dependencies;
    }

    private Integer getActiveConnections() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_stat_activity WHERE state = 'active'", 
                Integer.class);
        } catch (Exception e) {
            log.warn("Could not retrieve active connections", e);
            return null;
        }
    }

    private Integer getMaxConnections() {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT setting::int FROM pg_settings WHERE name = 'max_connections'", 
                Integer.class);
        } catch (Exception e) {
            log.warn("Could not retrieve max connections", e);
            return null;
        }
    }

    private Map<String, Object> getDiskUsage() {
        Map<String, Object> disk = new HashMap<>();
        
        try {
            java.io.File root = new java.io.File("/");
            disk.put("total", formatBytes(root.getTotalSpace()));
            disk.put("free", formatBytes(root.getFreeSpace()));
            disk.put("used", formatBytes(root.getTotalSpace() - root.getFreeSpace()));
            disk.put("usagePercent", String.format("%.2f%%", 
                (double) (root.getTotalSpace() - root.getFreeSpace()) / root.getTotalSpace() * 100));
        } catch (Exception e) {
            log.warn("Could not retrieve disk usage", e);
            disk.put("error", "Unable to retrieve disk usage");
        }
        
        return disk;
    }

    private Map<String, Object> getSystemInfo() {
        Map<String, Object> system = new HashMap<>();
        
        system.put("javaVersion", System.getProperty("java.version"));
        system.put("javaVendor", System.getProperty("java.vendor"));
        system.put("osName", System.getProperty("os.name"));
        system.put("osVersion", System.getProperty("os.version"));
        system.put("osArchitecture", System.getProperty("os.arch"));
        
        return system;
    }

    private Map<String, Object> getGCMetrics() {
        Map<String, Object> gc = new HashMap<>();
        
        try {
            ManagementFactory.getGarbageCollectorMXBeans().forEach(gcBean -> {
                Map<String, Object> gcInfo = new HashMap<>();
                gcInfo.put("collections", gcBean.getCollectionCount());
                gcInfo.put("time", gcBean.getCollectionTime() + "ms");
                gc.put(gcBean.getName(), gcInfo);
            });
        } catch (Exception e) {
            log.warn("Could not retrieve GC metrics", e);
        }
        
        return gc;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
}