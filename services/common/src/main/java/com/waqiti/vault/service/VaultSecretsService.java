package com.waqiti.vault.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultHealth;

import java.util.HashMap;
import java.util.Map;

/**
 * Core Vault operations service
 * 
 * Provides centralized access to HashiCorp Vault for secret management,
 * encryption, and dynamic credential generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = "spring.cloud.vault.enabled", havingValue = "true", matchIfMissing = true)
public class VaultSecretsService {

    private final VaultTemplate vaultTemplate;
    
    // Cache configuration
    private final Map<String, CachedSecret> secretCache = new HashMap<>();
    private boolean cacheEnabled = true;
    private long cacheTtlSeconds = 300; // 5 minutes default
    
    /**
     * Get a specific secret from Vault
     */
    public String getSecret(String path, String key) {
        try {
            VaultResponse response = vaultTemplate.read(path);
            if (response != null && response.getData() != null) {
                Object value = response.getData().get(key);
                if (value != null) {
                    return value.toString();
                }
                log.error("Secret key '{}' not found at Vault path: {}", key, path);
                throw new VaultSecretsException("Secret key not found: " + key + " at path: " + path);
            }
            log.error("No data found at Vault path: {}", path);
            throw new VaultSecretsException("No data found at Vault path: " + path);
        } catch (VaultSecretsException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read secret from Vault path: {}, key: {}", path, key, e);
            throw new VaultSecretsException("Unable to retrieve secret from Vault", e);
        }
    }

    /**
     * Get all secrets from a Vault path
     */
    public Map<String, Object> getAllSecrets(String path) {
        try {
            VaultResponse response = vaultTemplate.read(path);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
            log.warn("No data found at Vault path: {}", path);
            return new HashMap<>();
        } catch (Exception e) {
            log.error("Failed to read secrets from Vault path: {}", path, e);
            throw new VaultSecretsException("Unable to retrieve secrets from Vault", e);
        }
    }

    /**
     * Store a secret in Vault
     */
    public void storeSecret(String path, String key, String value) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put(key, value);
            vaultTemplate.write(path, data);
            log.debug("Successfully stored secret at path: {}, key: {}", path, key);
        } catch (Exception e) {
            log.error("Failed to store secret in Vault path: {}, key: {}", path, key, e);
            throw new VaultSecretsException("Unable to store secret in Vault", e);
        }
    }

    /**
     * Store multiple secrets in Vault
     */
    public void storeSecrets(String path, Map<String, Object> secrets) {
        try {
            vaultTemplate.write(path, secrets);
            log.debug("Successfully stored {} secrets at path: {}", secrets.size(), path);
        } catch (Exception e) {
            log.error("Failed to store secrets in Vault path: {}", path, e);
            throw new VaultSecretsException("Unable to store secrets in Vault", e);
        }
    }

    /**
     * Delete a secret from Vault
     */
    public void deleteSecret(String path) {
        try {
            vaultTemplate.delete(path);
            log.debug("Successfully deleted secret at path: {}", path);
        } catch (Exception e) {
            log.error("Failed to delete secret from Vault path: {}", path, e);
            throw new VaultSecretsException("Unable to delete secret from Vault", e);
        }
    }

    /**
     * Get database credentials from Vault (dynamic secrets)
     */
    public DatabaseCredentials getDatabaseCredentials(String role) {
        try {
            String path = "database/creds/" + role;
            VaultResponse response = vaultTemplate.read(path);
            
            if (response != null && response.getData() != null) {
                String username = (String) response.getData().get("username");
                String password = (String) response.getData().get("password");
                String leaseId = response.getLeaseId();
                Long leaseDuration = response.getLeaseDuration();
                boolean renewable = response.isRenewable();
                
                if (username != null && password != null) {
                    return DatabaseCredentials.builder()
                        .username(username)
                        .password(password)
                        .leaseId(leaseId)
                        .leaseDuration(leaseDuration)
                        .renewable(renewable)
                        .build();
                }
            }
            
            throw new VaultSecretsException("Invalid database credentials response from Vault");
        } catch (Exception e) {
            log.error("Failed to get database credentials for role: {}", role, e);
            throw new VaultSecretsException("Unable to retrieve database credentials", e);
        }
    }

    /**
     * Get encryption key from Vault
     */
    public String getEncryptionKey(String keyName) {
        try {
            String path = "transit/keys/" + keyName;
            VaultResponse response = vaultTemplate.read(path);
            
            if (response != null && response.getData() != null) {
                return (String) response.getData().get("key");
            }
            
            // If key doesn't exist, create it
            createEncryptionKey(keyName);
            return getEncryptionKey(keyName);
        } catch (Exception e) {
            log.error("Failed to get encryption key: {}", keyName, e);
            throw new VaultSecretsException("Unable to retrieve encryption key", e);
        }
    }

    /**
     * Create a new encryption key in Vault
     */
    public void createEncryptionKey(String keyName) {
        try {
            String path = "transit/keys/" + keyName;
            Map<String, Object> keyConfig = new HashMap<>();
            keyConfig.put("type", "aes256-gcm96");
            keyConfig.put("exportable", false);
            
            vaultTemplate.write(path, keyConfig);
            log.info("Created new encryption key: {}", keyName);
        } catch (Exception e) {
            log.error("Failed to create encryption key: {}", keyName, e);
            throw new VaultSecretsException("Unable to create encryption key", e);
        }
    }

    /**
     * Encrypt data using Vault Transit engine
     */
    public String encryptData(String keyName, String plaintext) {
        try {
            String path = "transit/encrypt/" + keyName;
            Map<String, Object> request = new HashMap<>();
            request.put("plaintext", java.util.Base64.getEncoder().encodeToString(plaintext.getBytes()));
            
            VaultResponse response = vaultTemplate.write(path, request);
            
            if (response != null && response.getData() != null) {
                return (String) response.getData().get("ciphertext");
            }
            
            throw new VaultSecretsException("Invalid encryption response from Vault");
        } catch (Exception e) {
            log.error("Failed to encrypt data with key: {}", keyName, e);
            throw new VaultSecretsException("Unable to encrypt data", e);
        }
    }

    /**
     * Decrypt data using Vault Transit engine
     */
    public String decryptData(String keyName, String ciphertext) {
        try {
            String path = "transit/decrypt/" + keyName;
            Map<String, Object> request = new HashMap<>();
            request.put("ciphertext", ciphertext);
            
            VaultResponse response = vaultTemplate.write(path, request);
            
            if (response != null && response.getData() != null) {
                String base64Plaintext = (String) response.getData().get("plaintext");
                return new String(java.util.Base64.getDecoder().decode(base64Plaintext));
            }
            
            throw new VaultSecretsException("Invalid decryption response from Vault");
        } catch (Exception e) {
            log.error("Failed to decrypt data with key: {}", keyName, e);
            throw new VaultSecretsException("Unable to decrypt data", e);
        }
    }

    /**
     * Rotate an encryption key
     */
    public void rotateEncryptionKey(String keyName) {
        try {
            String path = "transit/keys/" + keyName + "/rotate";
            vaultTemplate.write(path, new HashMap<>());
            log.info("Successfully rotated encryption key: {}", keyName);
        } catch (Exception e) {
            log.error("Failed to rotate encryption key: {}", keyName, e);
            throw new VaultSecretsException("Unable to rotate encryption key", e);
        }
    }

    /**
     * Check if Vault is accessible and healthy
     */
    public boolean isVaultHealthy() {
        try {
            vaultTemplate.opsForSys().health();
            return true;
        } catch (Exception e) {
            log.debug("Vault health check failed", e);
            return false;
        }
    }

    /**
     * Get Vault status information
     */
    public Map<String, Object> getVaultStatus() {
        try {
            VaultHealth health = vaultTemplate.opsForSys().health();
            Map<String, Object> status = new HashMap<>();
            status.put("initialized", health.isInitialized());
            status.put("sealed", health.isSealed());
            status.put("standby", health.isStandby());
            status.put("performanceStandby", health.isPerformanceStandby());
            // These methods may not be available in all Spring Vault versions
            // status.put("replicationPerformanceMode", health.isReplicationPerformanceMode());
            // status.put("replicationDrMode", health.isReplicationDrMode());
            status.put("serverTimeUtc", health.getServerTimeUtc());
            status.put("version", health.getVersion());
            return status;
        } catch (Exception e) {
            log.error("Failed to get Vault status", e);
            throw new VaultSecretsException("Unable to retrieve Vault status", e);
        }
    }

    /**
     * Get cache statistics
     */
    public Map<String, Object> getCacheStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", secretCache.size());
        stats.put("cacheEnabled", cacheEnabled);
        stats.put("cacheTtlSeconds", cacheTtlSeconds);
        return stats;
    }
    
    /**
     * List all secret engines
     */
    public Map<String, Object> listSecretEngines() {
        try {
            VaultResponse response = vaultTemplate.read("sys/mounts");
            if (response != null && response.getData() != null) {
                return response.getData();
            }
            return new HashMap<>();
        } catch (Exception e) {
            log.error("Failed to list secret engines", e);
            throw new VaultSecretsException("Unable to list secret engines", e);
        }
    }

    /**
     * Custom exception for Vault operations
     */
    public static class VaultSecretsException extends RuntimeException {
        public VaultSecretsException(String message) {
            super(message);
        }

        public VaultSecretsException(String message, Throwable cause) {
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
        
        @Override
        public String toString() {
            return "DatabaseCredentials{username='" + username + "'}"; // Don't log password
        }
    }
    
    /**
     * Cached secret holder
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class CachedSecret {
        private final Object value;
        private final long expiryTime;
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }
}