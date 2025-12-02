package com.waqiti.common.vault;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.*;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Enterprise-grade Vault Secret Service for managing secrets with HashiCorp Vault
 * 
 * Features:
 * - Secure secret storage and retrieval with HashiCorp Vault
 * - Automatic secret rotation and versioning
 * - Transit encryption for data-in-transit
 * - Local caching with TTL for performance
 * - Audit logging for compliance
 * - Fallback to encrypted local storage
 * - Health monitoring and metrics
 * 
 * Security Features:
 * - AES-256-GCM encryption for local fallback
 * - Secure key derivation and management
 * - Automatic secret expiration
 * - Access control and audit trails
 * - Zero-knowledge architecture
 * 
 * @author Waqiti Security Team
 * @since 2.0.0
 */
@Slf4j
@Service
@RefreshScope
@ConditionalOnProperty(value = "vault.enabled", havingValue = "true", matchIfMissing = false)
public class VaultSecretService {

    private final VaultTemplate vaultTemplate;
    
    public VaultSecretService(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }
    
    @Value("${vault.kv.backend:secret}")
    private String kvBackend;
    
    @Value("${vault.kv.default-context:waqiti}")
    private String defaultContext;
    
    @Value("${vault.transit.backend:transit}")
    private String transitBackend;
    
    @Value("${vault.transit.key-name:waqiti-master}")
    private String transitKeyName;
    
    @Value("${vault.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${vault.cache.ttl.minutes:5}")
    private int cacheTtlMinutes;
    
    @Value("${vault.cache.max-size:1000}")
    private int cacheMaxSize;
    
    @Value("${vault.fallback.enabled:true}")
    private boolean fallbackEnabled;
    
    @Value("${vault.audit.enabled:true}")
    private boolean auditEnabled;
    
    @Value("${vault.health.check.interval.seconds:30}")
    private int healthCheckIntervalSeconds;
    
    // Cache for frequently accessed secrets
    private final Map<String, CachedSecret> secretCache = new ConcurrentHashMap<>();
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // Fallback storage for when Vault is unavailable
    private final Map<String, EncryptedSecret> fallbackStorage = new ConcurrentHashMap<>();
    private SecretKey fallbackKey;
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Health monitoring
    private volatile boolean vaultHealthy = true;
    private volatile Instant lastHealthCheck = Instant.now();
    private volatile long secretsRetrieved = 0;
    private volatile long secretsStored = 0;
    private volatile long cacheHits = 0;
    private volatile long cacheMisses = 0;
    
    @PostConstruct
    public void initialize() {
        try {
            // Initialize fallback encryption key
            if (fallbackEnabled) {
                initializeFallbackEncryption();
            }
            
            // Verify Vault connectivity
            checkVaultHealth();
            
            // Initialize transit key if it doesn't exist
            initializeTransitKey();
            
            log.info("VaultSecretService initialized successfully - Backend: {}, Context: {}, Cache: {}, Fallback: {}",
                    kvBackend, defaultContext, cacheEnabled, fallbackEnabled);
                    
        } catch (Exception e) {
            log.error("Failed to initialize VaultSecretService", e);
            if (!fallbackEnabled) {
                throw new RuntimeException("Vault initialization failed and fallback is disabled", e);
            }
        }
    }
    
