package com.waqiti.common.security;

import com.waqiti.common.config.VaultConfig;
import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultException;
import io.github.jopenlibs.vault.response.LogicalResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized secret management using HashiCorp Vault
 * Handles secure retrieval and caching of secrets
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VaultSecretManager {
    
    private final VaultConfig vaultConfig;
    private Vault vault;
    private final Map<String, String> secretCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        try {
            this.vault = vaultConfig.createVaultClient();
            log.info("Successfully initialized Vault client");
        } catch (VaultException e) {
            log.error("Failed to initialize Vault client", e);
            throw new SecurityException("Vault initialization failed", e);
        }
    }
    
    /**
     * Retrieve a secret from Vault with caching
     */
    @Cacheable(value = "secrets", key = "#path + ':' + #key")
    public String getSecret(String path, String key) {
        String cacheKey = path + ":" + key;
        
        // Check cache first
        if (secretCache.containsKey(cacheKey)) {
            return secretCache.get(cacheKey);
        }
        
        try {
            LogicalResponse response = vault.logical().read(path);
            Map<String, String> data = response.getData();
            
            if (data != null && data.containsKey(key)) {
                String secret = data.get(key);
                secretCache.put(cacheKey, secret);
                return secret;
            }
            
            throw new SecurityException("Secret not found: " + cacheKey);
        } catch (VaultException e) {
            log.error("Failed to retrieve secret from Vault: {}", cacheKey, e);
            throw new SecurityException("Failed to retrieve secret", e);
        }
    }
    
    /**
     * Get database password for a service
     */
    public String getDatabasePassword(String service) {
        return getSecret("secret/data/database/" + service, "password");
    }
    
    /**
     * Get JWT secret for a service
     */
    public String getJwtSecret(String service) {
        return getSecret("secret/data/jwt/" + service, "secret");
    }
    
    /**
     * Get API key for external service
     */
    public String getApiKey(String service) {
        return getSecret("secret/data/api-keys/" + service, "key");
    }
    
    /**
     * Get Redis password
     */
    public String getRedisPassword() {
        return getSecret("secret/data/redis", "password");
    }
    
    /**
     * Get Kafka credentials
     */
    public Map<String, String> getKafkaCredentials() {
        try {
            LogicalResponse response = vault.logical().read("secret/data/kafka");
            return response.getData();
        } catch (VaultException e) {
            log.error("Failed to retrieve Kafka credentials", e);
            throw new SecurityException("Failed to retrieve Kafka credentials", e);
        }
    }
    
    /**
     * Get encryption key for a service
     */
    public String getEncryptionKey(String service) {
        return getSecret("secret/data/encryption/" + service, "key");
    }
    
    /**
     * Rotate a secret in Vault
     */
    public void rotateSecret(String path, String key, String newValue) {
        try {
            Map<String, Object> data = Map.of(key, newValue);
            vault.logical().write(path, data);
            
            // Clear from cache
            String cacheKey = path + ":" + key;
            secretCache.remove(cacheKey);
            
            log.info("Successfully rotated secret: {}", cacheKey);
        } catch (VaultException e) {
            log.error("Failed to rotate secret", e);
            throw new SecurityException("Failed to rotate secret", e);
        }
    }
    
    /**
     * Clear secret cache
     */
    public void clearCache() {
        secretCache.clear();
        log.info("Secret cache cleared");
    }
}