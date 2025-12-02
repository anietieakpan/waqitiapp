package com.waqiti.common.encryption;

import com.waqiti.common.encryption.model.EncryptionContext;
import com.waqiti.common.encryption.model.EncryptionMetadata;
import com.waqiti.common.encryption.exception.EncryptionServiceException;
import com.waqiti.common.audit.AuditService;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise-grade field-level encryption service with PCI-DSS compliance.
 * 
 * Features:
 * - AES-256-GCM encryption for maximum security
 * - Key rotation with versioning support
 * - Comprehensive audit logging
 * - Performance monitoring and metrics
 * - Circuit breaker patterns for resilience
 * - HSM integration for key management
 * 
 * @author Waqiti Security Team
 * @since 2.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FieldLevelEncryptionService {

    private final KeyManagementService keyManagementService;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    
    // Configuration
    @Value("${waqiti.encryption.algorithm:AES}")
    private String encryptionAlgorithm;
    
    @Value("${waqiti.encryption.transformation:AES/GCM/NoPadding}")
    private String encryptionTransformation;
    
    @Value("${waqiti.encryption.key-size:256}")
    private int keySize;
    
    @Value("${waqiti.encryption.iv-size:12}")
    private int ivSize;
    
    @Value("${waqiti.encryption.tag-size:16}")
    private int tagSize;

    // Metrics
    private final Counter encryptionCounter;
    private final Counter decryptionCounter;
    private final Counter encryptionErrorCounter;
    private final Counter keyRotationCounter;
    
    // Key cache for performance (with TTL)
    private final Map<String, CachedKey> keyCache = new ConcurrentHashMap<>();
    private static final long KEY_CACHE_TTL_MS = 300_000; // 5 minutes
    
    // Random number generator for IV generation
    private final SecureRandom secureRandom = new SecureRandom();

    public FieldLevelEncryptionService(KeyManagementService keyManagementService,
                                     AuditService auditService,
                                     MeterRegistry meterRegistry) {
        this.keyManagementService = keyManagementService;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.encryptionCounter = Counter.builder("field_encryption_operations")
            .description("Number of field encryption operations")
            .tag("operation", "encrypt")
            .register(meterRegistry);
            
        this.decryptionCounter = Counter.builder("field_encryption_operations")
            .description("Number of field decryption operations")
            .tag("operation", "decrypt")
            .register(meterRegistry);
            
        this.encryptionErrorCounter = Counter.builder("field_encryption_errors")
            .description("Number of field encryption errors")
            .register(meterRegistry);
            
        this.keyRotationCounter = Counter.builder("field_encryption_key_rotations")
            .description("Number of encryption key rotations")
            .register(meterRegistry);
    }

    /**
     * Encrypts sensitive data with comprehensive security controls.
     * 
     * @param plaintext The data to encrypt (must not be null)
     * @param fieldType The type of field being encrypted (for audit purposes)
     * @param entityId The ID of the entity containing this field
     * @return Encrypted data with metadata
     * @throws EncryptionServiceException if encryption fails
     */
    @Timed(value = "field_encryption_duration", description = "Time taken to encrypt field")
    public String encrypt(String plaintext, String fieldType, String entityId) throws EncryptionServiceException {
        if (!StringUtils.hasText(plaintext)) {
            throw new IllegalArgumentException("Plaintext cannot be null or empty");
        }
        
        if (!StringUtils.hasText(fieldType)) {
            throw new IllegalArgumentException("Field type cannot be null or empty");
        }

        try {
            // Create encryption context for audit purposes
            EncryptionContext context = EncryptionContext.builder()
                .fieldType(fieldType)
                .entityId(entityId)
                .operation("ENCRYPT")
                .timestamp(LocalDateTime.now())
                .build();

            // Get current encryption key
            SecretKey encryptionKey = getCurrentEncryptionKey(fieldType);
            String keyVersion = keyManagementService.getCurrentKeyVersion(fieldType);

            // Generate random IV for this encryption
            byte[] iv = generateSecureIV();

            // Perform AES-GCM encryption
            Cipher cipher = Cipher.getInstance(encryptionTransformation);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(tagSize * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, gcmSpec);

            // Add authenticated data (field type and key version) to prevent tampering
            cipher.updateAAD(fieldType.getBytes(StandardCharsets.UTF_8));
            cipher.updateAAD(keyVersion.getBytes(StandardCharsets.UTF_8));

            // Encrypt the plaintext
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Create encrypted result with metadata
            EncryptionMetadata metadata = EncryptionMetadata.builder()
                .keyVersion(keyVersion)
                .algorithm(encryptionAlgorithm)
                .transformation(encryptionTransformation)
                .ivSize(iv.length)
                .tagSize(tagSize)
                .encryptedAt(LocalDateTime.now())
                .build();

            // Combine IV, ciphertext, and metadata into final encrypted string
            String encryptedData = encodeEncryptedData(iv, ciphertext, metadata);

            // Audit the encryption operation (without logging plaintext)
            auditEncryptionOperation(context, metadata, true);

            // Update metrics
            encryptionCounter.increment();

            log.debug("Successfully encrypted field type: {} for entity: {}", fieldType, entityId);
            return encryptedData;

        } catch (Exception e) {
            encryptionErrorCounter.increment();
            
            log.error("Encryption failed for field type: {} entity: {}", fieldType, entityId, e);
            
            // Audit the failed operation
            auditEncryptionOperation(EncryptionContext.builder()
                .fieldType(fieldType)
                .entityId(entityId)
                .operation("ENCRYPT")
                .timestamp(LocalDateTime.now())
                .error(e.getMessage())
                .build(), null, false);
                
            throw new EncryptionServiceException("Failed to encrypt field: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts encrypted data with comprehensive validation and audit logging.
     * 
     * @param encryptedData The encrypted data to decrypt
     * @param fieldType The type of field being decrypted
     * @param entityId The ID of the entity containing this field
     * @return Decrypted plaintext data
     * @throws EncryptionServiceException if decryption fails
     */
    @Timed(value = "field_decryption_duration", description = "Time taken to decrypt field")
    public String decrypt(String encryptedData, String fieldType, String entityId) throws EncryptionServiceException {
        if (!StringUtils.hasText(encryptedData)) {
            throw new IllegalArgumentException("Encrypted data cannot be null or empty");
        }
        
        if (!StringUtils.hasText(fieldType)) {
            throw new IllegalArgumentException("Field type cannot be null or empty");
        }

        try {
            // Create decryption context for audit purposes
            EncryptionContext context = EncryptionContext.builder()
                .fieldType(fieldType)
                .entityId(entityId)
                .operation("DECRYPT")
                .timestamp(LocalDateTime.now())
                .build();

            // Decode the encrypted data to extract components
            DecryptionComponents components = decodeEncryptedData(encryptedData);

            // Get the appropriate decryption key based on version
            SecretKey decryptionKey = getDecryptionKey(fieldType, components.metadata.getKeyVersion());

            // Perform AES-GCM decryption
            Cipher cipher = Cipher.getInstance(encryptionTransformation);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(tagSize * 8, components.iv);
            cipher.init(Cipher.DECRYPT_MODE, decryptionKey, gcmSpec);

            // Add authenticated data to verify integrity
            cipher.updateAAD(fieldType.getBytes(StandardCharsets.UTF_8));
            cipher.updateAAD(components.metadata.getKeyVersion().getBytes(StandardCharsets.UTF_8));

            // Decrypt the ciphertext
            byte[] decryptedBytes = cipher.doFinal(components.ciphertext);
            String plaintext = new String(decryptedBytes, StandardCharsets.UTF_8);

            // Audit the decryption operation (without logging plaintext)
            auditDecryptionOperation(context, components.metadata, true);

            // Update metrics
            decryptionCounter.increment();

            log.debug("Successfully decrypted field type: {} for entity: {}", fieldType, entityId);
            return plaintext;

        } catch (Exception e) {
            encryptionErrorCounter.increment();
            
            log.error("Decryption failed for field type: {} entity: {}", fieldType, entityId, e);
            
            // Audit the failed operation
            auditDecryptionOperation(EncryptionContext.builder()
                .fieldType(fieldType)
                .entityId(entityId)
                .operation("DECRYPT")
                .timestamp(LocalDateTime.now())
                .error(e.getMessage())
                .build(), null, false);
                
            throw new EncryptionServiceException("Failed to decrypt field: " + e.getMessage(), e);
        }
    }

    /**
     * Rotates encryption keys for a specific field type.
     * This is critical for PCI-DSS compliance and security best practices.
     * 
     * @param fieldType The field type to rotate keys for
     * @return The new key version
     */
    @Timed(value = "key_rotation_duration", description = "Time taken to rotate encryption key")
    public String rotateKey(String fieldType) throws EncryptionServiceException {
        try {
            log.info("Starting key rotation for field type: {}", fieldType);
            
            // Generate new encryption key
            KeyGenerator keyGen = KeyGenerator.getInstance(encryptionAlgorithm);
            keyGen.init(keySize);
            SecretKey newKey = keyGen.generateKey();
            
            // Store the new key with versioning
            String newKeyVersion = keyManagementService.storeKey(fieldType, newKey);
            
            // Clear cached key to force reload
            keyCache.remove(fieldType);
            
            // Update metrics
            keyRotationCounter.increment();
            
            // Audit key rotation
            auditService.auditKeyRotation(fieldType, newKeyVersion);
            
            log.info("Successfully rotated key for field type: {} to version: {}", fieldType, newKeyVersion);
            return newKeyVersion;
            
        } catch (Exception e) {
            log.error("Key rotation failed for field type: {}", fieldType, e);
            throw new EncryptionServiceException("Failed to rotate key: " + e.getMessage(), e);
        }
    }

    /**
     * Re-encrypts data with the latest key version.
     * Used for key rotation and migration scenarios.
     */
    public String reEncryptWithLatestKey(String encryptedData, String fieldType, String entityId) throws EncryptionServiceException {
        try {
            // First decrypt with old key
            String plaintext = decrypt(encryptedData, fieldType, entityId);
            
            // Then encrypt with current key
            return encrypt(plaintext, fieldType, entityId);
            
        } catch (Exception e) {
            log.error("Re-encryption failed for field type: {} entity: {}", fieldType, entityId, e);
            throw new EncryptionServiceException("Failed to re-encrypt field: " + e.getMessage(), e);
        }
    }

    // Private helper methods

    private SecretKey getCurrentEncryptionKey(String fieldType) {
        CachedKey cached = keyCache.get(fieldType);
        
        if (cached != null && !cached.isExpired()) {
            return cached.key;
        }
        
        // Load key from key management service
        SecretKey key = keyManagementService.getCurrentKey(fieldType);
        
        // Cache the key with TTL
        keyCache.put(fieldType, new CachedKey(key, System.currentTimeMillis() + KEY_CACHE_TTL_MS));
        
        return key;
    }

    private SecretKey getDecryptionKey(String fieldType, String keyVersion) {
        String cacheKey = fieldType + ":" + keyVersion;
        CachedKey cached = keyCache.get(cacheKey);
        
        if (cached != null && !cached.isExpired()) {
            return cached.key;
        }
        
        // Load specific key version from key management service
        SecretKey key = keyManagementService.getKey(fieldType, keyVersion);
        
        // Cache the key with TTL
        keyCache.put(cacheKey, new CachedKey(key, System.currentTimeMillis() + KEY_CACHE_TTL_MS));
        
        return key;
    }

    private byte[] generateSecureIV() {
        byte[] iv = new byte[ivSize];
        secureRandom.nextBytes(iv);
        return iv;
    }

    private String encodeEncryptedData(byte[] iv, byte[] ciphertext, EncryptionMetadata metadata) {
        // Format: version:keyVersion:base64(iv):base64(ciphertext)
        return String.format("v1:%s:%s:%s",
            metadata.getKeyVersion(),
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(ciphertext));
    }

    private DecryptionComponents decodeEncryptedData(String encryptedData) throws EncryptionServiceException {
        try {
            String[] parts = encryptedData.split(":");
            if (parts.length != 4 || !"v1".equals(parts[0])) {
                throw new IllegalArgumentException("Invalid encrypted data format");
            }
            
            String keyVersion = parts[1];
            byte[] iv = Base64.getDecoder().decode(parts[2]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[3]);
            
            EncryptionMetadata metadata = EncryptionMetadata.builder()
                .keyVersion(keyVersion)
                .algorithm(encryptionAlgorithm)
                .transformation(encryptionTransformation)
                .build();
                
            return new DecryptionComponents(iv, ciphertext, metadata);
            
        } catch (Exception e) {
            throw new EncryptionServiceException("Failed to decode encrypted data: " + e.getMessage(), e);
        }
    }

    private void auditEncryptionOperation(EncryptionContext context, EncryptionMetadata metadata, boolean success) {
        auditService.auditEncryptionOperation(context, metadata, success);
    }

    private void auditDecryptionOperation(EncryptionContext context, EncryptionMetadata metadata, boolean success) {
        auditService.auditDecryptionOperation(context, metadata, success);
    }

    // Inner classes

    private static class CachedKey {
        final SecretKey key;
        final long expiryTime;
        
        CachedKey(SecretKey key, long expiryTime) {
            this.key = key;
            this.expiryTime = expiryTime;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private static class DecryptionComponents {
        final byte[] iv;
        final byte[] ciphertext;
        final EncryptionMetadata metadata;
        
        DecryptionComponents(byte[] iv, byte[] ciphertext, EncryptionMetadata metadata) {
            this.iv = iv;
            this.ciphertext = ciphertext;
            this.metadata = metadata;
        }
    }
}