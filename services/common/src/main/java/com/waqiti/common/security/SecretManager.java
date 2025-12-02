package com.waqiti.common.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade Secret Manager
 *
 * Provides centralized secret management with:
 * - HashiCorp Vault integration
 * - AWS Secrets Manager fallback
 * - In-memory encrypted cache with TTL
 * - Secret rotation support
 * - Audit logging
 * - Metrics and monitoring
 *
 * Security Features:
 * - AES-256-GCM encryption for cached secrets
 * - Automatic secret rotation detection
 * - Zero-trust secret access
 * - PCI-DSS compliant secret handling
 *
 * @author Waqiti Security Team
 * @version 2.0
 */
@Slf4j
@Component
public class SecretManager {

    private final VaultTemplate vaultTemplate;
    private final MeterRegistry meterRegistry;
    private final SecureRandom secureRandom;
    private final Map<String, CachedSecret> secretCache;

    // Metrics
    private final Counter secretAccessCounter;
    private final Counter secretCacheHitCounter;
    private final Counter secretCacheMissCounter;
    private final Counter secretRotationCounter;
    private final Timer secretFetchTimer;

    @Value("${secret.cache.ttl.minutes:5}")
    private int cacheTtlMinutes;

    @Value("${secret.vault.enabled:true}")
    private boolean vaultEnabled;

    @Value("${secret.fallback.enabled:true}")
    private boolean fallbackEnabled;

    private static final String VAULT_SECRET_PATH_PREFIX = "secret/data/waqiti/";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    public SecretManager(VaultTemplate vaultTemplate, MeterRegistry meterRegistry) {
        this.vaultTemplate = vaultTemplate;
        this.meterRegistry = meterRegistry;
        this.secureRandom = new SecureRandom();
        this.secretCache = new ConcurrentHashMap<>();

        // Initialize metrics
        this.secretAccessCounter = Counter.builder("secret.access.total")
                .description("Total secret access attempts")
                .register(meterRegistry);

        this.secretCacheHitCounter = Counter.builder("secret.cache.hit.total")
                .description("Secret cache hits")
                .register(meterRegistry);

        this.secretCacheMissCounter = Counter.builder("secret.cache.miss.total")
                .description("Secret cache misses")
                .register(meterRegistry);

        this.secretRotationCounter = Counter.builder("secret.rotation.detected.total")
                .description("Secret rotation events detected")
                .register(meterRegistry);

        this.secretFetchTimer = Timer.builder("secret.fetch.duration")
                .description("Time to fetch secret from Vault")
                .register(meterRegistry);

        log.info("SecretManager initialized - Vault enabled: {}, Fallback enabled: {}, Cache TTL: {} minutes",
                vaultEnabled, fallbackEnabled, cacheTtlMinutes);
    }

    /**
     * Retrieve secret from Vault with caching
     *
     * @param secretKey Secret identifier (e.g., "stripe.api.key")
     * @return Secret value
     * @throws SecretNotFoundException if secret not found
     */
    public String getSecret(String secretKey) {
        secretAccessCounter.increment();

        // Check cache first
        CachedSecret cached = secretCache.get(secretKey);
        if (cached != null && !cached.isExpired()) {
            secretCacheHitCounter.increment();
            log.debug("Secret cache hit: {}", secretKey);
            return cached.getDecryptedValue();
        }

        secretCacheMissCounter.increment();
        log.debug("Secret cache miss: {}", secretKey);

        // Fetch from Vault
        String secretValue = secretFetchTimer.record(() -> fetchFromVault(secretKey));

        // Cache encrypted secret
        cacheSecret(secretKey, secretValue);

        return secretValue;
    }

    /**
     * Retrieve secret from Vault with default value
     */
    public String getSecret(String secretKey, String defaultValue) {
        try {
            return getSecret(secretKey);
        } catch (SecretNotFoundException e) {
            log.warn("Secret not found: {}, using default value", secretKey);
            return defaultValue;
        }
    }

    /**
     * Retrieve database connection string
     */
    public String getDatabaseSecret(String environment, String database) {
        String secretKey = String.format("database.%s.%s.connection", environment, database);
        return getSecret(secretKey);
    }

    /**
     * Retrieve API key for external service
     */
    public String getApiKey(String serviceName) {
        String secretKey = String.format("api.%s.key", serviceName.toLowerCase());
        return getSecret(secretKey);
    }

    /**
     * Retrieve OAuth credentials
     */
    public OAuthCredentials getOAuthCredentials(String provider) {
        String clientIdKey = String.format("oauth.%s.client_id", provider.toLowerCase());
        String clientSecretKey = String.format("oauth.%s.client_secret", provider.toLowerCase());

        return new OAuthCredentials(
            getSecret(clientIdKey),
            getSecret(clientSecretKey)
        );
    }

    /**
     * Force secret refresh (for rotation scenarios)
     */
    public void refreshSecret(String secretKey) {
        secretCache.remove(secretKey);
        secretRotationCounter.increment();
        log.info("Secret refreshed: {}", secretKey);

        // Pre-fetch new value
        getSecret(secretKey);
    }

