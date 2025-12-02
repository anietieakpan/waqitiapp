package com.waqiti.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SecretService {

    @Autowired(required = false)
    private VaultTemplate vaultTemplate;

    @Value("${vault.enabled:false}")
    private boolean vaultEnabled;

    @Value("${vault.secret-path:secret/}")
    private String secretPath;

    // Cache for secrets (in production, use Redis or similar)
    private final Map<String, String> secretCache = new ConcurrentHashMap<>();

    /**
     * Retrieves a secret from HashiCorp Vault or falls back to environment variables
     * 
     * @param secretKey The key for the secret
     * @param fallbackEnvVar The environment variable to use as fallback
     * @return The secret value
     */
    public String getSecret(String secretKey, String fallbackEnvVar) {
        // Check cache first
        String cachedValue = secretCache.get(secretKey);
        if (cachedValue != null) {
            return cachedValue;
        }

        if (vaultEnabled && vaultTemplate != null) {
            try {
                return getSecretFromVault(secretKey)
                    .orElseGet(() -> getFallbackSecret(secretKey, fallbackEnvVar));
            } catch (Exception e) {
                log.warn("Failed to retrieve secret '{}' from Vault, falling back to environment variable", 
                         secretKey, e);
                return getFallbackSecret(secretKey, fallbackEnvVar);
            }
        } else {
            return getFallbackSecret(secretKey, fallbackEnvVar);
        }
    }

    /**
     * Retrieves JWT secret for token signing
     */
    public String getJwtSecret() {
        return getSecret("jwt-secret", "JWT_SECRET");
    }

    /**
     * Retrieves database password
     */
    public String getDatabasePassword(String serviceName) {
        return getSecret("database/" + serviceName + "/password", 
                        serviceName.toUpperCase() + "_DB_PASSWORD");
    }

    /**
     * Retrieves Redis password
     */
    public String getRedisPassword() {
        return getSecret("redis/password", "REDIS_PASSWORD");
    }

    /**
     * Retrieves Kafka credentials
     */
    public String getKafkaPassword() {
        return getSecret("kafka/password", "KAFKA_PASSWORD");
    }

    /**
     * Retrieves external service API keys
     */
    public String getApiKey(String serviceName) {
        return getSecret("api-keys/" + serviceName, 
                        serviceName.toUpperCase() + "_API_KEY");
    }

    /**
     * Retrieves Cyclos credentials
     */
    public String getCyclosPassword() {
        return getSecret("cyclos/admin-password", "CYCLOS_ADMIN_PASSWORD");
    }

    /**
     * Stores a secret in Vault (for runtime secret updates)
     */
    public void storeSecret(String secretKey, String secretValue) {
        if (vaultEnabled && vaultTemplate != null) {
            try {
                Map<String, Object> data = Map.of("value", secretValue);
                vaultTemplate.write(secretPath + secretKey, data);
                secretCache.put(secretKey, secretValue);
                log.info("Successfully stored secret: {}", secretKey);
            } catch (Exception e) {
                log.error("Failed to store secret '{}' in Vault", secretKey, e);
                throw new RuntimeException("Failed to store secret in Vault", e);
            }
        } else {
            log.warn("Vault not enabled, cannot store secret: {}", secretKey);
        }
    }

    /**
     * Invalidates secret cache (useful for secret rotation)
     */
    public void invalidateCache(String secretKey) {
        secretCache.remove(secretKey);
        log.info("Invalidated cache for secret: {}", secretKey);
    }

    /**
     * Invalidates all cached secrets
     */
    public void invalidateAllCache() {
        secretCache.clear();
        log.info("Invalidated all secret cache");
    }

    private Optional<String> getSecretFromVault(String secretKey) {
        try {
            VaultResponse response = vaultTemplate.read(secretPath + secretKey);
            if (response != null && response.getData() != null) {
                Object value = response.getData().get("value");
                if (value != null) {
                    String secretValue = value.toString();
                    secretCache.put(secretKey, secretValue);
                    return Optional.of(secretValue);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error retrieving secret '{}' from Vault", secretKey, e);
            return Optional.empty();
        }
    }

    private String getFallbackSecret(String secretKey, String fallbackEnvVar) {
        String fallbackValue = System.getenv(fallbackEnvVar);
        if (fallbackValue == null) {
            log.warn("No secret found for '{}' in Vault or environment variable '{}'", 
                     secretKey, fallbackEnvVar);
            // Return a default development value for non-production environments
            return getDefaultDevelopmentSecret(secretKey);
        }
        secretCache.put(secretKey, fallbackValue);
        return fallbackValue;
    }

    private String getDefaultDevelopmentSecret(String secretKey) {
        // Only provide defaults for development - never in production
        String profile = System.getProperty("spring.profiles.active", "dev");
        if ("dev".equals(profile) || "test".equals(profile)) {
            switch (secretKey) {
                case "jwt-secret":
                    return "dev-jwt-secret-not-for-production";
                case "redis/password":
                    return "";
                case "kafka/password":
                    return "";
                default:
                    return "dev-secret-" + secretKey;
            }
        }
        throw new RuntimeException("Secret '" + secretKey + "' not found and no fallback available");
    }
}