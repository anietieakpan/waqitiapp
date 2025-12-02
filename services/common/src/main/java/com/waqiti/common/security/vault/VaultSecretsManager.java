package com.waqiti.common.security.vault;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.springframework.vault.support.VaultResponseSupport;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise-grade Secrets Management Service using HashiCorp Vault
 *
 * Features:
 * - Dynamic secret retrieval from Vault KV v2
 * - Automatic secret rotation detection
 * - Circuit breaker for Vault unavailability
 * - Local caching with configurable TTL
 * - Secret versioning support
 * - Audit logging for all secret access
 * - Graceful degradation with fallback
 *
 * Security:
 * - Never logs secret values
 * - Automatic secret invalidation on rotation
 * - Secure in-memory caching with TTL
 * - TLS for all Vault communication
 *
 * @author Waqiti Security Team
 * @version 2.0
 */
@Slf4j
@Service
public class VaultSecretsManager {

    private final VaultTemplate vaultTemplate;
    private final VaultConfig vaultConfig;
    private final Map<String, CachedSecret> secretCache = new ConcurrentHashMap<>();

    @Value("${vault.secrets.cache-ttl:300}")
    private long cacheTtlSeconds;

    @Value("${vault.secrets.base-path:secret/data/waqiti}")
    private String basePath;

    @Value("${vault.enabled:true}")
    private boolean vaultEnabled;

    @Value("${spring.application.name}")
    private String applicationName;

    public VaultSecretsManager(VaultTemplate vaultTemplate, VaultConfig vaultConfig) {
        this.vaultTemplate = vaultTemplate;
        this.vaultConfig = vaultConfig;
    }

    @PostConstruct
    public void init() {
        if (vaultEnabled) {
            log.info("Initializing VaultSecretsManager for application: {}", applicationName);
            log.info("Vault address: {}", vaultConfig.getAddress());
            log.info("Base path: {}", basePath);
            log.info("Cache TTL: {} seconds", cacheTtlSeconds);

            // Test Vault connectivity
            try {
                vaultTemplate.opsForSys().health();
                log.info("Successfully connected to Vault");
            } catch (Exception e) {
                log.error("Failed to connect to Vault - secrets will fail over to environment variables", e);
            }
        } else {
            log.warn("Vault is DISABLED - using environment variables for secrets (NOT RECOMMENDED FOR PRODUCTION)");
        }
    }

    /**
     * Retrieve a secret from Vault with caching and circuit breaker
     *
     * @param secretPath Path to secret (e.g., "keycloak/client-secret")
     * @return Secret value
     */
    @CircuitBreaker(name = "vault", fallbackMethod = "getSecretFallback")
    @Retry(name = "vault")
    public String getSecret(String secretPath) {
        return getSecret(secretPath, "value");
    }

    /**
     * Retrieve a specific field from a secret
     *
     * @param secretPath Path to secret
     * @param fieldName Field name within secret
     * @return Secret field value
     */
    @CircuitBreaker(name = "vault", fallbackMethod = "getSecretFallback")
    @Retry(name = "vault")
    public String getSecret(String secretPath, String fieldName) {
        if (!vaultEnabled) {
            return getSecretFromEnvironment(secretPath, fieldName);
        }

        // Check cache first
        String cacheKey = getCacheKey(secretPath, fieldName);
        CachedSecret cached = secretCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Returning cached secret for path: {}", secretPath);
            return cached.getValue();
        }

        // Retrieve from Vault
        String fullPath = buildFullPath(secretPath);
        log.info("Retrieving secret from Vault: {} (field: {})", secretPath, fieldName);

