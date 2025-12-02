package com.waqiti.common.security.hsm;

import com.waqiti.common.security.hsm.exception.HSMException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HSM Health Monitor for continuous monitoring and alerting
 * 
 * Features:
 * - Real-time HSM health monitoring
 * - Performance metrics collection
 * - Automatic failover detection
 * - Compliance reporting for FIPS 140-2/3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HSMHealthMonitor implements HealthIndicator {

    private final HSMProvider hsmProvider;
    
    private final AtomicBoolean hsmHealthy = new AtomicBoolean(true);
    private final AtomicLong lastHealthCheck = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong totalOperations = new AtomicLong(0);
    private final AtomicLong failedOperations = new AtomicLong(0);
    private final AtomicLong averageResponseTime = new AtomicLong(0);
    
    private HSMStatus lastKnownStatus;
    private LocalDateTime lastStatusUpdate;
    private String lastError;
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing HSM Health Monitor");
        performHealthCheck();
    }

    /**
     * Spring Boot Actuator health check
     */
    @Override
    public Health health() {
        try {
            if (!hsmHealthy.get()) {
                return Health.down()
                    .withDetail("status", "UNHEALTHY")
                    .withDetail("lastError", lastError)
                    .withDetail("lastHealthCheck", new java.util.Date(lastHealthCheck.get()))
                    .withDetail("provider", hsmProvider.getProviderType())
                    .build();
            }
            
            HSMStatus status = hsmProvider.getStatus();
            
            return Health.up()
                .withDetail("status", "HEALTHY")
                .withDetail("provider", hsmProvider.getProviderType())
                .withDetail("totalOperations", totalOperations.get())
                .withDetail("failedOperations", failedOperations.get())
                .withDetail("successRate", calculateSuccessRate())
                .withDetail("averageResponseTime", averageResponseTime.get() + "ms")
                .withDetail("lastHealthCheck", new java.util.Date(lastHealthCheck.get()))
                .withDetail("hsmStatus", status)
                .build();
                
        } catch (Exception e) {
            log.error("Health check failed", e);
            hsmHealthy.set(false);
            lastError = e.getMessage();
            
            return Health.down()
                .withDetail("status", "ERROR")
                .withDetail("error", e.getMessage())
                .withDetail("provider", hsmProvider.getProviderType())
                .build();
        }
    }

    /**
     * Scheduled health check every 30 seconds
     */
    @Scheduled(fixedDelay = 30000)
    public void performHealthCheck() {
        try {
            long startTime = System.currentTimeMillis();
            
            log.debug("Performing HSM health check");
            
            // Test basic connectivity
            boolean connected = hsmProvider.testConnection();
            if (!connected) {
                throw new HSMException("HSM connection test failed");
            }
            
            // Get detailed status
            HSMStatus status = hsmProvider.getStatus();
            lastKnownStatus = status;
            lastStatusUpdate = LocalDateTime.now();
            
            // Validate critical status fields
            if (status == null || !status.isHealthy()) {
                throw new HSMException("HSM status indicates unhealthy state: " + status);
            }
            
            // Update metrics
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(responseTime, true);
            
            // Mark as healthy
            hsmHealthy.set(true);
            lastError = null;
            lastHealthCheck.set(System.currentTimeMillis());
            
            log.debug("HSM health check passed in {}ms", responseTime);
            
        } catch (Exception e) {
            log.error("HSM health check failed", e);
            
            hsmHealthy.set(false);
            lastError = e.getMessage();
            lastHealthCheck.set(System.currentTimeMillis());
            
            updateMetrics(0, false);
            
            // Send critical alert for HSM failure
            sendCriticalAlert(e);
        }
    }

    /**
     * Performance test - runs every 5 minutes
     */
    @Scheduled(fixedDelay = 300000)
    public void performPerformanceTest() {
        if (!hsmHealthy.get()) {
            log.debug("Skipping performance test - HSM is unhealthy");
            return;
        }
        
        try {
            log.debug("Performing HSM performance test");
            
            long startTime = System.currentTimeMillis();
            
            // Test key generation performance
            String testKeyId = "perf-test-" + System.currentTimeMillis();
            
            try {
                HSMKeyHandle keyHandle = hsmProvider.generateSecretKey(testKeyId, "AES", 256);
                
                // Test encryption performance
                byte[] testData = "HSM Performance Test Data".getBytes();
                byte[] encrypted = hsmProvider.encrypt(testKeyId, testData, "AES/GCM/NoPadding");
                byte[] decrypted = hsmProvider.decrypt(testKeyId, encrypted, "AES/GCM/NoPadding");
                
                if (!java.util.Arrays.equals(testData, decrypted)) {
                    throw new HSMException("HSM encrypt/decrypt test failed - data integrity check failed");
                }
                
                // Clean up test key
                hsmProvider.deleteKey(testKeyId);
                
                long responseTime = System.currentTimeMillis() - startTime;
                updateMetrics(responseTime, true);
                
                log.debug("HSM performance test completed successfully in {}ms", responseTime);
                
            } catch (Exception e) {
                // Try to clean up test key even if test failed
                try {
                    hsmProvider.deleteKey(testKeyId);
                } catch (Exception cleanupError) {
                    log.warn("Failed to clean up test key: {}", cleanupError.getMessage());
                }
                throw e;
            }
            
        } catch (Exception e) {
            log.error("HSM performance test failed", e);
            updateMetrics(0, false);
        }
    }

    /**
     * Update performance metrics
     */
    private void updateMetrics(long responseTime, boolean success) {
        totalOperations.incrementAndGet();
        
        if (!success) {
            failedOperations.incrementAndGet();
        }
        
        if (responseTime > 0) {
            // Update running average response time
            long currentAvg = averageResponseTime.get();
            long totalOps = totalOperations.get();
            
            if (totalOps == 1) {
                averageResponseTime.set(responseTime);
            } else {
                // Calculate new running average
                long newAvg = ((currentAvg * (totalOps - 1)) + responseTime) / totalOps;
                averageResponseTime.set(newAvg);
            }
        }
    }

    /**
     * Calculate success rate percentage
     */
    private double calculateSuccessRate() {
        long total = totalOperations.get();
        if (total == 0) {
            return 100.0;
        }
        
        long failed = failedOperations.get();
        return ((double) (total - failed) / total) * 100.0;
    }

    /**
     * Send critical alert when HSM fails
     */
    private void sendCriticalAlert(Exception error) {
        log.error("CRITICAL: HSM FAILURE DETECTED - Manual intervention may be required", error);
        
        // This would integrate with monitoring systems like:
        // - PagerDuty
        // - Slack/Teams notifications  
        // - SIEM systems
        // - Email alerts
        
        // For now, just log the critical event
        log.error("HSM Alert Details:");
        log.error("  Provider: {}", hsmProvider.getProviderType());
        log.error("  Error: {}", error.getMessage());
        log.error("  Time: {}", LocalDateTime.now());
        log.error("  Success Rate: {}%", calculateSuccessRate());
        log.error("  Total Operations: {}", totalOperations.get());
        log.error("  Failed Operations: {}", failedOperations.get());
    }

    /**
     * Get current HSM health status
     */
    public boolean isHSMHealthy() {
        return hsmHealthy.get();
    }

    /**
     * Get performance metrics
     */
    public HSMMetrics getMetrics() {
        return HSMMetrics.builder()
            .healthy(hsmHealthy.get())
            .totalOperations(totalOperations.get())
            .failedOperations(failedOperations.get())
            .successRate(calculateSuccessRate())
            .averageResponseTime(averageResponseTime.get())
            .lastHealthCheck(new java.util.Date(lastHealthCheck.get()))
            .providerType(hsmProvider.getProviderType())
            .lastKnownStatus(lastKnownStatus)
            .lastError(lastError)
            .build();
    }

    /**
     * Force immediate health check
     */
    public void forceHealthCheck() {
        log.info("Forcing immediate HSM health check");
        performHealthCheck();
    }

    /**
     * Reset performance metrics
     */
    public void resetMetrics() {
        log.info("Resetting HSM performance metrics");
        totalOperations.set(0);
        failedOperations.set(0);
        averageResponseTime.set(0);
        lastError = null;
    }
    
    /**
     * HSM Metrics data class
     */
    @lombok.Builder
    @lombok.Data
    public static class HSMMetrics {
        private boolean healthy;
        private long totalOperations;
        private long failedOperations;
        private double successRate;
        private long averageResponseTime;
        private java.util.Date lastHealthCheck;
        private HSMProvider.HSMProviderType providerType;
        private HSMStatus lastKnownStatus;
        private String lastError;
    }
}