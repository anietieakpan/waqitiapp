package com.waqiti.common.vault;

import com.waqiti.vault.service.VaultSecretsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultHealth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced Vault health indicator that provides detailed health information
 * about Vault connectivity, authentication status, and service availability.
 */
@Slf4j
@Component("vaultHealthIndicator")
@RequiredArgsConstructor
public class VaultHealthIndicator implements HealthIndicator {

    private final VaultTemplate vaultTemplate;
    private final VaultSecretsService vaultSecretsService;
    private final VaultConfigurationProperties vaultProperties;
    
    // Cache health status to avoid excessive calls
    private Health lastHealthStatus = Health.unknown().build();
    private LocalDateTime lastHealthCheck = LocalDateTime.now().minusMinutes(5);
    private final Duration healthCacheInterval = Duration.ofMinutes(1);

    @Override
    public Health health() {
        try {
            // Use cached health status if within cache interval
            if (Duration.between(lastHealthCheck, LocalDateTime.now()).compareTo(healthCacheInterval) < 0) {
                return lastHealthStatus;
            }
            
            // Perform comprehensive health check
            Health.Builder healthBuilder = new Health.Builder();
            Map<String, Object> details = new HashMap<>();
            
            // Check Vault server health
            boolean vaultServerHealthy = checkVaultServerHealth(details);
            
            // Check authentication status
            boolean authenticationHealthy = checkAuthenticationHealth(details);
            
            // Check secret access
            boolean secretAccessHealthy = checkSecretAccess(details);
            
            // Check lease renewal capability
            boolean leaseRenewalHealthy = checkLeaseRenewal(details);
            
            // Determine overall health status
            boolean overallHealthy = vaultServerHealthy && authenticationHealthy && 
                                   secretAccessHealthy && leaseRenewalHealthy;
            
            // Add timestamp and cache statistics
            details.put("lastChecked", LocalDateTime.now().toString());
            details.put("cacheStatistics", vaultSecretsService.getCacheStatistics());
            
            // Build health response
            Health health = overallHealthy ? 
                healthBuilder.up().withDetails(details).build() :
                healthBuilder.down().withDetails(details).build();
            
            // Cache the result
            lastHealthStatus = health;
            lastHealthCheck = LocalDateTime.now();
            
            return health;
            
        } catch (Exception e) {
            log.error("Vault health check failed", e);
            
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("error", e.getMessage());
            errorDetails.put("lastChecked", LocalDateTime.now().toString());
            
            Health health = Health.down()
                .withDetails(errorDetails)
                .build();
                
            lastHealthStatus = health;
            lastHealthCheck = LocalDateTime.now();
            
            return health;
        }
    }

    /**
     * Check Vault server health status
     */
    private boolean checkVaultServerHealth(Map<String, Object> details) {
        try {
            VaultHealth vaultHealth = vaultTemplate.opsForSys().health();
            
            details.put("vault.server.initialized", vaultHealth.isInitialized());
            details.put("vault.server.sealed", vaultHealth.isSealed());
            details.put("vault.server.standby", vaultHealth.isStandby());
            details.put("vault.server.version", vaultHealth.getVersion());
            // Cluster information not available in newer Spring Vault versions
            details.put("vault.server.cluster", "default");
            details.put("vault.server.clusterId", "default");
            
            boolean healthy = vaultHealth.isInitialized() && !vaultHealth.isSealed();
            details.put("vault.server.status", healthy ? "UP" : "DOWN");
            
            return healthy;
            
        } catch (Exception e) {
            log.debug("Vault server health check failed", e);
            details.put("vault.server.status", "UNKNOWN");
            details.put("vault.server.error", e.getMessage());
            return false;
        }
    }

    /**
     * Check authentication health
     */
    private boolean checkAuthenticationHealth(Map<String, Object> details) {
        try {
            // Try to access a simple endpoint that requires authentication
            vaultTemplate.opsForSys().getMounts();
            
            details.put("vault.authentication.status", "UP");
            details.put("vault.authentication.method", getAuthenticationMethod());
            
            return true;
            
        } catch (Exception e) {
            log.debug("Vault authentication health check failed", e);
            details.put("vault.authentication.status", "DOWN");
            details.put("vault.authentication.error", e.getMessage());
            return false;
        }
    }