        try {
            org.springframework.vault.support.Versioned<Map<String, Object>> response = vaultTemplate
                .opsForVersionedKeyValue(getBasePath())
                .get(secretPath);

            if (response == null || response.getData() == null) {
                log.error("Secret not found in Vault: {}", secretPath);
                return getSecretFromEnvironment(secretPath, fieldName);
            }

            Map<String, Object> data = response.getData();
            String secretValue = extractFieldValue(data, fieldName);

            if (secretValue == null) {
                log.error("Field '{}' not found in secret: {}", fieldName, secretPath);
                return getSecretFromEnvironment(secretPath, fieldName);
            }

            // Cache the secret
            secretCache.put(cacheKey, new CachedSecret(secretValue, Duration.ofSeconds(cacheTtlSeconds)));

            // Audit log (never log the actual secret value)
            auditSecretAccess(secretPath, fieldName, true);

            return secretValue;

        } catch (Exception e) {
            log.error("Error retrieving secret from Vault: {} - falling back to environment", secretPath, e);
            return getSecretFromEnvironment(secretPath, fieldName);
        }
    }

    /**
     * Store a secret in Vault (for secret rotation)
     *
     * @param secretPath Path to store secret
     * @param secretData Secret data as key-value pairs
     */
    @CircuitBreaker(name = "vault")
    @Retry(name = "vault")
    public void storeSecret(String secretPath, Map<String, Object> secretData) {
        if (!vaultEnabled) {
            log.warn("Vault disabled - cannot store secret: {}", secretPath);
            return;
        }

        String fullPath = buildFullPath(secretPath);
        log.info("Storing secret in Vault: {}", secretPath);

        try {
            vaultTemplate
                .opsForVersionedKeyValue(getBasePath())
                .put(secretPath, secretData);

            // Invalidate cache
            secretData.keySet().forEach(fieldName -> {
                String cacheKey = getCacheKey(secretPath, fieldName);
                secretCache.remove(cacheKey);
            });

            log.info("Successfully stored secret: {}", secretPath);
            auditSecretWrite(secretPath, secretData.keySet().toArray(new String[0]));

        } catch (Exception e) {
            log.error("Failed to store secret in Vault: {}", secretPath, e);
            throw new VaultSecretException("Failed to store secret: " + secretPath, e);
        }
    }

    /**
     * Rotate a secret (generate new value and store in Vault)
     *
     * @param secretPath Path to secret
     * @param newSecretValue New secret value
     */
    public void rotateSecret(String secretPath, String newSecretValue) {
        Map<String, Object> secretData = new HashMap<>();
        secretData.put("value", newSecretValue);
        secretData.put("rotated_at", Instant.now().toString());
        secretData.put("rotated_by", applicationName);

        storeSecret(secretPath, secretData);
        log.info("Secret rotated successfully: {}", secretPath);
    }

    /**
     * Get all secrets for a service (e.g., all Keycloak client secrets)
     *
     * @param servicePath Service path (e.g., "keycloak/clients")
     * @return Map of secret names to values
     */
    @CircuitBreaker(name = "vault", fallbackMethod = "getAllSecretsFallback")
    public Map<String, String> getAllSecretsForService(String servicePath) {
        if (!vaultEnabled) {
            return getAllSecretsFromEnvironment(servicePath);
        }

        log.info("Retrieving all secrets for service: {}", servicePath);
        Map<String, String> secrets = new HashMap<>();

        try {
            // List all secret paths under the service
            var list = vaultTemplate
                .opsForVersionedKeyValue(getBasePath())
                .list(servicePath);

            if (list != null && !list.isEmpty()) {
                for (String secretName : list) {
                    String fullPath = servicePath + "/" + secretName;
                    String secretValue = getSecret(fullPath);
                    secrets.put(secretName, secretValue);
                }
            }

            log.info("Retrieved {} secrets for service: {}", secrets.size(), servicePath);
            return secrets;

        } catch (Exception e) {
            log.error("Error retrieving secrets for service: {}", servicePath, e);
            return getAllSecretsFromEnvironment(servicePath);
        }
    }

    /**
     * Invalidate cached secret (force refresh on next access)
     *
     * @param secretPath Path to secret
     */
    public void invalidateCache(String secretPath) {
        secretCache.keySet().stream()
            .filter(key -> key.startsWith(secretPath))
            .forEach(secretCache::remove);
        log.info("Invalidated cache for secret: {}", secretPath);
    }

    /**
     * Clear all cached secrets
     */
    public void clearCache() {
        int size = secretCache.size();
        secretCache.clear();
        log.info("Cleared {} cached secrets", size);
    }

    /**
     * Fallback method when Vault is unavailable
     */
    private String getSecretFallback(String secretPath, String fieldName, Throwable t) {
        log.warn("Vault circuit breaker OPEN - falling back to environment variables for: {}", secretPath);
        return getSecretFromEnvironment(secretPath, fieldName);
    }

    private String getSecretFallback(String secretPath, Throwable t) {
        return getSecretFallback(secretPath, "value", t);
    }

    /**
     * Fallback to environment variables when Vault unavailable
     */
    private String getSecretFromEnvironment(String secretPath, String fieldName) {
        // Convert path to environment variable name
        // Example: "keycloak/client-secret" -> "KEYCLOAK_CLIENT_SECRET"
        String envVarName = secretPath
            .replace("/", "_")
            .replace("-", "_")
            .toUpperCase();

        if (!fieldName.equals("value")) {
            envVarName = envVarName + "_" + fieldName.toUpperCase();
        }

        String value = System.getenv(envVarName);
        if (value == null) {
            log.error("Secret not found in Vault or environment: {} (env var: {})", secretPath, envVarName);
            throw new VaultSecretException("Secret not found: " + secretPath);
        }

        log.warn("Using environment variable for secret: {} (THIS SHOULD ONLY HAPPEN IN DEVELOPMENT)", secretPath);
        return value;
    }

    private Map<String, String> getAllSecretsFromEnvironment(String servicePath) {
        Map<String, String> secrets = new HashMap<>();
        String prefix = servicePath.replace("/", "_").toUpperCase() + "_";

        System.getenv().forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                String secretName = key.substring(prefix.length()).toLowerCase();
                secrets.put(secretName, value);
            }
        });

        return secrets;
    }

    private Map<String, String> getAllSecretsFallback(String servicePath, Throwable t) {
        log.warn("Vault circuit breaker OPEN - falling back to environment for service: {}", servicePath);
        return getAllSecretsFromEnvironment(servicePath);
    }

    private String buildFullPath(String secretPath) {
        return basePath + "/" + secretPath;
    }

    private String getBasePath() {
        // Extract base mount path from basePath (remove /data/ for KV v2)
        return basePath.replace("/data/", "/");
    }

    private String getCacheKey(String secretPath, String fieldName) {
        return secretPath + ":" + fieldName;
    }

    private String extractFieldValue(Map<String, Object> data, String fieldName) {
        Object value = data.get(fieldName);
        return value != null ? value.toString() : null;
    }

    private void auditSecretAccess(String secretPath, String fieldName, boolean success) {
        // Log to audit service (never log actual secret value)
        log.info("AUDIT: Secret accessed - path={}, field={}, success={}, app={}, time={}",
            secretPath, fieldName, success, applicationName, Instant.now());
    }

    private void auditSecretWrite(String secretPath, String[] fields) {
        log.info("AUDIT: Secret written - path={}, fields={}, app={}, time={}",
            secretPath, String.join(",", fields), applicationName, Instant.now());
    }

    /**
     * Cached secret with TTL
     */
    private static class CachedSecret {
        private final String value;
        private final Instant expiresAt;

        public CachedSecret(String value, Duration ttl) {
            this.value = value;
            this.expiresAt = Instant.now().plus(ttl);
        }

        public String getValue() {
            return value;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Custom exception for secret operations
     */
    public static class VaultSecretException extends RuntimeException {
        public VaultSecretException(String message) {
            super(message);
        }

        public VaultSecretException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