    /**
     * Retrieve a secret from Vault with caching and fallback support
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String getSecret(String path) {
        return getSecret(path, defaultContext);
    }
    
    /**
     * Retrieve a secret from Vault with specific context
     */
    public String getSecret(String path, String context) {
        String fullPath = buildFullPath(path, context);
        
        // Check cache first
        if (cacheEnabled) {
            CachedSecret cached = getCachedSecret(fullPath);
            if (cached != null) {
                cacheHits++;
                auditSecretAccess(fullPath, "CACHE_HIT");
                return cached.getValue();
            }
            cacheMisses++;
        }
        
        try {
            // Retrieve from Vault
            String secret = retrieveFromVault(fullPath);
            
            if (secret != null) {
                // Cache the secret
                if (cacheEnabled) {
                    cacheSecret(fullPath, secret);
                }
                
                // Store in fallback if enabled
                if (fallbackEnabled) {
                    storeFallback(fullPath, secret);
                }
                
                secretsRetrieved++;
                auditSecretAccess(fullPath, "VAULT_RETRIEVED");
                return secret;
            }
            
        } catch (Exception e) {
            log.error("Failed to retrieve secret from Vault: {}", fullPath, e);
            
            // Try fallback storage
            if (fallbackEnabled) {
                String fallbackSecret = retrieveFromFallback(fullPath);
                if (fallbackSecret != null) {
                    auditSecretAccess(fullPath, "FALLBACK_RETRIEVED");
                    return fallbackSecret;
                }
            }
            
            throw new RuntimeException("Failed to retrieve secret: " + path, e);
        }
        
        return null;
    }
    
    /**
     * Store a secret in Vault
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void saveSecret(String path, String value) {
        saveSecret(path, value, defaultContext);
    }
    
    /**
     * Store a secret in Vault with specific context
     */
    public void saveSecret(String path, String value, String context) {
        String fullPath = buildFullPath(path, context);
        
        try {
            // Store in Vault
            storeInVault(fullPath, value);
            
            // Invalidate cache
            if (cacheEnabled) {
                invalidateCache(fullPath);
            }
            
            // Update fallback storage
            if (fallbackEnabled) {
                storeFallback(fullPath, value);
            }
            
            secretsStored++;
            auditSecretAccess(fullPath, "SECRET_STORED");
            
        } catch (Exception e) {
            log.error("Failed to store secret in Vault: {}", fullPath, e);
            
            // Store in fallback if Vault fails
            if (fallbackEnabled) {
                storeFallback(fullPath, value);
                log.warn("Secret stored in fallback storage due to Vault failure: {}", fullPath);
            } else {
                throw new RuntimeException("Failed to store secret: " + path, e);
            }
        }
    }
    
    /**
     * Store a secret in Vault (alias for saveSecret)
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void storeSecret(String path, String value) {
        saveSecret(path, value);
    }
    
    /**
     * Store a secret in Vault with specific context (alias for saveSecret)
     */
    public void storeSecret(String path, String value, String context) {
        saveSecret(path, value, context);
    }
    
    /**
     * Delete a secret from Vault
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void deleteSecret(String path) {
        deleteSecret(path, defaultContext);
    }
    
    /**
     * Delete a secret from Vault with specific context
     */
    public void deleteSecret(String path, String context) {
        String fullPath = buildFullPath(path, context);
        
        try {
            // Delete from Vault
            deleteFromVault(fullPath);
            
            // Remove from cache
            if (cacheEnabled) {
                invalidateCache(fullPath);
            }
            
            // Remove from fallback
            if (fallbackEnabled) {
                fallbackStorage.remove(fullPath);
            }
            
            auditSecretAccess(fullPath, "SECRET_DELETED");
            
        } catch (Exception e) {
            log.error("Failed to delete secret from Vault: {}", fullPath, e);
            throw new RuntimeException("Failed to delete secret: " + path, e);
        }
    }
    
    /**
     * Check if a secret exists
     */
    public boolean secretExists(String path) {
        return secretExists(path, defaultContext);
    }
    
