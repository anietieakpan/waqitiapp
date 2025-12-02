package com.waqiti.vault.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultHealth;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Vault Health Service
 * 
 * Monitors Vault cluster health and provides health indicators
 * for Spring Boot Actuator and application monitoring.
 */
@Service
public class VaultHealthService implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(VaultHealthService.class);
    
    private final VaultTemplate vaultTemplate;
    private final MeterRegistry meterRegistry;
    
    private final AtomicBoolean isHealthy = new AtomicBoolean(false);
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastHealthCheck = new AtomicReference<>();
    private final AtomicReference<VaultHealthStatus> healthStatus = new AtomicReference<>();

    public VaultHealthService(VaultTemplate vaultTemplate, MeterRegistry meterRegistry) {
        this.vaultTemplate = vaultTemplate;
        this.meterRegistry = meterRegistry;
        this.healthStatus.set(new VaultHealthStatus());
    }

    @PostConstruct
    private void initializeMetrics() {
        Gauge.builder("vault.health.status", this, service -> service.isHealthy.get() ? 1.0 : 0.0)
                .description("Vault health status (1 = healthy, 0 = unhealthy)")
                .register(meterRegistry);
        
        Gauge.builder("vault.health.sealed", this, service -> {
                    VaultHealthStatus status = service.healthStatus.get();
                    return status != null && status.isSealed() ? 1.0 : 0.0;
                })
                .description("Vault sealed status (1 = sealed, 0 = unsealed)")
                .register(meterRegistry);
        
        Gauge.builder("vault.health.standby", this, service -> {
                    VaultHealthStatus status = service.healthStatus.get();
                    return status != null && status.isStandby() ? 1.0 : 0.0;
                })
                .description("Vault standby status (1 = standby, 0 = active)")
                .register(meterRegistry);
    }

    @Override
    public Health health() {
        try {
            VaultHealthStatus status = checkVaultHealth();
            lastHealthCheck.set(LocalDateTime.now());
            
            if (status.isHealthy()) {
                isHealthy.set(true);
                lastError.set(null);
                
                return Health.up()
                        .withDetail("vault.initialized", status.isInitialized())
                        .withDetail("vault.sealed", status.isSealed())
                        .withDetail("vault.standby", status.isStandby())
                        .withDetail("vault.performance_standby", status.isPerformanceStandby())
                        .withDetail("vault.replication_performance_mode", status.getReplicationPerformanceMode())
                        .withDetail("vault.replication_dr_mode", status.getReplicationDrMode())
                        .withDetail("vault.server_time_utc", status.getServerTimeUtc())
                        .withDetail("vault.version", status.getVersion())
                        .withDetail("vault.cluster_name", status.getClusterName())
                        .withDetail("vault.cluster_id", status.getClusterId())
                        .withDetail("last_check", lastHealthCheck.get())
                        .build();
            } else {
                isHealthy.set(false);
                String error = "Vault is not healthy: " + status.getErrorMessage();
                lastError.set(error);
                
                return Health.down()
                        .withDetail("error", error)
                        .withDetail("vault.sealed", status.isSealed())
                        .withDetail("vault.initialized", status.isInitialized())
                        .withDetail("last_check", lastHealthCheck.get())
                        .build();
            }
            
        } catch (Exception e) {
            isHealthy.set(false);
            String error = "Failed to check Vault health: " + e.getMessage();
            lastError.set(error);
            logger.error("Vault health check failed", e);
            
            return Health.down()
                    .withDetail("error", error)
                    .withDetail("exception", e.getClass().getSimpleName())
                    .withDetail("last_check", lastHealthCheck.get())
                    .build();
        }
    }

    /**
     * Scheduled health check that runs every 30 seconds
     */
    @Scheduled(fixedRate = 30000, initialDelay = 10000)
    public void scheduledHealthCheck() {
        try {
            VaultHealthStatus status = checkVaultHealth();
            healthStatus.set(status);
            
            if (status.isHealthy()) {
                if (!isHealthy.get()) {
                    logger.info("Vault health restored");
                }
                isHealthy.set(true);
                lastError.set(null);
            } else {
                if (isHealthy.get()) {
                    logger.warn("Vault health degraded: {}", status.getErrorMessage());
                }
                isHealthy.set(false);
                lastError.set(status.getErrorMessage());
            }
            
            lastHealthCheck.set(LocalDateTime.now());
            
        } catch (Exception e) {
            if (isHealthy.get()) {
                logger.error("Vault health check failed", e);
            }
            isHealthy.set(false);
            lastError.set("Health check failed: " + e.getMessage());
            lastHealthCheck.set(LocalDateTime.now());
        }
    }

    /**
     * Get current health status
     */
    public boolean isVaultHealthy() {
        return isHealthy.get();
    }

    /**
     * Get last error message
     */
    public String getLastError() {
        return lastError.get();
    }

    /**
     * Get last health check time
     */
    public LocalDateTime getLastHealthCheck() {
        return lastHealthCheck.get();
    }

    /**
     * Get detailed health status
     */
    public VaultHealthStatus getDetailedHealthStatus() {
        return healthStatus.get();
    }

    /**
     * Force a health check
     */
    public VaultHealthStatus forceHealthCheck() {
        try {
            VaultHealthStatus status = checkVaultHealth();
            healthStatus.set(status);
            lastHealthCheck.set(LocalDateTime.now());
            
            if (status.isHealthy()) {
                isHealthy.set(true);
                lastError.set(null);
            } else {
                isHealthy.set(false);
                lastError.set(status.getErrorMessage());
            }
            
            return status;
            
        } catch (Exception e) {
            logger.error("Forced Vault health check failed", e);
            isHealthy.set(false);
            lastError.set("Health check failed: " + e.getMessage());
            lastHealthCheck.set(LocalDateTime.now());
            
            return new VaultHealthStatus("Health check failed: " + e.getMessage());
        }
    }

    private VaultHealthStatus checkVaultHealth() {
        try {
            VaultHealth vaultHealth = vaultTemplate.opsForSys().health();
            
            if (vaultHealth == null) {
                return new VaultHealthStatus("No health response from Vault");
            }
            
            // Check if Vault is healthy based on its response
            boolean healthy = vaultHealth.isInitialized() && !vaultHealth.isSealed();
            
            return new VaultHealthStatus(
                healthy,
                vaultHealth.isInitialized(),
                vaultHealth.isSealed(),
                vaultHealth.isStandby(),
                vaultHealth.isPerformanceStandby(),
                vaultHealth.getReplicationPerformanceMode(),
                vaultHealth.getReplicationDrMode(),
                vaultHealth.getServerTimeUtc(),
                vaultHealth.getVersion(),
                vaultHealth.getClusterName(),
                vaultHealth.getClusterId(),
                healthy ? null : buildErrorMessage(vaultHealth)
            );
            
        } catch (Exception e) {
            logger.debug("Vault health check exception", e);
            return new VaultHealthStatus("Vault health check failed: " + e.getMessage());
        }
    }

    private String buildErrorMessage(VaultHealth vaultHealth) {
        if (!vaultHealth.isInitialized()) {
            return "Vault is not initialized";
        }
        if (vaultHealth.isSealed()) {
            return "Vault is sealed";
        }
        return "Vault is not healthy";
    }

    /**
     * Vault Health Status class
     */
    public static class VaultHealthStatus {
        private final boolean healthy;
        private final boolean initialized;
        private final boolean sealed;
        private final boolean standby;
        private final boolean performanceStandby;
        private final String replicationPerformanceMode;
        private final String replicationDrMode;
        private final Long serverTimeUtc;
        private final String version;
        private final String clusterName;
        private final String clusterId;
        private final String errorMessage;

        // Constructor for error status
        public VaultHealthStatus(String errorMessage) {
            this.healthy = false;
            this.initialized = false;
            this.sealed = true;
            this.standby = false;
            this.performanceStandby = false;
            this.replicationPerformanceMode = null;
            this.replicationDrMode = null;
            this.serverTimeUtc = null;
            this.version = null;
            this.clusterName = null;
            this.clusterId = null;
            this.errorMessage = errorMessage;
        }

        // Constructor for unknown status
        public VaultHealthStatus() {
            this("Health status unknown");
        }

        // Full constructor
        public VaultHealthStatus(boolean healthy, boolean initialized, boolean sealed, boolean standby,
                               boolean performanceStandby, String replicationPerformanceMode,
                               String replicationDrMode, Long serverTimeUtc, String version,
                               String clusterName, String clusterId, String errorMessage) {
            this.healthy = healthy;
            this.initialized = initialized;
            this.sealed = sealed;
            this.standby = standby;
            this.performanceStandby = performanceStandby;
            this.replicationPerformanceMode = replicationPerformanceMode;
            this.replicationDrMode = replicationDrMode;
            this.serverTimeUtc = serverTimeUtc;
            this.version = version;
            this.clusterName = clusterName;
            this.clusterId = clusterId;
            this.errorMessage = errorMessage;
        }

        // Getters
        public boolean isHealthy() { return healthy; }
        public boolean isInitialized() { return initialized; }
        public boolean isSealed() { return sealed; }
        public boolean isStandby() { return standby; }
        public boolean isPerformanceStandby() { return performanceStandby; }
        public String getReplicationPerformanceMode() { return replicationPerformanceMode; }
        public String getReplicationDrMode() { return replicationDrMode; }
        public Long getServerTimeUtc() { return serverTimeUtc; }
        public String getVersion() { return version; }
        public String getClusterName() { return clusterName; }
        public String getClusterId() { return clusterId; }
        public String getErrorMessage() { return errorMessage; }

        public boolean isActive() {
            return healthy && initialized && !sealed && !standby;
        }

        @Override
        public String toString() {
            if (!healthy) {
                return "VaultHealthStatus{healthy=false, error='" + errorMessage + "'}";
            }
            return "VaultHealthStatus{" +
                    "healthy=" + healthy +
                    ", initialized=" + initialized +
                    ", sealed=" + sealed +
                    ", standby=" + standby +
                    ", version='" + version + '\'' +
                    ", clusterName='" + clusterName + '\'' +
                    '}';
        }
    }
}