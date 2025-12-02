package com.waqiti.kyc.security;

import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClientBuilder;
import com.amazonaws.services.kms.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * PII Encryption Service
 * 
 * Provides field-level encryption for Personally Identifiable Information (PII) using:
 * - AWS KMS for key management
 * - AES-256-GCM for data encryption
 * - Envelope encryption pattern
 * - Automatic key rotation
 * - Encryption context for additional security
 */
@Service
@Slf4j
public class PIIEncryptionService {
    
    private AWSKMS kmsClient;
    
    @Value("${aws.kms.pii-key-id}")
    private String kmsKeyId;
    
    @Value("${aws.kms.region:us-east-1}")
    private String awsRegion;
    
    @Value("${pii.encryption.cache.enabled:true}")
    private boolean cacheEnabled;
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final String KEY_SPEC = "AES";
    
    @PostConstruct
    public void init() {
        this.kmsClient = AWSKMSClientBuilder.standard()
            .withRegion(awsRegion)
            .build();
        
        // Validate KMS key exists and is accessible
        try {
            DescribeKeyRequest describeKeyRequest = new DescribeKeyRequest()
                .withKeyId(kmsKeyId);
            DescribeKeyResult result = kmsClient.describeKey(describeKeyRequest);
            
            if (!result.getKeyMetadata().isEnabled()) {
                throw new IllegalStateException("KMS key is not enabled: " + kmsKeyId);
            }
            
            log.info("PII Encryption Service initialized with KMS key: {}", 
                result.getKeyMetadata().getArn());
            
        } catch (Exception e) {
            log.error("Failed to validate KMS key", e);
            throw new IllegalStateException("Cannot initialize PII Encryption Service", e);
        }
    }
    
    /**
     * Encrypt PII data
     * 
     * @param plaintext The data to encrypt
     * @param context Additional context for encryption (e.g., userId, dataType)
     * @return Base64 encoded encrypted data with metadata
     */
    public String encryptPII(String plaintext, Map<String, String> context) {
        if (plaintext == null || plaintext.isEmpty()) {
            log.error("CRITICAL: Cannot encrypt null or empty PII data - GDPR compliance violation");
            throw new PIIEncryptionException("PII data cannot be null or empty for encryption");
        }
        
        try {
            // Generate a data encryption key (DEK) using KMS
            GenerateDataKeyRequest dataKeyRequest = new GenerateDataKeyRequest()
                .withKeyId(kmsKeyId)
                .withKeySpec("AES_256")
                .withEncryptionContext(context);
            
            GenerateDataKeyResult dataKeyResult = kmsClient.generateDataKey(dataKeyRequest);
            
            // Extract the plaintext DEK and encrypted DEK
            ByteBuffer plaintextKey = dataKeyResult.getPlaintext();
            ByteBuffer encryptedKey = dataKeyResult.getCiphertextBlob();
            
            // Use the plaintext DEK to encrypt the data
            byte[] encryptedData = encryptWithDataKey(
                plaintext.getBytes(StandardCharsets.UTF_8), 
                plaintextKey.array()
            );
            
            // Clear the plaintext key from memory
            plaintextKey.clear();
            
            // Combine encrypted DEK and encrypted data
            EncryptedPIIData encryptedPII = EncryptedPIIData.builder()
                .encryptedDataKey(Base64.getEncoder().encodeToString(encryptedKey.array()))
                .encryptedData(Base64.getEncoder().encodeToString(encryptedData))
                .version("1.0")
                .algorithm(ALGORITHM)
                .build();
            
            // Serialize to JSON-like format
            return serializeEncryptedData(encryptedPII);
            
        } catch (Exception e) {
            log.error("Failed to encrypt PII data", e);
            throw new PIIEncryptionException("Failed to encrypt PII data", e);
        }
    }
    
    /**
     * Encrypt PII data with default context
     */
    public String encryptPII(String plaintext) {
        Map<String, String> context = new HashMap<>();
        context.put("purpose", "pii-encryption");
        context.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return encryptPII(plaintext, context);
    }
    