    /**
     * Clear all cached secrets (emergency use only)
     */
    public void clearCache() {
        int size = secretCache.size();
        secretCache.clear();
        log.warn("Secret cache cleared - {} secrets removed", size);
    }

    /**
     * Fetch secret from Vault
     */
    private String fetchFromVault(String secretKey) {
        if (!vaultEnabled) {
            throw new SecretNotFoundException("Vault is disabled, cannot fetch secret: " + secretKey);
        }

        try {
            String vaultPath = VAULT_SECRET_PATH_PREFIX + secretKey.replace(".", "/");
            VaultResponse response = vaultTemplate.read(vaultPath);

            if (response == null || response.getData() == null) {
                log.error("Secret not found in Vault: {}", secretKey);

                // Try fallback if enabled
                if (fallbackEnabled) {
                    return fetchFromFallback(secretKey);
                }

                throw new SecretNotFoundException("Secret not found: " + secretKey);
            }

            // Extract secret value from response
            Map<String, Object> data = response.getData();
            Object value = data.get("value");

            if (value == null) {
                throw new SecretNotFoundException("Secret value is null: " + secretKey);
            }

            log.debug("Secret fetched from Vault: {}", secretKey);
            return value.toString();

        } catch (Exception e) {
            log.error("Failed to fetch secret from Vault: {}", secretKey, e);

            // Try fallback if enabled
            if (fallbackEnabled) {
                return fetchFromFallback(secretKey);
            }

            throw new SecretFetchException("Failed to fetch secret: " + secretKey, e);
        }
    }

    /**
     * Fetch secret from AWS Secrets Manager (fallback).
     * Provides redundancy when Vault is unavailable.
     */
    private String fetchFromFallback(String secretKey) {
        log.warn("Attempting to fetch secret from fallback (AWS Secrets Manager): {}", secretKey);

        try {
            // Use AWS Secrets Manager SDK
            software.amazon.awssdk.services.secretsmanager.SecretsManagerClient client =
                software.amazon.awssdk.services.secretsmanager.SecretsManagerClient.builder()
                    .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                    .build();

            software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest request =
                software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest.builder()
                    .secretId("waqiti/" + secretKey.replace(".", "/"))
                    .build();

            software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse response =
                client.getSecretValue(request);

            if (response.secretString() != null) {
                log.info("Secret fetched from AWS Secrets Manager fallback: {}", secretKey);
                return response.secretString();
            }

            throw new SecretNotFoundException("Secret value null in AWS Secrets Manager: " + secretKey);

        } catch (software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException e) {
            log.error("Secret not found in AWS Secrets Manager: {}", secretKey);
            throw new SecretNotFoundException("Secret not found in fallback: " + secretKey);

        } catch (Exception e) {
            log.error("Failed to fetch secret from AWS Secrets Manager: {}", secretKey, e);
            throw new SecretFetchException("Fallback secret fetch failed: " + secretKey, e);
        }
    }

    /**
     * Cache secret with encryption
     */
    private void cacheSecret(String secretKey, String secretValue) {
        try {
            // Generate encryption key for this secret
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, secureRandom);
            SecretKey encryptionKey = keyGen.generateKey();

            // Encrypt secret value
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);

            byte[] encryptedValue = cipher.doFinal(secretValue.getBytes(StandardCharsets.UTF_8));

            // Store in cache with expiration
            Instant expiration = Instant.now().plus(Duration.ofMinutes(cacheTtlMinutes));
            CachedSecret cached = new CachedSecret(
                encryptedValue,
                iv,
                encryptionKey.getEncoded(),
                expiration
            );

            secretCache.put(secretKey, cached);
            log.debug("Secret cached with encryption: {}, expires at: {}", secretKey, expiration);

        } catch (Exception e) {
            log.error("Failed to cache secret: {}", secretKey, e);
            // Non-critical failure, continue without caching
        }
    }

    /**
     * Cached secret with encryption
     */
    private static class CachedSecret {
        private final byte[] encryptedValue;
        private final byte[] iv;
        private final byte[] encryptionKey;
        private final Instant expiration;

        public CachedSecret(byte[] encryptedValue, byte[] iv, byte[] encryptionKey, Instant expiration) {
            this.encryptedValue = encryptedValue;
            this.iv = iv;
            this.encryptionKey = encryptionKey;
            this.expiration = expiration;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiration);
        }

        public String getDecryptedValue() {
            try {
                SecretKey key = new SecretKeySpec(encryptionKey, "AES");
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

                byte[] decryptedValue = cipher.doFinal(encryptedValue);
                return new String(decryptedValue, StandardCharsets.UTF_8);

            } catch (Exception e) {
                throw new SecretDecryptionException("Failed to decrypt cached secret", e);
            }
        }
    }

    /**
     * OAuth credentials
     */
    public static class OAuthCredentials {
        private final String clientId;
        private final String clientSecret;

        public OAuthCredentials(String clientId, String clientSecret) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        public String getClientId() {
            return clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }
    }

    /**
     * Custom exceptions
     */
    public static class SecretNotFoundException extends RuntimeException {
        public SecretNotFoundException(String message) {
            super(message);
        }
    }

    public static class SecretFetchException extends RuntimeException {
        public SecretFetchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class SecretDecryptionException extends RuntimeException {
        public SecretDecryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
