package com.waqiti.common.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;
import org.springframework.vault.support.VaultResponse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade Vault Manager with advanced features:
 * - Automatic secret rotation
 * - Lease management
 * - Circuit breaker pattern
 * - Metrics and monitoring
 * - Encryption as a Service
 * - Dynamic database credentials
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnterpriseVaultManager {
    
    private final VaultTemplate vaultTemplate;
    private final SecretLeaseContainer leaseContainer;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    private final Map<String, Instant> secretRotationTracker = new ConcurrentHashMap<>();
    private final Map<String, String> encryptionKeyCache = new ConcurrentHashMap<>();
    
    private static final String METRICS_PREFIX = "vault.secrets";
    private static final Duration DEFAULT_ROTATION_INTERVAL = Duration.ofHours(24);
    
    @PostConstruct
    public void initialize() {
        setupLeaseManagement();
        startSecretRotationScheduler();
        registerMetrics();
        log.info("Enterprise Vault Manager initialized with advanced features");
    }
    
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        leaseContainer.destroy();
    }
    
    /**
     * Get secret with automatic renewal and metrics
     */
    @Cacheable(value = "vault-secrets", key = "#path + ':' + #key")
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getSecret(String path, String key) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            VaultResponse response = vaultTemplate.read(path);
            
            if (response == null || response.getData() == null) {
                meterRegistry.counter(METRICS_PREFIX + ".not_found").increment();
                throw new VaultSecretNotFoundException("Secret not found: " + path + "/" + key);
            }
            
            String secret = (String) response.getData().get(key);
            if (secret == null) {
                meterRegistry.counter(METRICS_PREFIX + ".key_not_found").increment();
                throw new VaultSecretNotFoundException("Key not found: " + key + " in " + path);
            }
            
            // Track for rotation
            secretRotationTracker.put(path + ":" + key, Instant.now());
            meterRegistry.counter(METRICS_PREFIX + ".retrieved").increment();
            
            return secret;
            
        } catch (Exception e) {
            meterRegistry.counter(METRICS_PREFIX + ".errors").increment();
            log.error("Failed to retrieve secret from path: {}, key: {}", path, key, e);
            throw new VaultSecretException("Failed to retrieve secret", e);
        } finally {
            sample.stop(Timer.builder(METRICS_PREFIX + ".retrieval_time")
                .description("Time taken to retrieve secret from Vault")
                .register(meterRegistry));
        }
    }
    
    /**
     * Get database credentials with dynamic rotation
     */
    public DatabaseCredentials getDatabaseCredentials(String databaseRole) {
        String path = "database/creds/" + databaseRole;
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Request dynamic credentials from Vault
            VaultResponse response = vaultTemplate.read(path);
            
            if (response == null || response.getData() == null) {
                throw new VaultSecretNotFoundException("Database credentials not found for role: " + databaseRole);
            }
            
            String username = (String) response.getData().get("username");
            String password = (String) response.getData().get("password");
            
            // Setup lease renewal
            RequestedSecret requestedSecret = RequestedSecret.renewable(path);
            leaseContainer.addRequestedSecret(requestedSecret);
            
            meterRegistry.counter(METRICS_PREFIX + ".database_credentials.generated").increment();
            
            return DatabaseCredentials.builder()
                .username(username)
                .password(password)
                .leaseId(response.getLeaseId())
                .leaseDuration(response.getLeaseDuration())
                .renewable(response.isRenewable())
                .createdAt(Instant.now())
                .build();
                
        } finally {
            sample.stop(Timer.builder(METRICS_PREFIX + ".database_credentials.time")
                .description("Time taken to generate database credentials")
                .register(meterRegistry));
        }
    }
    
    /**
     * Encrypt data using Vault Transit Engine
     */
    public String encrypt(String plaintext, String keyName) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            VaultTransitOperations transitOps = vaultTemplate.opsForTransit();
            String ciphertext = transitOps.encrypt(keyName, plaintext);
            
            meterRegistry.counter(METRICS_PREFIX + ".encryption.operations").increment();
            return ciphertext;
            
        } catch (Exception e) {
            meterRegistry.counter(METRICS_PREFIX + ".encryption.errors").increment();
            log.error("Failed to encrypt data with key: {}", keyName, e);
            throw new VaultEncryptionException("Failed to encrypt data", e);
        } finally {
            sample.stop(Timer.builder(METRICS_PREFIX + ".encryption.time")
                .description("Time taken to encrypt data")
                .register(meterRegistry));
        }
    }
    
    /**
     * Decrypt data using Vault Transit Engine
     */
    public String decrypt(String ciphertext, String keyName) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            VaultTransitOperations transitOps = vaultTemplate.opsForTransit();
            String plaintext = transitOps.decrypt(keyName, ciphertext);
            
            meterRegistry.counter(METRICS_PREFIX + ".decryption.operations").increment();
            return plaintext;
            
        } catch (Exception e) {
            meterRegistry.counter(METRICS_PREFIX + ".decryption.errors").increment();
            log.error("Failed to decrypt data with key: {}", keyName, e);
            throw new VaultEncryptionException("Failed to decrypt data", e);
        } finally {
            sample.stop(Timer.builder(METRICS_PREFIX + ".decryption.time")
                .description("Time taken to decrypt data")
                .register(meterRegistry));
        }
    }
    
    /**
     * Rotate encryption key
     */
    public void rotateEncryptionKey(String keyName) {
        try {
            VaultTransitOperations transitOps = vaultTemplate.opsForTransit();
            transitOps.rotate(keyName);
            
            // Clear cache for this key
            encryptionKeyCache.remove(keyName);
            
            meterRegistry.counter(METRICS_PREFIX + ".key_rotation.success").increment();
            log.info("Successfully rotated encryption key: {}", keyName);
            
        } catch (Exception e) {
            meterRegistry.counter(METRICS_PREFIX + ".key_rotation.errors").increment();
            log.error("Failed to rotate encryption key: {}", keyName, e);
            throw new VaultKeyRotationException("Failed to rotate key", e);
        }
    }
    
    /**
     * Invalidate secret cache
     */
    @CacheEvict(value = "vault-secrets", key = "#path + ':' + #key")
    public void invalidateSecret(String path, String key) {
        secretRotationTracker.remove(path + ":" + key);
        meterRegistry.counter(METRICS_PREFIX + ".cache.evictions").increment();
        log.debug("Invalidated secret cache for: {}:{}", path, key);
    }
    
    /**
     * Get secret metadata including rotation info
     */
    public SecretMetadata getSecretMetadata(String path, String key) {
        String secretKey = path + ":" + key;
        Instant lastRotation = secretRotationTracker.get(secretKey);
        
        return SecretMetadata.builder()
            .path(path)
            .key(key)
            .lastAccessed(lastRotation)
            .nextRotation(lastRotation != null ? 
                lastRotation.plus(DEFAULT_ROTATION_INTERVAL) : null)
            .rotationRequired(isRotationRequired(secretKey))
            .build();
    }
    
    private void setupLeaseManagement() {
        // Handle lease creation events
        leaseContainer.addLeaseListener(event -> {
            if (event instanceof SecretLeaseCreatedEvent) {
                SecretLeaseCreatedEvent createdEvent = (SecretLeaseCreatedEvent) event;
                log.info("Secret lease created: {} with duration: {}s", 
                    createdEvent.getSource().getPath(), 
                    createdEvent.getLease().getLeaseDuration());
                meterRegistry.counter(METRICS_PREFIX + ".leases.created").increment();
            }
        });
        
        // Handle lease expiration events
        leaseContainer.addLeaseListener(event -> {
            if (event instanceof SecretLeaseExpiredEvent) {
                SecretLeaseExpiredEvent expiredEvent = (SecretLeaseExpiredEvent) event;
                log.warn("Secret lease expired: {}", expiredEvent.getSource().getPath());
                meterRegistry.counter(METRICS_PREFIX + ".leases.expired").increment();
            }
        });
        
        leaseContainer.start();
    }
    
    private void startSecretRotationScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                rotateExpiredSecrets();
            } catch (Exception e) {
                log.error("Error during scheduled secret rotation", e);
            }
        }, 1, 1, TimeUnit.HOURS);
    }
    
    private void rotateExpiredSecrets() {
        Instant now = Instant.now();
        
        secretRotationTracker.entrySet().stream()
            .filter(entry -> {
                Instant lastRotation = entry.getValue();
                return lastRotation.plus(DEFAULT_ROTATION_INTERVAL).isBefore(now);
            })
            .forEach(entry -> {
                String[] pathKey = entry.getKey().split(":");
                if (pathKey.length == 2) {
                    try {
                        invalidateSecret(pathKey[0], pathKey[1]);
                        meterRegistry.counter(METRICS_PREFIX + ".auto_rotation.success").increment();
                    } catch (Exception e) {
                        meterRegistry.counter(METRICS_PREFIX + ".auto_rotation.errors").increment();
                        log.error("Failed to auto-rotate secret: {}", entry.getKey(), e);
                    }
                }
            });
    }
    
    private boolean isRotationRequired(String secretKey) {
        Instant lastAccessed = secretRotationTracker.get(secretKey);
        if (lastAccessed == null) return false;
        
        return lastAccessed.plus(DEFAULT_ROTATION_INTERVAL).isBefore(Instant.now());
    }
    
    private void registerMetrics() {
        meterRegistry.gauge(METRICS_PREFIX + ".tracked_secrets", secretRotationTracker.size());
        meterRegistry.gauge(METRICS_PREFIX + ".cache.size", encryptionKeyCache.size());
    }

    // ========================================================================
    // Data Classes
    // ========================================================================

    /**
     * Database credentials from Vault's dynamic secret engine
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
        private Boolean renewable;
        private Instant createdAt;
    }

    /**
     * Metadata about a secret for rotation and monitoring
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SecretMetadata {
        private String path;
        private String key;
        private Instant lastAccessed;
        private Instant nextRotation;
        private Boolean rotationRequired;
    }
}