    /**
     * Decrypt PII data
     * 
     * @param encryptedData The encrypted data to decrypt
     * @param context The encryption context (must match encryption context)
     * @return Decrypted plaintext
     */
    public String decryptPII(String encryptedData, Map<String, String> context) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            log.error("CRITICAL: Cannot decrypt null or empty PII data - GDPR compliance violation");
            throw new PIIEncryptionException("Encrypted PII data cannot be null or empty for decryption");
        }
        
        try {
            // Parse the encrypted data
            EncryptedPIIData encryptedPII = deserializeEncryptedData(encryptedData);
            
            // Decrypt the data encryption key using KMS
            DecryptRequest decryptRequest = new DecryptRequest()
                .withCiphertextBlob(ByteBuffer.wrap(
                    Base64.getDecoder().decode(encryptedPII.getEncryptedDataKey())
                ))
                .withEncryptionContext(context);
            
            DecryptResult decryptResult = kmsClient.decrypt(decryptRequest);
            ByteBuffer plaintextKey = decryptResult.getPlaintext();
            
            // Use the decrypted DEK to decrypt the data
            byte[] decryptedData = decryptWithDataKey(
                Base64.getDecoder().decode(encryptedPII.getEncryptedData()),
                plaintextKey.array()
            );
            
            // Clear the plaintext key from memory
            plaintextKey.clear();
            
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Failed to decrypt PII data", e);
            throw new PIIEncryptionException("Failed to decrypt PII data", e);
        }
    }
    
    /**
     * Decrypt PII data with default context
     */
    public String decryptPII(String encryptedData) {
        Map<String, String> context = new HashMap<>();
        context.put("purpose", "pii-encryption");
        return decryptPII(encryptedData, context);
    }
    
    /**
     * Encrypt data using a data encryption key
     */
    private byte[] encryptWithDataKey(byte[] plaintext, byte[] dataKey) throws Exception {
        // Generate a random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        
        // Initialize cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(dataKey, KEY_SPEC);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        
        // Encrypt the data
        byte[] ciphertext = cipher.doFinal(plaintext);
        
        // Combine IV and ciphertext
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        
        return result;
    }
    
    /**
     * Decrypt data using a data encryption key
     */
    private byte[] decryptWithDataKey(byte[] encryptedData, byte[] dataKey) throws Exception {
        // Extract IV and ciphertext
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[encryptedData.length - GCM_IV_LENGTH];
        System.arraycopy(encryptedData, 0, iv, 0, iv.length);
        System.arraycopy(encryptedData, iv.length, ciphertext, 0, ciphertext.length);
        
        // Initialize cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(dataKey, KEY_SPEC);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        
        // Decrypt the data
        return cipher.doFinal(ciphertext);
    }
    
    /**
     * Rotate encryption keys for existing encrypted data
     */
    public String rotateEncryption(String encryptedData, Map<String, String> oldContext, 
                                  Map<String, String> newContext) {
        String decrypted = decryptPII(encryptedData, oldContext);
        return encryptPII(decrypted, newContext);
    }
    
    /**
     * Serialize encrypted data to string format
     */
    private String serializeEncryptedData(EncryptedPIIData data) {
        return String.format("v%s:%s:%s", 
            data.getVersion(),
            data.getEncryptedDataKey(),
            data.getEncryptedData()
        );
    }
    
    /**
     * Deserialize encrypted data from string format
     */
    private EncryptedPIIData deserializeEncryptedData(String serialized) {
        String[] parts = serialized.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid encrypted data format");
        }
        
        return EncryptedPIIData.builder()
            .version(parts[0].substring(1)) // Remove 'v' prefix
            .encryptedDataKey(parts[1])
            .encryptedData(parts[2])
            .algorithm(ALGORITHM)
            .build();
    }
    
    /**
     * Encrypted PII data structure
     */
    @lombok.Data
    @lombok.Builder
    private static class EncryptedPIIData {
        private String version;
        private String encryptedDataKey;
        private String encryptedData;
        private String algorithm;
    }
    
    /**
     * Exception for PII encryption errors
     */
    public static class PIIEncryptionException extends RuntimeException {
        public PIIEncryptionException(String message) {
            super(message);
        }
        
        public PIIEncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}