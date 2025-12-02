package com.waqiti.common.vault;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise Vault Service for secure secret management
 * Provides secure storage and retrieval of sensitive data using HashiCorp Vault
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VaultService {
    
    private final VaultTemplate vaultTemplate;
    
    @Value("${waqiti.vault.kv-path:secret}")
    private String kvPath;
    
    @Value("${waqiti.vault.encryption-key-path:encryption-keys}")
    private String encryptionKeyPath;
    
    @Value("${waqiti.vault.database-path:database}")
    private String databasePath;
    
    @Value("${waqiti.vault.api-key-path:api-keys}")
    private String apiKeyPath;
    
    // Cache for frequently accessed secrets (with expiration)
    private final Map<String, CachedSecret> secretCache = new ConcurrentHashMap<>();
    
    /**
     * Store a secret in Vault
     */
    public void storeSecret(String path, Map<String, Object> secret) {
        try {
            String fullPath = kvPath + "/" + path;
            vaultTemplate.write(fullPath, secret);
            
            // Invalidate cache for this path
            secretCache.remove(path);
            
            log.info("Successfully stored secret at path: {}", path);
            
        } catch (Exception e) {
            log.error("Failed to store secret at path: {}", path, e);
            throw new VaultException("Failed to store secret", e);
        }
    }
    
    /**
     * Retrieve a secret from Vault
     */
    public Map<String, Object> getSecret(String path) {
        try {
            // Check cache first
            CachedSecret cached = secretCache.get(path);
            if (cached != null && !cached.isExpired()) {
                log.debug("Retrieved secret from cache: {}", path);
                return cached.getData();
            }
            
            String fullPath = kvPath + "/" + path;
            VaultResponse response = vaultTemplate.read(fullPath);
            
            if (response == null || response.getData() == null) {
                log.warn("Secret not found at path: {}", path);
                return null;
            }
            
            Map<String, Object> secretData = response.getData();
            
            // Cache the secret for 5 minutes
            secretCache.put(path, new CachedSecret(secretData, LocalDateTime.now().plusMinutes(5)));
            
            log.debug("Successfully retrieved secret from vault: {}", path);
            return secretData;
            
        } catch (Exception e) {
            log.error("Failed to retrieve secret at path: {}", path, e);
            throw new VaultException("Failed to retrieve secret", e);
        }
    }
    
    /**
     * Delete a secret from Vault
     */
    public void deleteSecret(String path) {
        try {
            String fullPath = kvPath + "/" + path;
            vaultTemplate.delete(fullPath);
            
            // Remove from cache
            secretCache.remove(path);
            
            log.info("Successfully deleted secret at path: {}", path);
            
        } catch (Exception e) {
            log.error("Failed to delete secret at path: {}", path, e);
            throw new VaultException("Failed to delete secret", e);
        }
    }
    
    /**
     * Store encryption key
     */
    public void storeEncryptionKey(String keyId, String keyValue, Map<String, Object> metadata) {
        Map<String, Object> keyData = Map.of(
            "key", keyValue,
            "createdAt", LocalDateTime.now().toString(),
            "metadata", metadata != null ? metadata : Map.of()
        );
        
        storeSecret(encryptionKeyPath + "/" + keyId, keyData);
        log.info("Stored encryption key: {}", keyId);
    }
    
    /**
     * Retrieve encryption key
     */
    public String getEncryptionKey(String keyId) {
        Map<String, Object> keyData = getSecret(encryptionKeyPath + "/" + keyId);
        if (keyData == null) {
            throw new VaultException("Encryption key not found: " + keyId);
        }
        
        return (String) keyData.get("key");
    }
    
    /**
     * Store database credentials
     */
    public void storeDatabaseCredentials(String dbName, String username, String password, Map<String, Object> config) {
        Map<String, Object> dbData = Map.of(
            "username", username,
            "password", password,
            "config", config != null ? config : Map.of(),
            "createdAt", LocalDateTime.now().toString()
        );
        
        storeSecret(databasePath + "/" + dbName, dbData);
        log.info("Stored database credentials for: {}", dbName);
    }
    
    /**
     * Get database credentials
     */
    public DatabaseCredentials getDatabaseCredentials(String dbName) {
        Map<String, Object> dbData = getSecret(databasePath + "/" + dbName);
        if (dbData == null) {
            throw new VaultException("Database credentials not found: " + dbName);
        }
        
        return DatabaseCredentials.builder()
            .username((String) dbData.get("username"))
            .password((String) dbData.get("password"))
            .config((Map<String, Object>) dbData.get("config"))
            .build();
    }
    
    /**
     * Store API key
     */
    public void storeApiKey(String apiName, String apiKey, Map<String, Object> metadata) {
        Map<String, Object> apiData = Map.of(
            "key", apiKey,
            "createdAt", LocalDateTime.now().toString(),
            "metadata", metadata != null ? metadata : Map.of()
        );
        
        storeSecret(apiKeyPath + "/" + apiName, apiData);
        log.info("Stored API key for: {}", apiName);
    }
    
    /**
     * Get API key
     */
    public String getApiKey(String apiName) {
        Map<String, Object> apiData = getSecret(apiKeyPath + "/" + apiName);
        if (apiData == null) {
            throw new VaultException("API key not found: " + apiName);
        }
        
        return (String) apiData.get("key");
    }
    
    /**
     * Check if secret exists
     */
    public boolean secretExists(String path) {
        try {
            Map<String, Object> secret = getSecret(path);
            return secret != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Rotate secret by generating new value
     */
    public void rotateSecret(String path, Map<String, Object> newSecret) {
        // Store old secret in backup location
        Map<String, Object> oldSecret = getSecret(path);
        if (oldSecret != null) {
            storeSecret(path + ".backup." + System.currentTimeMillis(), oldSecret);
        }
        
        // Store new secret
        storeSecret(path, newSecret);
        
        log.info("Rotated secret at path: {}", path);
    }
    
    /**
     * Clear secret cache
     */
    public void clearCache() {
        secretCache.clear();
        log.info("Cleared vault secret cache");
    }
    
    /**
     * Write secret (alias for storeSecret)
     */
    public void write(String path, Map<String, Object> secret) {
        storeSecret(path, secret);
    }
    
    /**
     * Read secret (alias for getSecret)
     */
    public Map<String, Object> read(String path) {
        return getSecret(path);
    }
    
    /**
     * List secrets at a path
     */
    public List<String> list(String path) {
        try {
            String fullPath = kvPath + "/" + path;
            VaultResponse response = vaultTemplate.read(fullPath);
            if (response != null && response.getData() != null) {
                Object keys = response.getData().get("keys");
                if (keys instanceof List) {
                    return (List<String>) keys;
                }
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to list secrets at path: {}", path, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Get cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        int totalEntries = secretCache.size();
        long expiredEntries = secretCache.values().stream()
            .mapToLong(cached -> cached.isExpired() ? 1 : 0)
            .sum();
        
        return CacheStatistics.builder()
            .totalEntries(totalEntries)
            .expiredEntries(expiredEntries)
            .validEntries(totalEntries - expiredEntries)
            .build();
    }
    
    /**
     * Cached secret data
     */
    private static class CachedSecret {
        private final Map<String, Object> data;
        private final LocalDateTime expiresAt;
        
        public CachedSecret(Map<String, Object> data, LocalDateTime expiresAt) {
            this.data = data;
            this.expiresAt = expiresAt;
        }
        
        public Map<String, Object> getData() {
            return data;
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }
    
    /**
     * Database credentials model
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DatabaseCredentials {
        private String username;
        private String password;
        private Map<String, Object> config;
    }
    
    /**
     * Cache statistics model
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CacheStatistics {
        private int totalEntries;
        private long expiredEntries;
        private long validEntries;
    }
    
    /**
     * Vault exception
     */
    public static class VaultException extends RuntimeException {
        public VaultException(String message) {
            super(message);
        }
        
        public VaultException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}