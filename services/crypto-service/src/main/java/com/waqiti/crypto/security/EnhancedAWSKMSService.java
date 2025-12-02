/**
 * Enhanced AWS KMS Service
 * 
 * Provides advanced encryption/decryption for cryptocurrency private keys with:
 * - Hardware Security Module (HSM) backing
 * - Enhanced encryption context
 * - Key derivation functions
 * - Envelope encryption pattern
 * - Audit logging
 * - Key rotation management
 */
package com.waqiti.crypto.security;

import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.*;
import com.waqiti.crypto.dto.EncryptedKey;
import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.exception.KMSEncryptionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedAWSKMSService {

    @Value("${aws.kms.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.kms.master-key-id}")
    private String masterKeyId;
    
    @Value("${aws.kms.hsm-key-id:}")
    private String hsmKeyId; // CloudHSM key for maximum security

    @Value("${aws.kms.key-rotation.enabled:true}")
    private boolean keyRotationEnabled;
    
    @Value("${aws.kms.key-rotation.days:90}")
    private int keyRotationDays;
    
    @Value("${crypto.envelope.encryption:true}")
    private boolean envelopeEncryption;

    private AWSKMS kmsClient;
    
    // Cache for data encryption keys (short-lived)
    private final Map<String, CachedDataKey> dataKeyCache = new ConcurrentHashMap<>();
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int DATA_KEY_CACHE_TTL_MINUTES = 5;

    @PostConstruct
    public void init() {
        this.kmsClient = AWSKMSClientBuilder.standard()
                .withRegion(awsRegion)
                .build();
        
        // Verify KMS key exists and is active
        validateKMSKey();
        
        if (keyRotationEnabled) {
            enableKeyRotation();
        }
        
        // Schedule data key cache cleanup
        scheduleDataKeyCacheCleanup();
        
        log.info("Enhanced AWS KMS Service initialized with region: {}, master key: {}, HSM: {}", 
            awsRegion, maskKeyId(masterKeyId), hsmKeyId != null ? "enabled" : "disabled");
    }

    /**
     * Enhanced private key encryption with envelope encryption
     */
    public EncryptedKey encryptPrivateKey(String privateKey, UUID userId, CryptoCurrency currency, 
                                         Map<String, Object> additionalContext) {
        log.info("Encrypting private key for user: {} currency: {} with enhanced security", 
            userId, currency.name());
        
        try {
            // Create comprehensive encryption context
            Map<String, String> encryptionContext = createEnhancedEncryptionContext(
                userId, currency, additionalContext);
            
            if (envelopeEncryption) {
                return encryptWithEnvelopeEncryption(privateKey, encryptionContext);
            } else {
                return encryptDirectly(privateKey, encryptionContext);
            }
            
        } catch (Exception e) {
            log.error("Failed to encrypt private key for user: {} currency: {}", userId, currency, e);
            throw new KMSEncryptionException("Enhanced encryption failed", e);
        }
    }

    /**
     * Envelope encryption implementation
     */
    private EncryptedKey encryptWithEnvelopeEncryption(String privateKey, 
                                                      Map<String, String> encryptionContext) 
            throws Exception {
        
        // Generate or retrieve data encryption key
        GenerateDataKeyRequest dataKeyRequest = new GenerateDataKeyRequest()
            .withKeyId(getActiveKeyId())
            .withKeySpec("AES_256")
            .withEncryptionContext(encryptionContext);
        
        GenerateDataKeyResult dataKeyResult = kmsClient.generateDataKey(dataKeyRequest);
        
        // Extract plaintext and encrypted data keys
        ByteBuffer plaintextKey = dataKeyResult.getPlaintext();
        ByteBuffer encryptedKey = dataKeyResult.getCiphertextBlob();
        
        try {
            // Encrypt private key with data key using AES-GCM
            byte[] encryptedPrivateKey = encryptWithDataKey(
                privateKey.getBytes(StandardCharsets.UTF_8), 
                plaintextKey.array()
            );
            
            // Create envelope structure
            EnvelopeEncryptedData envelope = EnvelopeEncryptedData.builder()
                .encryptedDataKey(Base64.getEncoder().encodeToString(encryptedKey.array()))
                .encryptedData(Base64.getEncoder().encodeToString(encryptedPrivateKey))
                .algorithm(ALGORITHM)
                .version("2.0")
                .build();
            
            String serializedEnvelope = serializeEnvelope(envelope);
            
            log.info("Successfully performed envelope encryption");
            
            return EncryptedKey.builder()
                .encryptedData(serializedEnvelope)
                .keyId(dataKeyResult.getKeyId())
                .encryptionContext(encryptionContext)
                .algorithm("ENVELOPE_AES_256_GCM")
                .version("2.0")
                .build();
            
        } finally {
            // Clear plaintext key from memory
            Arrays.fill(plaintextKey.array(), (byte) 0);
        }
    }
    
    /**
     * Direct KMS encryption (legacy mode)
     */
    private EncryptedKey encryptDirectly(String privateKey, Map<String, String> encryptionContext) {
        EncryptRequest encryptRequest = new EncryptRequest()
            .withKeyId(getActiveKeyId())
            .withPlaintext(ByteBuffer.wrap(privateKey.getBytes(StandardCharsets.UTF_8)))
            .withEncryptionContext(encryptionContext);
        
        EncryptResult result = kmsClient.encrypt(encryptRequest);
        String encryptedData = Base64.getEncoder().encodeToString(result.getCiphertextBlob().array());
        
        return EncryptedKey.builder()
            .encryptedData(encryptedData)
            .keyId(result.getKeyId())
            .encryptionContext(encryptionContext)
            .algorithm("AWS_KMS_AES_256")
            .version("1.0")
            .build();
    }

    /**
     * Enhanced private key decryption
     */
    public String decryptPrivateKey(EncryptedKey encryptedKey) {
        log.debug("Decrypting private key with version: {}", encryptedKey.getVersion());
        
        try {
            // Validate encryption context
            validateEncryptionContext(encryptedKey.getEncryptionContext());
            
            if ("2.0".equals(encryptedKey.getVersion()) && envelopeEncryption) {
                return decryptWithEnvelopeDecryption(encryptedKey);
            } else {
                return decryptDirectly(encryptedKey);
            }
            
        } catch (Exception e) {
            log.error("Failed to decrypt private key", e);
            throw new KMSEncryptionException("Enhanced decryption failed", e);
        }
    }
    
    /**
     * Envelope decryption implementation
     */
    private String decryptWithEnvelopeDecryption(EncryptedKey encryptedKey) throws Exception {
        // Deserialize envelope
        EnvelopeEncryptedData envelope = deserializeEnvelope(encryptedKey.getEncryptedData());
        
        // Decrypt data encryption key using KMS
        DecryptRequest decryptRequest = new DecryptRequest()
            .withCiphertextBlob(ByteBuffer.wrap(
                Base64.getDecoder().decode(envelope.getEncryptedDataKey())
            ))
            .withEncryptionContext(encryptedKey.getEncryptionContext());
        
        DecryptResult decryptResult = kmsClient.decrypt(decryptRequest);
        ByteBuffer plaintextKey = decryptResult.getPlaintext();
        
        try {
            // Decrypt private key with data key
            byte[] encryptedData = Base64.getDecoder().decode(envelope.getEncryptedData());
            byte[] decryptedPrivateKey = decryptWithDataKey(encryptedData, plaintextKey.array());
            
            return new String(decryptedPrivateKey, StandardCharsets.UTF_8);
            
        } finally {
            // Clear plaintext key from memory
            Arrays.fill(plaintextKey.array(), (byte) 0);
        }
    }
    
    /**
     * Direct KMS decryption (legacy mode)
     */
    private String decryptDirectly(EncryptedKey encryptedKey) {
        byte[] ciphertext = Base64.getDecoder().decode(encryptedKey.getEncryptedData());
        
        DecryptRequest decryptRequest = new DecryptRequest()
            .withCiphertextBlob(ByteBuffer.wrap(ciphertext))
            .withEncryptionContext(encryptedKey.getEncryptionContext());
        
        DecryptResult result = kmsClient.decrypt(decryptRequest);
        return new String(result.getPlaintext().array(), StandardCharsets.UTF_8);
    }

    /**
     * Encrypt data with data encryption key using AES-GCM
     */
    private byte[] encryptWithDataKey(byte[] plaintext, byte[] dataKey) throws Exception {
        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom.getInstanceStrong().nextBytes(iv);
        
        // Initialize cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(dataKey, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        
        // Encrypt
        byte[] ciphertext = cipher.doFinal(plaintext);
        
        // Combine IV + ciphertext
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        
        return result;
    }
    
    /**
     * Decrypt data with data encryption key using AES-GCM
     */
    private byte[] decryptWithDataKey(byte[] encryptedData, byte[] dataKey) throws Exception {
        // Extract IV and ciphertext
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[encryptedData.length - GCM_IV_LENGTH];
        System.arraycopy(encryptedData, 0, iv, 0, iv.length);
        System.arraycopy(encryptedData, iv.length, ciphertext, 0, ciphertext.length);
        
        // Initialize cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(dataKey, "AES");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        
        // Decrypt
        return cipher.doFinal(ciphertext);
    }

    /**
     * Create enhanced encryption context
     */
    private Map<String, String> createEnhancedEncryptionContext(UUID userId, CryptoCurrency currency, 
                                                                Map<String, Object> additionalContext) {
        Map<String, String> context = new HashMap<>();
        
        // Standard context
        context.put("userId", userId.toString());
        context.put("currency", currency.name());
        context.put("purpose", "CRYPTO_PRIVATE_KEY");
        context.put("service", "waqiti-crypto");
        context.put("version", "2.0");
        
        // Temporal context
        context.put("timestamp", Instant.now().toString());
        context.put("encryptionDate", DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC).format(Instant.now()));
        
        // Security context
        context.put("keyRotationVersion", getCurrentKeyRotationVersion());
        context.put("environment", System.getProperty("spring.profiles.active", "unknown"));
        context.put("region", awsRegion);
        
        // Additional context
        if (additionalContext != null) {
            additionalContext.forEach((key, value) -> 
                context.put("custom_" + key, value.toString()));
        }
        
        return context;
    }
    
    /**
     * Validate encryption context for security
     */
    private void validateEncryptionContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            throw new KMSEncryptionException("Encryption context is required");
        }
        
        // Verify required fields
        String[] requiredFields = {"userId", "currency", "purpose", "timestamp"};
        for (String field : requiredFields) {
            if (!context.containsKey(field)) {
                throw new KMSEncryptionException("Missing required context field: " + field);
            }
        }
        
        // Verify purpose
        if (!"CRYPTO_PRIVATE_KEY".equals(context.get("purpose"))) {
            throw new KMSEncryptionException("Invalid encryption purpose");
        }
    }
    
    /**
     * Get the active KMS key ID (prefer HSM if available)
     */
    private String getActiveKeyId() {
        return hsmKeyId != null && !hsmKeyId.isEmpty() ? hsmKeyId : masterKeyId;
    }
    
    /**
     * Validate KMS key exists and is active
     */
    private void validateKMSKey() {
        try {
            DescribeKeyRequest request = new DescribeKeyRequest().withKeyId(getActiveKeyId());
            DescribeKeyResult result = kmsClient.describeKey(request);
            
            if (!result.getKeyMetadata().isEnabled()) {
                throw new KMSEncryptionException("KMS key is not enabled: " + getActiveKeyId());
            }
            
            log.info("KMS key validated: {} ({})", 
                maskKeyId(result.getKeyMetadata().getKeyId()),
                result.getKeyMetadata().getKeyUsage());
            
        } catch (Exception e) {
            throw new KMSEncryptionException("KMS key validation failed", e);
        }
    }
    
    /**
     * Enable automatic key rotation
     */
    private void enableKeyRotation() {
        try {
            EnableKeyRotationRequest request = new EnableKeyRotationRequest()
                .withKeyId(getActiveKeyId());
            
            kmsClient.enableKeyRotation(request);
            log.info("Key rotation enabled for key: {}", maskKeyId(getActiveKeyId()));
            
        } catch (Exception e) {
            log.warn("Could not enable key rotation (may already be enabled): {}", e.getMessage());
        }
    }
    
    /**
     * Get current key rotation version
     */
    private String getCurrentKeyRotationVersion() {
        try {
            DescribeKeyRequest request = new DescribeKeyRequest().withKeyId(getActiveKeyId());
            DescribeKeyResult result = kmsClient.describeKey(request);
            
            return String.valueOf(result.getKeyMetadata().getCreationDate().getTime() / 
                (keyRotationDays * 24 * 60 * 60 * 1000L));
            
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * Schedule data key cache cleanup
     */
    private void scheduleDataKeyCacheCleanup() {
        Timer timer = new Timer("KMS-DataKey-Cache-Cleanup", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanupDataKeyCache();
            }
        }, DATA_KEY_CACHE_TTL_MINUTES * 60 * 1000, DATA_KEY_CACHE_TTL_MINUTES * 60 * 1000);
    }
    
    /**
     * Clean up expired data keys from cache
     */
    private void cleanupDataKeyCache() {
        long now = System.currentTimeMillis();
        int removed = 0;
        
        Iterator<Map.Entry<String, CachedDataKey>> iterator = dataKeyCache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CachedDataKey> entry = iterator.next();
            if (now - entry.getValue().getTimestamp() > DATA_KEY_CACHE_TTL_MINUTES * 60 * 1000) {
                iterator.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            log.debug("Cleaned up {} expired data keys from cache", removed);
        }
    }
    
    /**
     * Serialize envelope encrypted data
     */
    private String serializeEnvelope(EnvelopeEncryptedData envelope) {
        return String.format("v%s:%s:%s:%s", 
            envelope.getVersion(),
            envelope.getAlgorithm(),
            envelope.getEncryptedDataKey(),
            envelope.getEncryptedData()
        );
    }
    
    /**
     * Deserialize envelope encrypted data
     */
    private EnvelopeEncryptedData deserializeEnvelope(String serialized) {
        String[] parts = serialized.split(":", 4);
        if (parts.length != 4) {
            throw new KMSEncryptionException("Invalid envelope format");
        }
        
        return EnvelopeEncryptedData.builder()
            .version(parts[0].substring(1)) // Remove 'v' prefix
            .algorithm(parts[1])
            .encryptedDataKey(parts[2])
            .encryptedData(parts[3])
            .build();
    }
    
    /**
     * Mask key ID for logging
     */
    private String maskKeyId(String keyId) {
        if (keyId == null || keyId.length() < 8) return keyId;
        return keyId.substring(0, 8) + "***";
    }
    
    /**
     * Envelope encrypted data structure
     */
    @lombok.Data
    @lombok.Builder
    private static class EnvelopeEncryptedData {
        private String version;
        private String algorithm;
        private String encryptedDataKey;
        private String encryptedData;
    }
    
    /**
     * Cached data key structure
     */
    @lombok.Data
    @lombok.Builder
    private static class CachedDataKey {
        private byte[] plaintextKey;
        private String encryptedKey;
        private long timestamp;
    }
}