package com.waqiti.ledger.controller;

import com.waqiti.ledger.service.HealthCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Health Check Controller
 * 
 * Provides comprehensive health check endpoints for monitoring
 * service availability, database connectivity, and system metrics.
 */
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health Checks", description = "Service health monitoring APIs")
public class HealthController {

    private final HealthCheckService healthCheckService;

    @GetMapping
    @Operation(summary = "Basic health check", 
               description = "Check if the service is running and responsive")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Service is healthy"),
        @ApiResponse(responseCode = "503", description = "Service is unhealthy")
    })
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now());
        response.put("service", "waqiti-ledger-service");
        response.put("version", "1.0.0");
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/detailed")
    @Operation(summary = "Detailed health check", 
               description = "Comprehensive health check including all dependencies")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Detailed health information retrieved"),
        @ApiResponse(responseCode = "503", description = "One or more components are unhealthy")
    })
    public ResponseEntity<Health> detailedHealth() {
        try {
            Health health = healthCheckService.performDetailedHealthCheck();
            
            HttpStatus status = health.getStatus() == Status.UP ? 
                HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
                
            return ResponseEntity.status(status).body(health);
            
        } catch (Exception e) {
            log.error("Health check failed", e);
            
            Health unhealthyHealth = Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("timestamp", LocalDateTime.now())
                .build();
                
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(unhealthyHealth);
        }
    }

    @GetMapping("/database")
    @Operation(summary = "Database health check", 
               description = "Check database connectivity and performance")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Database is healthy"),
        @ApiResponse(responseCode = "503", description = "Database connection issues")
    })
    public ResponseEntity<Map<String, Object>> databaseHealth() {
        try {
            Map<String, Object> dbHealth = healthCheckService.checkDatabaseHealth();
            
            boolean isHealthy = (Boolean) dbHealth.getOrDefault("healthy", false);
            HttpStatus status = isHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            
            return ResponseEntity.status(status).body(dbHealth);
            
        } catch (Exception e) {
            log.error("Database health check failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("healthy", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }

    @GetMapping("/cache")
    @Operation(summary = "Cache health check", 
               description = "Check Redis cache connectivity and performance")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cache is healthy"),
        @ApiResponse(responseCode = "503", description = "Cache connection issues")
    })
    public ResponseEntity<Map<String, Object>> cacheHealth() {
        try {
            Map<String, Object> cacheHealth = healthCheckService.checkCacheHealth();
            
            boolean isHealthy = (Boolean) cacheHealth.getOrDefault("healthy", false);
            HttpStatus status = isHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            
            return ResponseEntity.status(status).body(cacheHealth);
            
        } catch (Exception e) {
            log.error("Cache health check failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("healthy", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }

    @GetMapping("/readiness")
    @Operation(summary = "Readiness check", 
               description = "Check if service is ready to accept requests")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Service is ready"),
        @ApiResponse(responseCode = "503", description = "Service is not ready")
    })
    public ResponseEntity<Map<String, Object>> readiness() {
        try {
            boolean isReady = healthCheckService.isServiceReady();
            
            Map<String, Object> response = new HashMap<>();
            response.put("ready", isReady);
            response.put("timestamp", LocalDateTime.now());
            response.put("checks", healthCheckService.getReadinessChecks());
            
            HttpStatus status = isReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(response);
            
        } catch (Exception e) {
            log.error("Readiness check failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("ready", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }

    @GetMapping("/liveness")
    @Operation(summary = "Liveness check", 
               description = "Check if service is alive and should not be restarted")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Service is alive"),
        @ApiResponse(responseCode = "503", description = "Service should be restarted")
    })
    public ResponseEntity<Map<String, Object>> liveness() {
        try {
            boolean isAlive = healthCheckService.isServiceAlive();
            
            Map<String, Object> response = new HashMap<>();
            response.put("alive", isAlive);
            response.put("timestamp", LocalDateTime.now());
            response.put("uptime", healthCheckService.getUptime());
            response.put("memoryUsage", healthCheckService.getMemoryUsage());
            
            HttpStatus status = isAlive ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(response);
            
        } catch (Exception e) {
            log.error("Liveness check failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("alive", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }

    @GetMapping("/metrics")
    @Operation(summary = "Application metrics", 
               description = "Get key application performance metrics")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metrics retrieved successfully")
    })
    public ResponseEntity<Map<String, Object>> metrics() {
        try {
            Map<String, Object> metrics = healthCheckService.getApplicationMetrics();
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("Failed to retrieve metrics", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve metrics");
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/dependencies")
    @Operation(summary = "Dependency health", 
               description = "Check health of all external dependencies")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dependency health retrieved"),
        @ApiResponse(responseCode = "503", description = "One or more dependencies are unhealthy")
    })
    public ResponseEntity<Map<String, Object>> dependencies() {
        try {
            Map<String, Object> dependencyHealth = healthCheckService.checkDependenciesHealth();
            
            boolean allHealthy = dependencyHealth.values().stream()
                .allMatch(health -> {
                    if (health instanceof Map) {
                        return (Boolean) ((Map<?, ?>) health).getOrDefault("healthy", false);
                    }
                    return false;
                });
            
            HttpStatus status = allHealthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(dependencyHealth);
            
        } catch (Exception e) {
            log.error("Dependency health check failed", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Dependency health check failed");
            errorResponse.put("details", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
        }
    }
}