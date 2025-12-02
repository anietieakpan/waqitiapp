package com.waqiti.common.security.encryption;

import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.*;
import software.amazon.awssdk.core.SdkBytes;
import com.waqiti.common.security.secrets.SecretProvider;
import com.waqiti.common.security.secrets.SecretProviderException;
import com.waqiti.common.exception.EncryptionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralized key management service using AWS KMS for production
 * and local keys for development. Handles key rotation, caching,
 * and secure key derivation.
 */
@Slf4j
@Service
public class KeyManagementService {
    
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int AES_KEY_SIZE = 256;
    
    private final KmsClient kmsClient;
    private final SecretProvider secretProvider;
    private final boolean useKms;
    private final String environment;
    private final Map<String, CachedDataKey> dataKeyCache = new ConcurrentHashMap<>();
    
    @Value("${waqiti.encryption.kms.key-id:}")
    private String kmsKeyId;
    
    @Value("${waqiti.encryption.cache-ttl-minutes:60}")
    private int cacheTimeToLiveMinutes;
    
    public KeyManagementService(SecretProvider secretProvider,
                                @Value("${spring.profiles.active:dev}") String environment) {
        this.secretProvider = secretProvider;
        this.environment = environment;
        this.useKms = "production".equals(environment) || "staging".equals(environment);
        
        if (useKms) {
            this.kmsClient = KmsClient.builder().build();
            log.info("Initialized KeyManagementService with AWS KMS for environment: {}", environment);
        } else {
            this.kmsClient = null;
            log.info("Initialized KeyManagementService with local keys for environment: {}", environment);
        }
    }
    