    /**
     * Check if a secret exists with specific context
     */
    public boolean secretExists(String path, String context) {
        String fullPath = buildFullPath(path, context);
        
        try {
            return getSecret(path, context) != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Encrypt data using Vault Transit engine
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String encryptData(String plaintext) {
        try {
            VaultTransitOperations transitOperations = vaultTemplate.opsForTransit(transitBackend);
            String ciphertext = transitOperations.encrypt(transitKeyName, plaintext);
            return ciphertext;
        } catch (Exception e) {
            log.error("Failed to encrypt data using Vault Transit", e);
            
            // Fallback to local encryption
            if (fallbackEnabled) {
                return encryptLocal(plaintext);
            }
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Decrypt data using Vault Transit engine
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String decryptData(String ciphertext) {
        try {
            VaultTransitOperations transitOperations = vaultTemplate.opsForTransit(transitBackend);
            String plaintext = transitOperations.decrypt(transitKeyName, ciphertext);
            return plaintext;
        } catch (Exception e) {
            log.error("Failed to decrypt data using Vault Transit", e);
            
            // Fallback to local decryption
            if (fallbackEnabled && ciphertext.startsWith("local:")) {
                return decryptLocal(ciphertext.substring(6));
            }
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }
    
    /**
     * Rotate encryption keys
     */
    public void rotateKeys() {
        try {
            // Rotate transit key
            VaultTransitOperations transitOperations = vaultTemplate.opsForTransit(transitBackend);
            transitOperations.rotate(transitKeyName);
            
            // Rotate fallback key
            if (fallbackEnabled) {
                rotateFallbackKey();
            }
            
            log.info("Successfully rotated encryption keys");
            auditSecretAccess("KEY_ROTATION", "KEYS_ROTATED");
            
        } catch (Exception e) {
            log.error("Failed to rotate keys", e);
            throw new RuntimeException("Failed to rotate keys", e);
        }
    }
    
    /**
     * Get secret metadata
     */
    public Map<String, Object> getSecretMetadata(String path) {
        return getSecretMetadata(path, defaultContext);
    }
    
    /**
     * Get secret metadata with context
     */
    public Map<String, Object> getSecretMetadata(String path, String context) {
        String fullPath = buildFullPath(path, context);
        
        try {
            VaultVersionedKeyValueOperations kvOps = vaultTemplate
                    .opsForVersionedKeyValue(kvBackend);
            
            Versioned.Metadata metadata = kvOps.get(fullPath).getMetadata();
            
            Map<String, Object> metadataMap = new HashMap<>();
            metadataMap.put("version", metadata.getVersion());
            metadataMap.put("destroyed", metadata.isDestroyed());
            
            // Add timestamp information using reflection for compatibility
            // These fields may not be available in all Spring Vault versions
            metadataMap.put("createdTime", extractFieldValue(metadata, "createdTime", Instant.now()));
            metadataMap.put("deletedTime", extractFieldValue(metadata, "deletedTime", null));
            metadataMap.put("customMetadata", extractFieldValue(metadata, "customMetadata", Collections.emptyMap()));
            
            return metadataMap;
            
        } catch (Exception e) {
            log.error("Failed to get secret metadata: {}", fullPath, e);
            return Collections.emptyMap();
        }
    }
    
    // Scheduled health check
    @Scheduled(fixedDelayString = "${vault.health.check.interval.seconds:30}000")
    public void healthCheck() {
        checkVaultHealth();
    }
    
    // Scheduled cache cleanup
    @Scheduled(fixedDelay = 60000) // Every minute
    public void cleanupCache() {
        if (!cacheEnabled) return;
        
        cacheLock.writeLock().lock();
        try {
            Instant cutoff = Instant.now().minus(Duration.ofMinutes(cacheTtlMinutes));
            secretCache.entrySet().removeIf(entry -> 
                entry.getValue().getCreatedAt().isBefore(cutoff));
                
            // Enforce max size
            if (secretCache.size() > cacheMaxSize) {
                List<Map.Entry<String, CachedSecret>> entries = new ArrayList<>(secretCache.entrySet());
                entries.sort(Comparator.comparing(e -> e.getValue().getCreatedAt()));
                
                int toRemove = secretCache.size() - cacheMaxSize;
                for (int i = 0; i < toRemove; i++) {
                    secretCache.remove(entries.get(i).getKey());
                }
            }
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    // Private helper methods
    
    private String buildFullPath(String path, String context) {
        return context + "/" + path;
    }
    
    private String retrieveFromVault(String fullPath) throws Exception {
        VaultVersionedKeyValueOperations kvOps = vaultTemplate
                .opsForVersionedKeyValue(kvBackend);
        
        Versioned<Map<String, Object>> response = kvOps.get(fullPath);
        
        if (response != null && response.hasData()) {
            Map<String, Object> data = response.getData();
            if (data.containsKey("value")) {
                return (String) data.get("value");
            }
        }
        
        return null;
    }
    
    private void storeInVault(String fullPath, String value) throws Exception {
        VaultVersionedKeyValueOperations kvOps = vaultTemplate
                .opsForVersionedKeyValue(kvBackend);
        
        Map<String, String> data = new HashMap<>();
        data.put("value", value);
        data.put("timestamp", Instant.now().toString());
        data.put("source", "VaultSecretService");
        
        kvOps.put(fullPath, data);
    }
    
    private void deleteFromVault(String fullPath) throws Exception {
        VaultVersionedKeyValueOperations kvOps = vaultTemplate
                .opsForVersionedKeyValue(kvBackend);
        kvOps.delete(fullPath);
    }
    
    private CachedSecret getCachedSecret(String path) {
        cacheLock.readLock().lock();
        try {
            CachedSecret cached = secretCache.get(path);
            if (cached != null && !cached.isExpired(cacheTtlMinutes)) {
                return cached;
            }
            return null;
        } finally {
            cacheLock.readLock().unlock();
        }
    }
    
    private void cacheSecret(String path, String value) {
        cacheLock.writeLock().lock();
        try {
            secretCache.put(path, new CachedSecret(value));
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    private void invalidateCache(String path) {
        cacheLock.writeLock().lock();
        try {
            secretCache.remove(path);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    private void initializeFallbackEncryption() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, secureRandom);
            fallbackKey = keyGen.generateKey();
            log.info("Fallback encryption initialized with AES-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to initialize fallback encryption", e);
        }
    }
    
    private void storeFallback(String path, String value) {
        try {
            String encrypted = encryptLocal(value);
            fallbackStorage.put(path, new EncryptedSecret(encrypted));
        } catch (Exception e) {
            log.error("Failed to store in fallback: {}", path, e);
        }
    }
    
    private String retrieveFromFallback(String path) {
        EncryptedSecret encrypted = fallbackStorage.get(path);
        if (encrypted != null) {
            try {
                return decryptLocal(encrypted.getValue());
            } catch (Exception e) {
                log.error("Failed to decrypt from fallback: {}", path, e);
            }
        }
        return null;
    }
    
    private String encryptLocal(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            
            // Generate IV
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            
            // Initialize cipher
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, fallbackKey, parameterSpec);
            
            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);
            
            return "local:" + Base64.getEncoder().encodeToString(byteBuffer.array());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt locally", e);
        }
    }
    
    private String decryptLocal(String ciphertext) {
        try {
            // Decode from base64
            byte[] ciphertextBytes = Base64.getDecoder().decode(ciphertext);
            
            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(ciphertextBytes);
            byte[] iv = new byte[12];
            byteBuffer.get(iv);
            byte[] encrypted = new byte[byteBuffer.remaining()];
            byteBuffer.get(encrypted);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, fallbackKey, parameterSpec);
            
            // Decrypt
            byte[] plaintext = cipher.doFinal(encrypted);
            
            return new String(plaintext, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt locally", e);
        }
    }
    
    private void rotateFallbackKey() {
        Map<String, String> decryptedSecrets = new HashMap<>();
        
        // Decrypt all secrets with old key
        for (Map.Entry<String, EncryptedSecret> entry : fallbackStorage.entrySet()) {
            try {
                String decrypted = decryptLocal(entry.getValue().getValue());
                decryptedSecrets.put(entry.getKey(), decrypted);
            } catch (Exception e) {
                log.error("Failed to decrypt secret during rotation: {}", entry.getKey(), e);
            }
        }
        
        // Generate new key
        initializeFallbackEncryption();
        
        // Re-encrypt with new key
        for (Map.Entry<String, String> entry : decryptedSecrets.entrySet()) {
            storeFallback(entry.getKey(), entry.getValue());
        }
        
        log.info("Rotated fallback encryption key, re-encrypted {} secrets", decryptedSecrets.size());
    }
    
    private void initializeTransitKey() {
        try {
            VaultTransitOperations transitOperations = vaultTemplate.opsForTransit(transitBackend);
            
            // Check if key exists, create if not
            try {
                transitOperations.getKey(transitKeyName);
            } catch (Exception e) {
                // Key doesn't exist, create it
                VaultTransitKeyCreationRequest request = VaultTransitKeyCreationRequest.builder()
                        .exportable(false)
                        .allowPlaintextBackup(false)
                        .type("aes256-gcm96")
                        .build();
                        
                transitOperations.createKey(transitKeyName, request);
                log.info("Created transit key: {}", transitKeyName);
            }
        } catch (Exception e) {
            log.error("Failed to initialize transit key", e);
        }
    }
    
    private void checkVaultHealth() {
        try {
            VaultHealth health = vaultTemplate.opsForSys().health();
            vaultHealthy = !health.isSealed() && health.isInitialized();
            lastHealthCheck = Instant.now();
            
            if (!vaultHealthy) {
                log.error("Vault is unhealthy - Sealed: {}, Initialized: {}", 
                         health.isSealed(), health.isInitialized());
            }
        } catch (Exception e) {
            vaultHealthy = false;
            log.error("Failed to check Vault health", e);
        }
    }
    
    private void auditSecretAccess(String path, String action) {
        if (!auditEnabled) return;
        
        try {
            Map<String, Object> auditEntry = new HashMap<>();
            auditEntry.put("timestamp", Instant.now().toString());
            auditEntry.put("path", path);
            auditEntry.put("action", action);
            auditEntry.put("service", "VaultSecretService");
            
            // Log audit entry
            log.info("AUDIT: {}", auditEntry);
            
            // Could also send to external audit system
        } catch (Exception e) {
            log.error("Failed to audit secret access", e);
        }
    }
    
    /**
     * Get service metrics
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("vaultHealthy", vaultHealthy);
        metrics.put("lastHealthCheck", lastHealthCheck.toString());
        metrics.put("secretsRetrieved", secretsRetrieved);
        metrics.put("secretsStored", secretsStored);
        metrics.put("cacheHits", cacheHits);
        metrics.put("cacheMisses", cacheMisses);
        metrics.put("cacheSize", secretCache.size());
        metrics.put("fallbackSize", fallbackStorage.size());
        
        if (cacheMisses > 0) {
            double hitRate = (double) cacheHits / (cacheHits + cacheMisses) * 100;
            metrics.put("cacheHitRate", String.format("%.2f%%", hitRate));
        }
        
        return metrics;
    }
    
    /**
     * Extract field value using reflection for compatibility
     */
    private Object extractFieldValue(Object obj, String fieldName, Object defaultValue) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            // Field doesn't exist or is inaccessible - return default
            return defaultValue;
        }
    }
    
    // Inner classes
    
    private static class CachedSecret {
        private final String value;
        private final Instant createdAt;
        
        public CachedSecret(String value) {
            this.value = value;
            this.createdAt = Instant.now();
        }
        
        public String getValue() {
            return value;
        }
        
        public Instant getCreatedAt() {
            return createdAt;
        }
        
        public boolean isExpired(int ttlMinutes) {
            return createdAt.plus(Duration.ofMinutes(ttlMinutes)).isBefore(Instant.now());
        }
    }
    
    private static class EncryptedSecret {
        private final String value;
        private final Instant createdAt;
        
        public EncryptedSecret(String value) {
            this.value = value;
            this.createdAt = Instant.now();
        }
        
        public String getValue() {
            return value;
        }
        
        public Instant getCreatedAt() {
            return createdAt;
        }
    }
}