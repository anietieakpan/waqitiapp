package com.waqiti.common.encryption;

import com.waqiti.common.encryption.model.EncryptionKey;
import com.waqiti.common.encryption.exception.KeyManagementException;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.vault.VaultService;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise key management service with HashiCorp Vault integration.
 * 
 * Provides secure key storage, rotation, and versioning for field-level encryption.
 * Integrates with HashiCorp Vault for enterprise-grade key security.
 * 
 * Features:
 * - Secure key generation and storage
 * - Key versioning and rotation
 * - Integration with HashiCorp Vault
 * - Comprehensive audit logging
 * - Performance monitoring and caching
 * - Compliance with security best practices
 * 
 * @author Waqiti Security Team
 * @since 2.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KeyManagementService {

    private final VaultService vaultService;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    
    @Value("${waqiti.encryption.key-rotation-days:90}")
    private int keyRotationDays;
    
    @Value("${waqiti.encryption.vault-path:secret/encryption-keys}")
    private String vaultKeyPath;
    
    // Metrics
    private final Counter keyGenerationCounter;
    private final Counter keyRetrievalCounter;
    private final Counter keyRotationCounter;
    
    // Key metadata cache
    private final Map<String, KeyMetadata> keyMetadataCache = new ConcurrentHashMap<>();

    public KeyManagementService(VaultService vaultService, AuditService auditService, MeterRegistry meterRegistry) {
        this.vaultService = vaultService;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.keyGenerationCounter = Counter.builder("encryption_key_operations")
            .description("Number of encryption key operations")
            .tag("operation", "generate")
            .register(meterRegistry);
            
        this.keyRetrievalCounter = Counter.builder("encryption_key_operations")
            .description("Number of encryption key operations")
            .tag("operation", "retrieve")
            .register(meterRegistry);
            
        this.keyRotationCounter = Counter.builder("encryption_key_operations")
            .description("Number of encryption key operations")
            .tag("operation", "rotate")
            .register(meterRegistry);
    }

    /**
     * Generates and stores a new encryption key for a field type.
     * 
     * @param fieldType The type of field this key will encrypt
     * @param keyBytes The raw key bytes
     * @return The version identifier for the stored key
     */
    @Timed(value = "key_storage_duration", description = "Time taken to store encryption key")
    public String storeKey(String fieldType, SecretKey key) {
        try {
            String keyVersion = generateKeyVersion();
            String vaultPath = buildVaultPath(fieldType, keyVersion);
            
            // Store key in Vault with metadata
            Map<String, Object> keyData = Map.of(
                "key", key.getEncoded(),
                "algorithm", key.getAlgorithm(),
                "fieldType", fieldType,
                "createdAt", LocalDateTime.now().toString(),
                "version", keyVersion
            );
            
            vaultService.write(vaultPath, keyData);
            
            // Update key metadata cache
            KeyMetadata metadata = KeyMetadata.builder()
                .fieldType(fieldType)
                .version(keyVersion)
                .algorithm(key.getAlgorithm())
                .createdAt(LocalDateTime.now())
                .vaultPath(vaultPath)
                .build();
                
            keyMetadataCache.put(fieldType, metadata);
            
            // Update metrics
            keyGenerationCounter.increment();
            
            // Audit key creation
            auditService.auditKeyGeneration(fieldType, keyVersion);
            
            log.info("Successfully stored encryption key for field type: {} version: {}", fieldType, keyVersion);
            return keyVersion;
            
        } catch (Exception e) {
            log.error("Failed to store encryption key for field type: {}", fieldType, e);
            throw new KeyManagementException("Failed to store encryption key: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the current encryption key for a field type.
     * 
     * @param fieldType The field type to get the key for
     * @return The current encryption key
     */
    @Cacheable(value = "encryption-keys", key = "#fieldType")
    @Timed(value = "key_retrieval_duration", description = "Time taken to retrieve encryption key")
    public SecretKey getCurrentKey(String fieldType) {
        try {
            String currentVersion = getCurrentKeyVersion(fieldType);
            return getKey(fieldType, currentVersion);
            
        } catch (Exception e) {
            log.error("Failed to retrieve current key for field type: {}", fieldType, e);
            throw new KeyManagementException("Failed to retrieve current key: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a specific version of an encryption key.
     * 
     * @param fieldType The field type
     * @param version The key version to retrieve
     * @return The encryption key for the specified version
     */
    @Cacheable(value = "encryption-keys", key = "#fieldType + ':' + #version")
    @Timed(value = "key_retrieval_duration", description = "Time taken to retrieve encryption key")
    public SecretKey getKey(String fieldType, String version) {
        try {
            String vaultPath = buildVaultPath(fieldType, version);
            
            // Retrieve key from Vault
            Map<String, Object> keyData = vaultService.read(vaultPath);
            
            if (keyData == null || !keyData.containsKey("key")) {
                throw new KeyManagementException("Key not found in Vault for field type: " + fieldType + " version: " + version);
            }
            
            // Reconstruct SecretKey
            byte[] keyBytes = (byte[]) keyData.get("key");
            String algorithm = (String) keyData.get("algorithm");
            
            SecretKey key = new SecretKeySpec(keyBytes, algorithm);
            
            // Update metrics
            keyRetrievalCounter.increment();
            
            // Audit key retrieval
            auditService.auditKeyRetrieval(fieldType, version);
            
            log.debug("Successfully retrieved encryption key for field type: {} version: {}", fieldType, version);
            return key;
            
        } catch (Exception e) {
            log.error("Failed to retrieve encryption key for field type: {} version: {}", fieldType, version, e);
            throw new KeyManagementException("Failed to retrieve encryption key: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the current key version for a field type.
     * 
     * @param fieldType The field type
     * @return The current key version
     */
    public String getCurrentKeyVersion(String fieldType) {
        try {
            // Check cache first
            KeyMetadata cached = keyMetadataCache.get(fieldType);
            if (cached != null && !isKeyRotationDue(cached)) {
                return cached.getVersion();
            }
            
            // List all versions in Vault and find the latest
            String latestVersion = findLatestKeyVersion(fieldType);
            
            if (latestVersion == null) {
                throw new KeyManagementException("No encryption key found for field type: " + fieldType);
            }
            
            return latestVersion;
            
        } catch (Exception e) {
            log.error("Failed to get current key version for field type: {}", fieldType, e);
            throw new KeyManagementException("Failed to get current key version: " + e.getMessage(), e);
        }
    }

    /**
     * Rotates the encryption key for a field type.
     * This creates a new key version while keeping old versions for decryption.
     * 
     * @param fieldType The field type to rotate keys for
     * @return The new key version
     */
    @CacheEvict(value = "encryption-keys", allEntries = true)
    @Timed(value = "key_rotation_duration", description = "Time taken to rotate encryption key")
    public String rotateKey(String fieldType, SecretKey newKey) {
        try {
            log.info("Starting key rotation for field type: {}", fieldType);
            
            String newVersion = storeKey(fieldType, newKey);
            
            // Update metrics
            keyRotationCounter.increment();
            
            // Audit key rotation
            auditService.auditKeyRotation(fieldType, newVersion);
            
            log.info("Successfully rotated key for field type: {} to version: {}", fieldType, newVersion);
            return newVersion;
            
        } catch (Exception e) {
            log.error("Key rotation failed for field type: {}", fieldType, e);
            throw new KeyManagementException("Failed to rotate key: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if keys require rotation based on age and policy.
     * 
     * @param fieldType The field type to check
     * @return true if rotation is due
     */
    public boolean isKeyRotationDue(String fieldType) {
        try {
            KeyMetadata metadata = keyMetadataCache.get(fieldType);
            if (metadata == null) {
                // If no metadata in cache, assume rotation is due
                return true;
            }
            
            return isKeyRotationDue(metadata);
            
        } catch (Exception e) {
            log.error("Error checking key rotation status for field type: {}", fieldType, e);
            return true; // Err on the side of caution
        }
    }

    /**
     * Lists all available key versions for a field type.
     * 
     * @param fieldType The field type
     * @return List of available key versions
     */
    public java.util.List<String> getKeyVersions(String fieldType) {
        try {
            String basePath = vaultKeyPath + "/" + fieldType + "/";
            return vaultService.list(basePath);
            
        } catch (Exception e) {
            log.error("Failed to list key versions for field type: {}", fieldType, e);
            throw new KeyManagementException("Failed to list key versions: " + e.getMessage(), e);
        }
    }

    // Private helper methods

    private String generateKeyVersion() {
        return "v" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String buildVaultPath(String fieldType, String version) {
        return vaultKeyPath + "/" + fieldType + "/" + version;
    }

    private String findLatestKeyVersion(String fieldType) {
        try {
            java.util.List<String> versions = getKeyVersions(fieldType);
            
            if (versions.isEmpty()) {
                return null;
            }
            
            // Sort versions by timestamp (embedded in version string)
            return versions.stream()
                .max((v1, v2) -> {
                    long t1 = extractTimestampFromVersion(v1);
                    long t2 = extractTimestampFromVersion(v2);
                    return Long.compare(t1, t2);
                })
                .orElse(null);
                
        } catch (Exception e) {
            log.error("Error finding latest key version for field type: {}", fieldType, e);
            return null;
        }
    }

    private long extractTimestampFromVersion(String version) {
        try {
            // Version format: v{timestamp}_{uuid}
            String timestampPart = version.substring(1, version.indexOf('_'));
            return Long.parseLong(timestampPart);
        } catch (Exception e) {
            return 0; // Fallback for malformed versions
        }
    }

    private boolean isKeyRotationDue(KeyMetadata metadata) {
        LocalDateTime rotationThreshold = LocalDateTime.now().minusDays(keyRotationDays);
        return metadata.getCreatedAt().isBefore(rotationThreshold);
    }

    // Inner classes

    @lombok.Data
    @lombok.Builder
    private static class KeyMetadata {
        private String fieldType;
        private String version;
        private String algorithm;
        private LocalDateTime createdAt;
        private String vaultPath;
    }
}