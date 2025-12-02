package com.waqiti.security.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Biometric Template Encryption Service
 * Provides secure encryption and decryption of biometric templates
 * using AES-GCM encryption with key rotation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BiometricEncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int KEY_SIZE = 256;
    
    @Value("${biometric.encryption.master-key:#{null}}")
    private String masterKeyBase64;
    
    @Value("${biometric.encryption.key-rotation-enabled:true}")
    private boolean keyRotationEnabled;
    
    private final Map<String, SecretKey> keyCache = new ConcurrentHashMap<>();
    private SecretKey currentKey;
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Encrypt biometric template
     */
    public String encryptTemplate(byte[] template) {
        try {
            // Get or generate encryption key
            SecretKey key = getOrGenerateKey();
            
            // Generate IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
            
            // Encrypt the template
            byte[] cipherText = cipher.doFinal(template);
            
            // Combine IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            
            // Encode to Base64
            return Base64.getEncoder().encodeToString(byteBuffer.array());
            
        } catch (Exception e) {
            log.error("Error encrypting biometric template", e);
            throw new RuntimeException("Failed to encrypt biometric template", e);
        }
    }
    
    /**
     * Decrypt biometric template
     */
    public byte[] decryptTemplate(String encryptedTemplate) {
        try {
            // Decode from Base64
            byte[] cipherMessage = Base64.getDecoder().decode(encryptedTemplate);
            
            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(cipherMessage);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);
            
            // Get decryption key
            SecretKey key = getOrGenerateKey();
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            
            // Decrypt the template
            return cipher.doFinal(cipherText);
            
        } catch (Exception e) {
            log.error("Error decrypting biometric template", e);
            throw new RuntimeException("Failed to decrypt biometric template", e);
        }
    }
    
    /**
     * Get or generate encryption key
     */
    private SecretKey getOrGenerateKey() {
        if (currentKey == null) {
            synchronized (this) {
                if (currentKey == null) {
                    if (masterKeyBase64 != null && !masterKeyBase64.isEmpty()) {
                        // Use configured master key
                        byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64);
                        currentKey = new SecretKeySpec(keyBytes, "AES");
                    } else {
                        // Generate new key
                        currentKey = generateNewKey();
                    }
                }
            }
        }
        return currentKey;
    }
    
    /**
     * Generate new AES key
     */
    private SecretKey generateNewKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(KEY_SIZE, secureRandom);
            SecretKey key = keyGenerator.generateKey();
            
            // Store key for future use (in production, use proper key management)
            String keyId = "key_" + System.currentTimeMillis();
            keyCache.put(keyId, key);
            
            log.info("Generated new encryption key: {}", keyId);
            return key;
            
        } catch (Exception e) {
            log.error("Error generating encryption key", e);
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }
    
    /**
     * Rotate encryption key
     */
    public void rotateKey() {
        if (!keyRotationEnabled) {
            log.warn("Key rotation is disabled");
            return;
        }
        
        try {
            SecretKey newKey = generateNewKey();
            SecretKey oldKey = currentKey;
            currentKey = newKey;
            
            // Keep old key for decryption of existing data
            if (oldKey != null) {
                String oldKeyId = "old_key_" + System.currentTimeMillis();
                keyCache.put(oldKeyId, oldKey);
            }
            
            log.info("Encryption key rotated successfully");
            
        } catch (Exception e) {
            log.error("Error rotating encryption key", e);
        }
    }
}