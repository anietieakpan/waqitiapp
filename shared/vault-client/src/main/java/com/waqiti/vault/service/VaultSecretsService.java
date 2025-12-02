package com.waqiti.vault.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vault Secrets Service
 * 
 * Provides secure access to secrets stored in HashiCorp Vault.
 * Implements caching, retry logic, and monitoring for production use.
 */
@Service
public class VaultSecretsService {

    private static final Logger logger = LoggerFactory.getLogger(VaultSecretsService.class);
    
    private final VaultTemplate vaultTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private Counter secretsRetrievedCounter;
    private Counter secretsStoredCounter;
    private Counter secretsErrorCounter;
    private Timer secretsRetrievalTimer;
    
    // In-memory cache for frequently accessed secrets
    private final Map<String, CachedSecret> secretCache = new ConcurrentHashMap<>();
    private final Duration cacheExpiration = Duration.ofMinutes(5);

    public VaultSecretsService(VaultTemplate vaultTemplate, 
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.vaultTemplate = vaultTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    private void initializeMetrics() {
        this.secretsRetrievedCounter = Counter.builder("vault.secrets.retrieved")
                .description("Number of secrets retrieved from Vault")
                .register(meterRegistry);
        
        this.secretsStoredCounter = Counter.builder("vault.secrets.stored")
                .description("Number of secrets stored in Vault")
                .register(meterRegistry);
        
        this.secretsErrorCounter = Counter.builder("vault.secrets.errors")
                .description("Number of Vault secret operation errors")
                .register(meterRegistry);
        
        this.secretsRetrievalTimer = Timer.builder("vault.secrets.retrieval.duration")
                .description("Time taken to retrieve secrets from Vault")
                .register(meterRegistry);
    }

    /**
     * Retrieve a secret from Vault with caching and retry logic
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public String getSecret(String path, String key) {
        return secretsRetrievalTimer.recordCallable(() -> {
            try {
                // Check cache first
                String cacheKey = path + ":" + key;
                CachedSecret cached = secretCache.get(cacheKey);
                
                if (cached != null && !cached.isExpired()) {
                    logger.debug("Retrieved secret from cache: {}", path);
                    return cached.getValue();
                }
                
                // Retrieve from Vault
                VaultKeyValueOperations kvOps = vaultTemplate.opsForKeyValue("secret", 
                    VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);
                
                VaultResponse response = kvOps.get(path);
                
                if (response == null || response.getData() == null) {
                    logger.warn("Secret not found at path: {}", path);
                    secretsErrorCounter.increment("type", "not_found");
                    throw new VaultSecretsException("Secret not found at path: " + path);
                }
                
                Object value = response.getData().get(key);
                if (value == null) {
                    logger.warn("Key '{}' not found in secret at path: {}", key, path);
                    secretsErrorCounter.increment("type", "key_not_found");
                    throw new VaultSecretsException("Key '" + key + "' not found in secret at path: " + path);
                }
                
                String secretValue = value.toString();
                
                // Cache the secret
                secretCache.put(cacheKey, new CachedSecret(secretValue, LocalDateTime.now()));
                
                secretsRetrievedCounter.increment("path", sanitizePath(path));
                logger.debug("Retrieved secret from Vault: {}", path);
                
                return secretValue;
                
            } catch (Exception e) {
                logger.error("Failed to retrieve secret from path: {} key: {}", path, key, e);
                secretsErrorCounter.increment("type", "retrieval_error");
                throw new VaultSecretsException("Failed to retrieve secret", e);
            }
        });
    }

    /**
     * Retrieve all secrets from a path
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public Map<String, Object> getAllSecrets(String path) {
        return secretsRetrievalTimer.recordCallable(() -> {
            try {
                VaultKeyValueOperations kvOps = vaultTemplate.opsForKeyValue("secret", 
                    VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);
                
                VaultResponse response = kvOps.get(path);
                
                if (response == null || response.getData() == null) {
                    logger.warn("No secrets found at path: {}", path);
                    return new HashMap<>();
                }
                
                secretsRetrievedCounter.increment("path", sanitizePath(path));
                logger.debug("Retrieved all secrets from Vault path: {}", path);
                
                return response.getData();
                
            } catch (Exception e) {
                logger.error("Failed to retrieve secrets from path: {}", path, e);
                secretsErrorCounter.increment("type", "retrieval_error");
                throw new VaultSecretsException("Failed to retrieve secrets", e);
            }
        });
    }

    /**
     * Store a secret in Vault
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void storeSecret(String path, String key, String value) {
        try {
            VaultKeyValueOperations kvOps = vaultTemplate.opsForKeyValue("secret", 
                VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);
            
            // Get existing secrets first
            Map<String, Object> existingSecrets = new HashMap<>();
            VaultResponse existing = kvOps.get(path);
            if (existing != null && existing.getData() != null) {
                existingSecrets = existing.getData();
            }
            
            // Add/update the new secret
            existingSecrets.put(key, value);
            
            // Store back to Vault
            kvOps.put(path, existingSecrets);
            
            // Invalidate cache
            String cacheKey = path + ":" + key;
            secretCache.remove(cacheKey);
            
            secretsStoredCounter.increment("path", sanitizePath(path));
            logger.debug("Stored secret in Vault: {}", path);
            
        } catch (Exception e) {
            logger.error("Failed to store secret at path: {} key: {}", path, key, e);
            secretsErrorCounter.increment("type", "storage_error");
            throw new VaultSecretsException("Failed to store secret", e);
        }
    }

    /**
     * Store multiple secrets at once
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void storeSecrets(String path, Map<String, Object> secrets) {
        try {
            VaultKeyValueOperations kvOps = vaultTemplate.opsForKeyValue("secret", 
                VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);
            
            kvOps.put(path, secrets);
            
            // Invalidate related cache entries
            secretCache.entrySet().removeIf(entry -> entry.getKey().startsWith(path + ":"));
            
            secretsStoredCounter.increment("path", sanitizePath(path));
            logger.debug("Stored {} secrets in Vault path: {}", secrets.size(), path);
            
        } catch (Exception e) {
            logger.error("Failed to store secrets at path: {}", path, e);
            secretsErrorCounter.increment("type", "storage_error");
            throw new VaultSecretsException("Failed to store secrets", e);
        }
    }

    /**
     * Delete a secret from Vault
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void deleteSecret(String path) {
        try {
            VaultKeyValueOperations kvOps = vaultTemplate.opsForKeyValue("secret", 
                VaultKeyValueOperationsSupport.KeyValueBackend.KV_2);
            
            kvOps.delete(path);
            
            // Invalidate cache
            secretCache.entrySet().removeIf(entry -> entry.getKey().startsWith(path + ":"));
            
            logger.debug("Deleted secret from Vault: {}", path);
            
        } catch (Exception e) {
            logger.error("Failed to delete secret at path: {}", path, e);
            secretsErrorCounter.increment("type", "deletion_error");
            throw new VaultSecretsException("Failed to delete secret", e);
        }
    }

    /**
     * Get database credentials from Vault
     */
    @Cacheable(value = "databaseCredentials", key = "#role", unless = "#result == null")
    public DatabaseCredentials getDatabaseCredentials(String role) {
        try {
            VaultResponse response = vaultTemplate.read("database/creds/" + role);
            
            if (response == null || response.getData() == null) {
                logger.error("No database credentials found for role: {}", role);
                secretsErrorCounter.increment("type", "db_creds_not_found");
                throw new VaultSecretsException("No database credentials found for role: " + role);
            }
            
            String username = (String) response.getData().get("username");
            String password = (String) response.getData().get("password");
            
            if (username == null || password == null) {
                logger.error("Invalid database credentials format for role: {}", role);
                secretsErrorCounter.increment("type", "db_creds_invalid");
                throw new VaultSecretsException("Invalid database credentials format for role: " + role);
            }
            
            // Parse lease information
            long leaseDuration = response.getLeaseDuration();
            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(leaseDuration);
            
            secretsRetrievedCounter.increment("type", "database_credentials");
            logger.debug("Retrieved database credentials for role: {}", role);
            
            return new DatabaseCredentials(username, password, response.getLeaseId(), expiresAt);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve database credentials for role: {}", role, e);
            secretsErrorCounter.increment("type", "db_creds_error");
            throw new VaultSecretsException("Failed to retrieve database credentials", e);
        }
    }

    /**
     * Renew database credentials lease
     */
    public void renewDatabaseCredentials(String leaseId) {
        try {
            vaultTemplate.write("sys/leases/renew", Map.of("lease_id", leaseId, "increment", "3600"));
            logger.debug("Renewed database credentials lease: {}", leaseId);
            
        } catch (Exception e) {
            logger.error("Failed to renew database credentials lease: {}", leaseId, e);
            secretsErrorCounter.increment("type", "lease_renewal_error");
            throw new VaultSecretsException("Failed to renew lease", e);
        }
    }

    /**
     * Get encryption key for data encryption
     */
    public String getEncryptionKey(String keyName) {
        try {
            VaultResponse response = vaultTemplate.write("transit/datakey/plaintext/" + keyName, null);
            
            if (response == null || response.getData() == null) {
                logger.error("No encryption key found: {}", keyName);
                secretsErrorCounter.increment("type", "encryption_key_not_found");
                throw new VaultSecretsException("No encryption key found: " + keyName);
            }
            
            String plaintext = (String) response.getData().get("plaintext");
            secretsRetrievedCounter.increment("type", "encryption_key");
            logger.debug("Retrieved encryption key: {}", keyName);
            
            return plaintext;
            
        } catch (Exception e) {
            logger.error("Failed to retrieve encryption key: {}", keyName, e);
            secretsErrorCounter.increment("type", "encryption_key_error");
            throw new VaultSecretsException("Failed to retrieve encryption key", e);
        }
    }

    /**
     * Encrypt data using Vault transit engine
     */
    public String encryptData(String keyName, String plaintext) {
        try {
            Map<String, Object> request = Map.of("plaintext", java.util.Base64.getEncoder().encodeToString(plaintext.getBytes()));
            VaultResponse response = vaultTemplate.write("transit/encrypt/" + keyName, request);
            
            if (response == null || response.getData() == null) {
                logger.error("Failed to encrypt data with key: {}", keyName);
                secretsErrorCounter.increment("type", "encryption_failed");
                throw new VaultSecretsException("Failed to encrypt data with key: " + keyName);
            }
            
            String ciphertext = (String) response.getData().get("ciphertext");
            logger.debug("Encrypted data with key: {}", keyName);
            
            return ciphertext;
            
        } catch (Exception e) {
            logger.error("Failed to encrypt data with key: {}", keyName, e);
            secretsErrorCounter.increment("type", "encryption_error");
            throw new VaultSecretsException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt data using Vault transit engine
     */
    public String decryptData(String keyName, String ciphertext) {
        try {
            Map<String, Object> request = Map.of("ciphertext", ciphertext);
            VaultResponse response = vaultTemplate.write("transit/decrypt/" + keyName, request);
            
            if (response == null || response.getData() == null) {
                logger.error("Failed to decrypt data with key: {}", keyName);
                secretsErrorCounter.increment("type", "decryption_failed");
                throw new VaultSecretsException("Failed to decrypt data with key: " + keyName);
            }
            
            String plaintext = (String) response.getData().get("plaintext");
            byte[] decoded = java.util.Base64.getDecoder().decode(plaintext);
            
            logger.debug("Decrypted data with key: {}", keyName);
            return new String(decoded);
            
        } catch (Exception e) {
            logger.error("Failed to decrypt data with key: {}", keyName, e);
            secretsErrorCounter.increment("type", "decryption_error");
            throw new VaultSecretsException("Failed to decrypt data", e);
        }
    }

    /**
     * Clear expired entries from cache
     */
    public void clearExpiredCache() {
        secretCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        logger.debug("Cleared expired cache entries");
    }

    /**
     * Get cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        long totalEntries = secretCache.size();
        long expiredEntries = secretCache.values().stream()
                .mapToLong(secret -> secret.isExpired() ? 1 : 0)
                .sum();
        
        return new CacheStatistics(totalEntries, expiredEntries);
    }

    // Helper methods

    private String sanitizePath(String path) {
        // Remove sensitive information from metrics
        return path.replaceAll("/[^/]+$", "/***");
    }

    // Inner classes

    private static class CachedSecret {
        private final String value;
        private final LocalDateTime cachedAt;

        public CachedSecret(String value, LocalDateTime cachedAt) {
            this.value = value;
            this.cachedAt = cachedAt;
        }

        public String getValue() {
            return value;
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(cachedAt.plus(Duration.ofMinutes(5)));
        }
    }

    public static class DatabaseCredentials {
        private final String username;
        private final String password;
        private final String leaseId;
        private final LocalDateTime expiresAt;

        public DatabaseCredentials(String username, String password, String leaseId, LocalDateTime expiresAt) {
            this.username = username;
            this.password = password;
            this.leaseId = leaseId;
            this.expiresAt = expiresAt;
        }

        // Getters
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getLeaseId() { return leaseId; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt.minusMinutes(5)); // Renew 5 minutes before expiry
        }
    }

    public static class CacheStatistics {
        private final long totalEntries;
        private final long expiredEntries;

        public CacheStatistics(long totalEntries, long expiredEntries) {
            this.totalEntries = totalEntries;
            this.expiredEntries = expiredEntries;
        }

        // Getters
        public long getTotalEntries() { return totalEntries; }
        public long getExpiredEntries() { return expiredEntries; }
        public long getActiveEntries() { return totalEntries - expiredEntries; }
    }

    public static class VaultSecretsException extends RuntimeException {
        public VaultSecretsException(String message) {
            super(message);
        }

        public VaultSecretsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}