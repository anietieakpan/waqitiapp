package com.waqiti.common.vault;

import com.waqiti.vault.service.VaultSecretsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Service-specific Vault integration helper
 * 
 * Provides convenient methods for services to access their Vault secrets
 * with proper namespacing and error handling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "spring.cloud.vault.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceVaultIntegration {

    private final VaultSecretsService vaultSecretsService;
    private final VaultConfigurationProperties vaultProperties;
    
    @Value("${spring.application.name}")
    private String serviceName;
    
    @Value("${spring.profiles.active:development}")
    private String activeProfile;
    
    private Map<String, String> cachedPaths = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("Initializing Vault integration for service: {} with profile: {}", serviceName, activeProfile);
        initializePaths();
    }

    /**
     * Initialize service-specific paths
     */
    private void initializePaths() {
        // Database paths
        cachedPaths.put("database", String.format("database/%s", serviceName));
        cachedPaths.put("database-read", String.format("database/%s-read", serviceName));
        cachedPaths.put("database-static", String.format("database/%s-static", serviceName));
        
        // JWT paths
        cachedPaths.put("jwt", String.format("jwt/%s", serviceName));
        cachedPaths.put("jwt-refresh", String.format("jwt/%s/refresh", serviceName));
        
        // API keys
        cachedPaths.put("api-keys", String.format("api-keys/%s", serviceName));
        
        // Encryption
        cachedPaths.put("encryption", String.format("encryption/%s", serviceName));
        
        // Application specific
        cachedPaths.put("application", String.format("application/%s", serviceName));
        
        log.debug("Initialized Vault paths for service {}: {}", serviceName, cachedPaths);
    }

    /**
     * Get database credentials from Vault
     */
    public DatabaseCredentials getDatabaseCredentials() {
        try {
            VaultSecretsService.DatabaseCredentials vaultCreds = 
                vaultSecretsService.getDatabaseCredentials(serviceName + "-db-role");
            
            // Convert from VaultSecretsService.DatabaseCredentials to local DatabaseCredentials
            return DatabaseCredentials.builder()
                .username(vaultCreds.getUsername())
                .password(vaultCreds.getPassword())
                .leaseId(vaultCreds.getLeaseId())
                .leaseDuration(vaultCreds.getLeaseDuration())
                .renewable(vaultCreds.isRenewable())
                .build();
        } catch (Exception e) {
            log.error("Failed to get database credentials from Vault", e);
            throw new VaultIntegrationException("Unable to retrieve database credentials", e);
        }
    }

    /**
     * Get JWT secret from Vault
     */
    public String getJwtSecret() {
        return getSecret("jwt", "secret");
    }

    /**
     * Get JWT refresh secret from Vault
     */
    public String getJwtRefreshSecret() {
        return getSecret("jwt-refresh", "secret");
    }

    /**
     * Get Redis password from Vault
     */
    public String getRedisPassword() {
        return getSecret("infrastructure/redis", "password");
    }

    /**
     * Get Kafka credentials from Vault
     */
    public Map<String, Object> getKafkaCredentials() {
        return vaultSecretsService.getAllSecrets("infrastructure/kafka");
    }

    /**
     * Get API key for external service
     */
    public String getApiKey(String provider) {
        String path = String.format("api-keys/%s/%s", serviceName, provider);
        return vaultSecretsService.getSecret(path, "api-key");
    }

    /**
     * Get API secret for external service
     */
    public String getApiSecret(String provider) {
        String path = String.format("api-keys/%s/%s", serviceName, provider);
        return vaultSecretsService.getSecret(path, "api-secret");
    }

    /**
     * Get encryption key
     */
    public String getEncryptionKey(String keyName) {
        return vaultSecretsService.getEncryptionKey(serviceName + "-" + keyName);
    }

    /**
     * Encrypt data using service-specific key
     */
    public String encryptData(String data) {
        return vaultSecretsService.encryptData(serviceName + "-encryption", data);
    }

    /**
     * Decrypt data using service-specific key
     */
    public String decryptData(String encryptedData) {
        return vaultSecretsService.decryptData(serviceName + "-encryption", encryptedData);
    }

    /**
     * Get secret from a specific path
     */
    public String getSecret(String pathKey, String secretKey) {
        try {
            String path = cachedPaths.getOrDefault(pathKey, pathKey);
            
            // Add environment prefix if configured
            if (vaultProperties.getEnvironment().isUsePrefix()) {
                path = activeProfile + "/" + path;
            }
            
            return vaultSecretsService.getSecret(path, secretKey);
        } catch (Exception e) {
            log.error("Failed to get secret from path: {}, key: {}", pathKey, secretKey, e);
            throw new VaultIntegrationException("Unable to retrieve secret", e);
        }
    }

    /**
     * Get all secrets from a path
     */
    public Map<String, Object> getAllSecrets(String pathKey) {
        try {
            String path = cachedPaths.getOrDefault(pathKey, pathKey);
            
            // Add environment prefix if configured
            if (vaultProperties.getEnvironment().isUsePrefix()) {
                path = activeProfile + "/" + path;
            }
            
            return vaultSecretsService.getAllSecrets(path);
        } catch (Exception e) {
            log.error("Failed to get secrets from path: {}", pathKey, e);
            throw new VaultIntegrationException("Unable to retrieve secrets", e);
        }
    }

    /**
     * Store secret in Vault
     */
    public void storeSecret(String pathKey, String secretKey, String value) {
        try {
            String path = cachedPaths.getOrDefault(pathKey, pathKey);
            
            // Add environment prefix if configured
            if (vaultProperties.getEnvironment().isUsePrefix()) {
                path = activeProfile + "/" + path;
            }
            
            vaultSecretsService.storeSecret(path, secretKey, value);
        } catch (Exception e) {
            log.error("Failed to store secret at path: {}, key: {}", pathKey, secretKey, e);
            throw new VaultIntegrationException("Unable to store secret", e);
        }
    }
    
    /**
     * Read secret from Vault path (alias for getAllSecrets)
     */
    public Map<String, Object> readSecret(String pathKey) {
        return getAllSecrets(pathKey);
    }
    
    /**
     * Write secret to Vault path (alias for storing multiple secrets)
     */
    public void writeSecret(String pathKey, Map<String, Object> secrets) {
        try {
            String path = cachedPaths.getOrDefault(pathKey, pathKey);
            
            // Add environment prefix if configured
            if (vaultProperties.getEnvironment().isUsePrefix()) {
                path = activeProfile + "/" + path;
            }
            
            // Store each secret in the map
            for (Map.Entry<String, Object> entry : secrets.entrySet()) {
                vaultSecretsService.storeSecret(path, entry.getKey(), entry.getValue().toString());
            }
        } catch (Exception e) {
            log.error("Failed to write secrets to path: {}", pathKey, e);
            throw new VaultIntegrationException("Unable to write secrets", e);
        }
    }

    /**
     * Check if service has proper Vault access
     */
    public boolean isVaultAccessible() {
        try {
            // Try to access a basic path
            vaultSecretsService.getSecret("health/check", "status");
            return true;
        } catch (Exception e) {
            log.debug("Vault accessibility check failed", e);
            return false;
        }
    }

    /**
     * Get service-specific configuration from Vault
     */
    public Map<String, Object> getServiceConfiguration() {
        return getAllSecrets("application");
    }

    /**
     * Get feature flags from Vault
     */
    public boolean isFeatureEnabled(String featureName) {
        try {
            String value = getSecret("features/" + serviceName, featureName);
            return "true".equalsIgnoreCase(value) || "enabled".equalsIgnoreCase(value);
        } catch (Exception e) {
            log.debug("Feature flag {} not found, defaulting to false", featureName);
            return false;
        }
    }

    /**
     * Vault integration exception
     */
    public static class VaultIntegrationException extends RuntimeException {
        public VaultIntegrationException(String message) {
            super(message);
        }

        public VaultIntegrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Database credentials holder
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DatabaseCredentials {
        private String username;
        private String password;
        private String leaseId;
        private Long leaseDuration;
        private boolean renewable;
    }
}