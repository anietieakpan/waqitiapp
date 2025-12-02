package com.waqiti.common.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for database connection pool monitoring
 */
@RestController
@RequestMapping("/api/monitoring/database-pool")
@RequiredArgsConstructor
@Slf4j
public class DatabasePoolController {
    
    private final DatabasePoolMonitor poolMonitor;
    
    /**
     * Get current connection pool metrics
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('MONITORING_READ')")
    public ResponseEntity<ConnectionPoolMetrics> getCurrentMetrics() {
        try {
            ConnectionPoolMetrics metrics = poolMonitor.getCurrentMetrics();
            
            if (metrics == null) {
                return ResponseEntity.noContent().build();
            }
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Failed to get connection pool metrics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get connection pool statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('MONITORING_READ')")
    public ResponseEntity<ConnectionPoolStatistics> getStatistics() {
        try {
            ConnectionPoolStatistics statistics = poolMonitor.getStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Failed to get connection pool statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Test database connection
     */
    @GetMapping("/test")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('MONITORING_READ')")
    public ResponseEntity<ConnectionTestResult> testConnection() {
        try {
            ConnectionTestResult result = poolMonitor.testConnection();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to test database connection", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get connection pool health status
     */
    @GetMapping("/health")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('MONITORING_READ')")
    public ResponseEntity<ConnectionPoolHealth> getHealth() {
        try {
            ConnectionPoolMetrics metrics = poolMonitor.getCurrentMetrics();
            
            if (metrics == null) {
                return ResponseEntity.ok(ConnectionPoolHealth.builder()
                    .status("UNKNOWN")
                    .message("Unable to retrieve pool metrics")
                    .build());
            }
            
            ConnectionPoolHealth health = ConnectionPoolHealth.builder()
                .status(metrics.getHealthStatus())
                .healthy(metrics.isHealthy())
                .activeConnections(metrics.getActiveConnections())
                .idleConnections(metrics.getIdleConnections())
                .totalConnections(metrics.getTotalConnections())
                .utilization(metrics.getUtilizationPercentage())
                .threadsWaiting(metrics.getThreadsAwaitingConnection())
                .message(generateHealthMessage(metrics))
                .build();
                
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Failed to get connection pool health", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private String generateHealthMessage(ConnectionPoolMetrics metrics) {
        if (metrics.getThreadsAwaitingConnection() > 0) {
            return String.format("%d threads waiting for connections - pool may be undersized", 
                metrics.getThreadsAwaitingConnection());
        }
        
        if (metrics.getUtilizationPercentage() > 80) {
            return String.format("High utilization (%.1f%%) - consider increasing pool size", 
                metrics.getUtilizationPercentage());
        }
        
        if (metrics.getIdleConnections() == 0) {
            return "No idle connections available - all connections in use";
        }
        
        return String.format("Pool operating normally with %.1f%% utilization", 
            metrics.getUtilizationPercentage());
    }
}