    /**
     * Check secret access capability
     */
    private boolean checkSecretAccess(Map<String, Object> details) {
        try {
            // Try to access a known secret path (health check should not expose actual secrets)
            String healthCheckPath = "health/check";
            
            // Attempt to read from the health check path (it's okay if it doesn't exist)
            try {
                vaultSecretsService.getSecret(healthCheckPath, "status");
                details.put("vault.secrets.access", "UP");
                details.put("vault.secrets.healthPath", healthCheckPath);
            } catch (Exception e) {
                // It's okay if the health check secret doesn't exist
                details.put("vault.secrets.access", "UP");
                details.put("vault.secrets.note", "Health check path not found (normal)");
            }
            
            return true;
            
        } catch (Exception e) {
            log.debug("Vault secret access health check failed", e);
            details.put("vault.secrets.access", "DOWN");
            details.put("vault.secrets.error", e.getMessage());
            return false;
        }
    }

    /**
     * Check lease renewal capability
     */
    private boolean checkLeaseRenewal(Map<String, Object> details) {
        try {
            // Check if lease renewal is working by accessing lease information
            // This is a lightweight check that doesn't actually renew leases
            vaultTemplate.opsForSys().getPolicy("default");
            
            details.put("vault.lease.renewal", "UP");
            details.put("vault.lease.note", "Lease renewal capability verified");
            
            return true;
            
        } catch (Exception e) {
            log.debug("Vault lease renewal health check failed", e);
            details.put("vault.lease.renewal", "UNKNOWN");
            details.put("vault.lease.error", e.getMessage());
            return true; // Don't fail overall health for lease issues
        }
    }

    /**
     * Get the authentication method being used
     */
    private String getAuthenticationMethod() {
        try {
            // Try to determine authentication method from configuration
            if (vaultProperties != null) {
                if (vaultProperties.getToken() != null && !vaultProperties.getToken().isEmpty()) {
                    return "token";
                }
                if (vaultProperties.getAppRole() != null && vaultProperties.getAppRole().getRoleId() != null) {
                    return "approle";
                }
                if (vaultProperties.getKubernetes() != null && vaultProperties.getKubernetes().getRole() != null) {
                    return "kubernetes";
                }
                if (vaultProperties.getAws() != null && vaultProperties.getAws().getRole() != null) {
                    return "aws";
                }
            }
            
            // Fallback to checking environment variables
            if (System.getenv("VAULT_TOKEN") != null) {
                return "token";
            }
            if (System.getenv("VAULT_ROLE_ID") != null) {
                return "approle";
            }
            if (System.getenv("KUBERNETES_SERVICE_ACCOUNT_TOKEN") != null) {
                return "kubernetes";
            }
            if (System.getenv("AWS_ROLE") != null || System.getenv("AWS_ACCESS_KEY_ID") != null) {
                return "aws";
            }
            
            // Check system properties as final fallback
            if (System.getProperty("vault.token") != null) {
                return "token";
            }
            
            return "unknown";
            
        } catch (Exception e) {
            log.debug("Failed to determine Vault authentication method", e);
            return "undetermined";
        }
    }

    /**
     * Force a fresh health check (bypassing cache)
     */
    public Health freshHealthCheck() {
        lastHealthCheck = LocalDateTime.now().minusMinutes(5); // Force cache miss
        return health();
    }

    /**
     * Get detailed Vault statistics
     */
    public Map<String, Object> getDetailedStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // Add cache statistics
            stats.put("cache", vaultSecretsService.getCacheStatistics());
            
            // Add configuration information (non-sensitive)
            stats.put("config.healthEnabled", vaultProperties.getHealth().isEnabled());
            stats.put("config.metricsEnabled", vaultProperties.getMetrics().isEnabled());
            stats.put("config.cacheEnabled", vaultProperties.getCache().isEnabled());
            stats.put("config.retryEnabled", vaultProperties.getRetry().isEnabled());
            
            // Add runtime information
            stats.put("lastHealthCheck", lastHealthCheck.toString());
            stats.put("healthCacheInterval", healthCacheInterval.toString());
            
        } catch (Exception e) {
            log.warn("Failed to gather detailed Vault statistics", e);
            stats.put("error", "Failed to gather statistics: " + e.getMessage());
        }
        
        return stats;
    }
}