    /**
     * Encrypts data using envelope encryption with KMS in production
     * or local key encryption in development
     */
    public EncryptedData encrypt(String plaintext, String context) {
        try {
            if (useKms) {
                return encryptWithKms(plaintext, context);
            } else {
                return encryptWithLocalKey(plaintext, context);
            }
        } catch (Exception e) {
            log.error("Encryption failed for context: {}", context, e);
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Decrypts data encrypted by this service
     */
    public String decrypt(EncryptedData encryptedData, String context) {
        try {
            if (useKms) {
                return decryptWithKms(encryptedData, context);
            } else {
                return decryptWithLocalKey(encryptedData, context);
            }
        } catch (Exception e) {
            log.error("Decryption failed for context: {}", context, e);
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }
    
    /**
     * Generates a data encryption key for a specific service/purpose
     */
    @Cacheable(value = "dataKeys", key = "#keyAlias")
    public DataKey generateDataKey(String keyAlias) {
        if (useKms) {
            return generateKmsDataKey(keyAlias);
        } else {
            return generateLocalDataKey(keyAlias);
        }
    }
    
    /**
     * Rotates encryption keys for a service
     */
    public void rotateKeys(String service) {
        log.info("Initiating key rotation for service: {}", service);
        
        // Invalidate cached keys
        dataKeyCache.entrySet().removeIf(entry -> entry.getKey().startsWith(service));
        
        if (useKms) {
            // KMS handles key rotation automatically
            log.info("AWS KMS automatic key rotation is enabled for service: {}", service);
        } else {
            // For local development, generate new keys
            String keyPath = String.format("encryption/%s/key", service);
            String newKey = generateSecureKey();
            try {
                secretProvider.createOrUpdateSecret(keyPath, newKey);
            } catch (SecretProviderException e) {
                log.error("Failed to create or update secret for service: {}", service, e);
                throw new RuntimeException("Key rotation failed for service: " + service, e);
            }
            log.info("Generated new local encryption key for service: {}", service);
        }
    }
    
    private EncryptedData encryptWithKms(String plaintext, String context) throws Exception {
        // Generate a data encryption key
        GenerateDataKeyRequest dataKeyRequest = GenerateDataKeyRequest.builder()
            .keyId(kmsKeyId)
            .keySpec(DataKeySpec.AES_256)
            .encryptionContext(Map.of("context", context))
            .build();
            
        GenerateDataKeyResponse dataKeyResult = kmsClient.generateDataKey(dataKeyRequest);
        
        // Use the plaintext key to encrypt the data
        byte[] plaintextKey = dataKeyResult.plaintext().asByteArray();
        SecretKey secretKey = new SecretKeySpec(plaintextKey, "AES");
        
        // Generate IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        
        // Encrypt
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
        
        // Clear the plaintext key from memory
        java.util.Arrays.fill(plaintextKey, (byte) 0);
        
        return EncryptedData.builder()
            .ciphertext(Base64.getEncoder().encodeToString(ciphertext))
            .encryptedDataKey(Base64.getEncoder().encodeToString(dataKeyResult.ciphertextBlob().asByteArray()))
            .iv(Base64.getEncoder().encodeToString(iv))
            .algorithm(AES_ALGORITHM)
            .keyId(kmsKeyId)
            .build();
    }
    
    private String decryptWithKms(EncryptedData encryptedData, String context) throws Exception {
        // Decrypt the data key
        DecryptRequest decryptRequest = DecryptRequest.builder()
            .ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder().decode(encryptedData.getEncryptedDataKey())))
            .encryptionContext(Map.of("context", context))
            .build();
            
        DecryptResponse decryptResult = kmsClient.decrypt(decryptRequest);
        byte[] plaintextKey = decryptResult.plaintext().asByteArray();
        
        try {
            // Use the decrypted key to decrypt the data
            SecretKey secretKey = new SecretKeySpec(plaintextKey, "AES");
            
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, 
                Base64.getDecoder().decode(encryptedData.getIv()));
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(encryptedData.getCiphertext()));
            return new String(plaintext);
        } finally {
            // Clear the plaintext key from memory
            java.util.Arrays.fill(plaintextKey, (byte) 0);
        }
    }
    
    private EncryptedData encryptWithLocalKey(String plaintext, String context) throws Exception {
        String keyPath = String.format("encryption/%s/key", context);
        String base64Key = secretProvider.getSecret(keyPath);
        if (base64Key == null) {
            throw new EncryptionException("Encryption key not found for context: " + context);
        }
            
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        
        // Generate IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        
        // Encrypt
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
        
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
        
        return EncryptedData.builder()
            .ciphertext(Base64.getEncoder().encodeToString(ciphertext))
            .iv(Base64.getEncoder().encodeToString(iv))
            .algorithm(AES_ALGORITHM)
            .keyId(context)
            .build();
    }
    
    private String decryptWithLocalKey(EncryptedData encryptedData, String context) throws Exception {
        String keyPath = String.format("encryption/%s/key", context);
        String base64Key = secretProvider.getSecret(keyPath);
        if (base64Key == null) {
            throw new EncryptionException("Decryption key not found for context: " + context);
        }
            
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");
        
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, 
            Base64.getDecoder().decode(encryptedData.getIv()));
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
        
        byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(encryptedData.getCiphertext()));
        return new String(plaintext);
    }
    
    private DataKey generateKmsDataKey(String keyAlias) {
        GenerateDataKeyRequest request = GenerateDataKeyRequest.builder()
            .keyId(kmsKeyId)
            .keySpec(DataKeySpec.AES_256)
            .encryptionContext(Map.of("keyAlias", keyAlias))
            .build();
            
        GenerateDataKeyResponse result = kmsClient.generateDataKey(request);
        
        return DataKey.builder()
            .plaintext(result.plaintext().asByteArray())
            .encrypted(result.ciphertextBlob().asByteArray())
            .keyId(kmsKeyId)
            .algorithm("AES_256")
            .build();
    }
    
    private DataKey generateLocalDataKey(String keyAlias) {
        byte[] keyBytes = new byte[AES_KEY_SIZE / 8];
        new SecureRandom().nextBytes(keyBytes);
        
        return DataKey.builder()
            .plaintext(keyBytes)
            .encrypted(keyBytes) // In dev, no separate encryption
            .keyId(keyAlias)
            .algorithm("AES_256")
            .build();
    }
    
    private String generateSecureKey() {
        byte[] keyBytes = new byte[AES_KEY_SIZE / 8];
        new SecureRandom().nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
    
    /**
     * Clears all cached keys (useful for key rotation)
     */
    public void clearKeyCache() {
        dataKeyCache.clear();
        log.info("Cleared all cached encryption keys");
    }
    
    /**
     * Health check for KMS connectivity
     */
    public boolean isHealthy() {
        if (!useKms) {
            return true;
        }
        
        try {
            DescribeKeyRequest request = DescribeKeyRequest.builder().keyId(kmsKeyId).build();
            DescribeKeyResponse result = kmsClient.describeKey(request);
            return result.keyMetadata().enabled();
        } catch (Exception e) {
            log.error("KMS health check failed", e);
            return false;
        }
    }
    
    /**
     * Cached data key with expiration
     */
    private static class CachedDataKey {
        private final DataKey dataKey;
        private final long expirationTime;
        
        CachedDataKey(DataKey dataKey, long ttlMinutes) {
            this.dataKey = dataKey;
            this.expirationTime = System.currentTimeMillis() + (ttlMinutes * 60 * 1000);
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
    
    /**
     * Custom exception for encryption operations
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message) {
            super(message);
        }
        
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}