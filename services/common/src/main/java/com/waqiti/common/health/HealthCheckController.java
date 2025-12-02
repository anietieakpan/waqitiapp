package com.waqiti.common.health;

import com.waqiti.common.resilience.ResilientServiceExecutor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Comprehensive health check controller with multiple endpoints
 * Provides detailed health information for monitoring and debugging
 */
@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthCheckController {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ResilientServiceExecutor resilientExecutor;
    private final ComprehensiveHealthIndicator healthIndicator;

    @Value("${spring.application.name}")
    private String serviceName;

    /**
     * Public basic health check endpoint for load balancers (minimal info)
     */
    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> publicHealth() {
        // Simple health check without sensitive information
        try {
            // Just check if service is responsive
            Map<String, String> response = new HashMap<>();
            response.put("status", "UP");
            response.put("service", serviceName);
            response.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                "status", "DOWN",
                "timestamp", Instant.now().toString()
            ));
        }
    }
    
    /**
     * Detailed health check endpoint - requires authentication for sensitive info
     */
    @GetMapping
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN') or hasRole('HEALTH_CHECK')")
    public ResponseEntity<Map<String, Object>> health() {
        // Audit sensitive health check access
        auditHealthAccess("DETAILED_HEALTH_CHECK");
        
        try {
            Health health = healthIndicator.health();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", health.getStatus().getCode());
            response.put("details", health.getDetails());
            
            if (health.getStatus() == Status.UP) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(503).body(response);
            }
        } catch (Exception e) {
            log.error("Health check failed", e);
            return ResponseEntity.status(503).body(Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", Instant.now().toString()
            ));
        }
    }

    /**
     * Liveness probe endpoint (Kubernetes) - public for K8s health checks
     */
    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> liveness() {
        // Basic liveness check - just verify the service is running
        // Keep this public for Kubernetes liveness probes
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", serviceName,
            "timestamp", Instant.now().toString(),
            "uptime", String.valueOf(ManagementFactory.getRuntimeMXBean().getUptime())
        ));
    }

    /**
     * Readiness probe endpoint (Kubernetes) - public for K8s readiness checks
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        try {
            // Check critical dependencies for readiness - minimal info for K8s
            boolean databaseReady = checkDatabaseReadiness();
            boolean redisReady = checkRedisReadiness();
            
            Map<String, Object> response = new HashMap<>();
            response.put("service", serviceName);
            response.put("timestamp", Instant.now().toString());
            
            // Only expose basic status for K8s - no sensitive details
            if (databaseReady && redisReady) {
                response.put("status", "UP");
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "DOWN");
                return ResponseEntity.status(503).body(response);
            }
        } catch (Exception e) {
            log.error("Readiness check failed", e);
            return ResponseEntity.status(503).body(Map.of(
                "status", "DOWN",
                "timestamp", Instant.now().toString()
            ));
        }
    }
    
    /**
     * Detailed readiness check with dependency details - requires authentication
     */
    @GetMapping("/ready/detailed")
    @PreAuthorize("hasRole('SYSTEM') or hasRole('ADMIN') or hasRole('HEALTH_CHECK')")
    public ResponseEntity<Map<String, Object>> detailedReadiness() {
        try {
            // Check critical dependencies for readiness
            boolean databaseReady = checkDatabaseReadiness();
            boolean redisReady = checkRedisReadiness();
            
            Map<String, Object> response = new HashMap<>();
            response.put("service", serviceName);
            response.put("timestamp", Instant.now().toString());
            response.put("database", Map.of("status", databaseReady ? "UP" : "DOWN"));
            response.put("redis", Map.of("status", redisReady ? "UP" : "DOWN"));
            
            if (databaseReady && redisReady) {
                response.put("status", "UP");
                return ResponseEntity.ok(response);
            } else {
                response.put("status", "DOWN");
                return ResponseEntity.status(503).body(response);
            }
        } catch (Exception e) {
            log.error("Detailed readiness check failed", e);
            return ResponseEntity.status(503).body(Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", Instant.now().toString()
            ));
        }
    }

    /**
     * Database-specific health check - requires admin privileges
     */
    @GetMapping("/database")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DBA') or hasRole('SYSTEM')")
    public ResponseEntity<Map<String, Object>> databaseHealth() {
        // Audit sensitive database health access
        auditHealthAccess("DATABASE_HEALTH_CHECK");
        
        try {
            long startTime = System.currentTimeMillis();
            
            try (Connection connection = dataSource.getConnection()) {
                boolean isValid = connection.isValid(5);
                long responseTime = System.currentTimeMillis() - startTime;
                
                Map<String, Object> response = new HashMap<>();
                response.put("status", isValid ? "UP" : "DOWN");
                response.put("response-time-ms", responseTime);
                response.put("connection-valid", isValid);
                response.put("database-product", connection.getMetaData().getDatabaseProductName());
                response.put("database-version", connection.getMetaData().getDatabaseProductVersion());
                response.put("timestamp", Instant.now().toString());
                
                if (isValid && responseTime < 1000) {
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.status(503).body(response);
                }
            }
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return ResponseEntity.status(503).body(Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", Instant.now().toString()
            ));
        }
    }

    /**
     * Redis-specific health check - requires admin privileges
     */
    @GetMapping("/redis")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DBA') or hasRole('SYSTEM')")
    public ResponseEntity<Map<String, Object>> redisHealth() {
        // Audit sensitive Redis health access
        auditHealthAccess("REDIS_HEALTH_CHECK");
        
        try {
            long startTime = System.currentTimeMillis();
            
            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                String pong = connection.ping();
                long responseTime = System.currentTimeMillis() - startTime;
                
                Map<String, Object> response = new HashMap<>();
                response.put("status", "PONG".equals(pong) ? "UP" : "DOWN");
                response.put("response-time-ms", responseTime);
                response.put("ping", pong);
                response.put("timestamp", Instant.now().toString());
                
                if ("PONG".equals(pong) && responseTime < 500) {
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.status(503).body(response);
                }
            }
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return ResponseEntity.status(503).body(Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", Instant.now().toString()
            ));
        }
    }

    /**
     * Circuit breaker status endpoint - requires admin privileges
     */
    @GetMapping("/circuit-breakers")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM') or hasRole('OPERATIONS')")
    public ResponseEntity<Map<String, Object>> circuitBreakersStatus() {
        // Audit sensitive circuit breaker access
        auditHealthAccess("CIRCUIT_BREAKER_STATUS");
        
        Map<String, Object> circuitBreakers = new HashMap<>();
        
        String[] services = {"payment-gateway", "kyc-service", "fraud-detection", 
                           "notification-service", "currency-exchange"};
        
        for (String serviceName : services) {
            try {
                CircuitBreaker.State state = resilientExecutor.getCircuitBreakerState(serviceName);
                CircuitBreaker.Metrics metrics = resilientExecutor.getCircuitBreakerMetrics(serviceName);
                
                Map<String, Object> cbInfo = new HashMap<>();
                cbInfo.put("state", state.toString());
                cbInfo.put("failure-rate", String.format("%.2f%%", metrics.getFailureRate()));
                cbInfo.put("success-rate", String.format("%.2f%%", 100 - metrics.getFailureRate()));
                cbInfo.put("buffered-calls", metrics.getNumberOfBufferedCalls());
                cbInfo.put("failed-calls", metrics.getNumberOfFailedCalls());
                cbInfo.put("successful-calls", metrics.getNumberOfSuccessfulCalls());
                cbInfo.put("not-permitted-calls", metrics.getNumberOfNotPermittedCalls());
                
                circuitBreakers.put(serviceName, cbInfo);
            } catch (Exception e) {
                circuitBreakers.put(serviceName, Map.of(
                    "status", "ERROR",
                    "error", e.getMessage()
                ));
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("circuit-breakers", circuitBreakers);
        response.put("timestamp", Instant.now().toString());
        
        return ResponseEntity.ok(response);
    }

    /**
     * System metrics endpoint - requires admin privileges (sensitive performance data)
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM') or hasRole('MONITORING')")
    public ResponseEntity<Map<String, Object>> systemMetrics() {
        // Audit sensitive metrics access
        auditHealthAccess("SYSTEM_METRICS_ACCESS");
        
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            Runtime runtime = Runtime.getRuntime();
            
            Map<String, Object> metrics = new HashMap<>();
            
            // Memory metrics
            Map<String, Object> memory = new HashMap<>();
            memory.put("heap-used-mb", memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));
            memory.put("heap-max-mb", memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024));
            memory.put("heap-committed-mb", memoryBean.getHeapMemoryUsage().getCommitted() / (1024 * 1024));
            memory.put("non-heap-used-mb", memoryBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024));
            
            // JVM metrics
            Map<String, Object> jvm = new HashMap<>();
            jvm.put("uptime-ms", ManagementFactory.getRuntimeMXBean().getUptime());
            jvm.put("processors", runtime.availableProcessors());
            jvm.put("load-average", osBean.getSystemLoadAverage());
            
            // Disk metrics
            Map<String, Object> disk = new HashMap<>();
            java.io.File root = new java.io.File("/");
            disk.put("total-space-gb", root.getTotalSpace() / (1024 * 1024 * 1024));
            disk.put("free-space-gb", root.getFreeSpace() / (1024 * 1024 * 1024));
            disk.put("usable-space-gb", root.getUsableSpace() / (1024 * 1024 * 1024));
            
            metrics.put("memory", memory);
            metrics.put("jvm", jvm);
            metrics.put("disk", disk);
            metrics.put("service", serviceName);
            metrics.put("timestamp", Instant.now().toString());
            
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("Failed to get system metrics", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to collect system metrics: " + e.getMessage(),
                "timestamp", Instant.now().toString()
            ));
        }
    }

    /**
     * Service info endpoint - basic service information with authentication
     */
    @GetMapping("/info")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<Map<String, String>> serviceInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("service", serviceName);
        info.put("version", getServiceVersion());
        info.put("environment", getEnvironment());
        info.put("java-version", System.getProperty("java.version"));
        info.put("spring-profiles", System.getProperty("spring.profiles.active", "default"));
        info.put("timestamp", Instant.now().toString());
        info.put("uptime", String.valueOf(ManagementFactory.getRuntimeMXBean().getUptime()));
        
        return ResponseEntity.ok(info);
    }

    /**
     * Manual circuit breaker reset endpoint - CRITICAL ADMIN OPERATION
     */
    @PostMapping("/circuit-breakers/{serviceName}/reset")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM')")
    public ResponseEntity<Map<String, String>> resetCircuitBreaker(@PathVariable String serviceName) {
        
        log.warn("ADMIN ACTION: Circuit breaker reset requested for service: {} by user", serviceName);
        
        // Additional validation for critical operations
        if (serviceName == null || serviceName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Service name is required",
                "timestamp", Instant.now().toString()
            ));
        }
        try {
            resilientExecutor.resetCircuitBreaker(serviceName);
            
            log.info("ADMIN ACTION: Circuit breaker successfully reset for service: {}", serviceName);
            
            return ResponseEntity.ok(Map.of(
                "message", "Circuit breaker reset successfully",
                "service", serviceName,
                "timestamp", Instant.now().toString(),
                "action", "CIRCUIT_BREAKER_RESET"
            ));
        } catch (Exception e) {
            log.error("Failed to reset circuit breaker for service: {}", serviceName, e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to reset circuit breaker: " + e.getMessage(),
                "service", serviceName,
                "timestamp", Instant.now().toString()
            ));
        }
    }

    // Helper methods

    private boolean checkDatabaseReadiness() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(3);
        } catch (Exception e) {
            log.debug("Database not ready", e);
            return false;
        }
    }

    private boolean checkRedisReadiness() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            log.debug("Redis not ready", e);
            return false;
        }
    }

    private String getServiceVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "1.0.0-SNAPSHOT";
    }

    private String getEnvironment() {
        return System.getProperty("spring.profiles.active", 
               System.getenv("ENVIRONMENT") != null ? System.getenv("ENVIRONMENT") : "development");
    }
    
    /**
     * Audit health check access for security monitoring
     */
    private void auditHealthAccess(String endpoint) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "anonymous";
            String authorities = auth != null ? auth.getAuthorities().toString() : "none";
            
            log.info("SECURITY_AUDIT: Health endpoint accessed - Endpoint: {}, User: {}, Roles: {}, Service: {}",
                endpoint, username, authorities, serviceName);
                
        } catch (Exception e) {
            log.warn("Failed to audit health check access for endpoint: {}", endpoint, e);
        }
    }
}