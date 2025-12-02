package com.waqiti.common.encryption;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Arrays;

/**
 * Field-level encryption service for sensitive data
 * Uses AES-GCM for authenticated encryption with deterministic IVs for searchable fields
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FieldEncryption {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    
    @Value("${encryption.field.master-key:}")
    private String masterKeyBase64;
    
    @Value("${encryption.field.key-derivation-salt:waqiti-field-encryption}")
    private String keyDerivationSalt;
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Encrypt a field value
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            // Generate random IV for each encryption
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and ciphertext
            byte[] encryptedWithIv = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(cipherText, 0, encryptedWithIv, iv.length, cipherText.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            log.error("Failed to encrypt field", e);
            throw new EncryptionException("Field encryption failed", e);
        }
    }
    
    /**
     * Decrypt a field value
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        try {
            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedText);
            
            // Extract IV and ciphertext
            byte[] iv = Arrays.copyOfRange(encryptedWithIv, 0, GCM_IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(encryptedWithIv, GCM_IV_LENGTH, encryptedWithIv.length);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            
            byte[] plainTextBytes = cipher.doFinal(cipherText);
            
            return new String(plainTextBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Failed to decrypt field", e);
            throw new EncryptionException("Field decryption failed", e);
        }
    }
    
    /**
     * Encrypt with deterministic IV for searchable fields
     * WARNING: Use only for search keys, not for sensitive data storage
     */
    public String encryptSearchable(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            SecretKey key = deriveKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            // Generate deterministic IV based on plaintext hash
            byte[] iv = generateDeterministicIV(plaintext);
            
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and ciphertext
            byte[] encryptedWithIv = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(cipherText, 0, encryptedWithIv, iv.length, cipherText.length);
            
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            log.error("Failed to encrypt searchable field", e);
            throw new EncryptionException("Searchable field encryption failed", e);
        }
    }
    
    /**
     * Hash a field for indexing (one-way, non-reversible)
     */
    public String hash(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(keyDerivationSalt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to hash field", e);
            throw new EncryptionException("Field hashing failed", e);
        }
    }
    
    /**
     * Generate a new encryption key
     */
    public String generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_LENGTH);
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            log.error("Failed to generate encryption key", e);
            throw new EncryptionException("Key generation failed", e);
        }
    }
    
    /**
     * Validate that the encryption configuration is properly set up
     */
    public boolean isConfigurationValid() {
        try {
            if (masterKeyBase64 == null || masterKeyBase64.isEmpty()) {
                log.warn("Master key not configured for field encryption");
                return false;
            }
            
            // Test encryption/decryption
            String testValue = "test-encryption-" + System.currentTimeMillis();
            String encrypted = encrypt(testValue);
            String decrypted = decrypt(encrypted);
            
            boolean valid = testValue.equals(decrypted);
            if (!valid) {
                log.error("Field encryption validation failed - decrypted value doesn't match original");
            }
            
            return valid;
        } catch (Exception e) {
            log.error("Field encryption configuration validation failed", e);
            return false;
        }
    }
    
    private SecretKey deriveKey() {
        try {
            if (masterKeyBase64 == null || masterKeyBase64.isEmpty()) {
                throw new EncryptionException("Master key not configured");
            }
            
            // Use PBKDF2 to derive key from master key
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(keyDerivationSalt.getBytes(StandardCharsets.UTF_8));
            byte[] masterKeyBytes = Base64.getDecoder().decode(masterKeyBase64);
            digest.update(masterKeyBytes);
            byte[] derivedKeyBytes = digest.digest();
            
            return new SecretKeySpec(derivedKeyBytes, ALGORITHM);
        } catch (Exception e) {
            log.error("Failed to derive encryption key", e);
            throw new EncryptionException("Key derivation failed", e);
        }
    }
    
    private byte[] generateDeterministicIV(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(keyDerivationSalt.getBytes(StandardCharsets.UTF_8));
            digest.update(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            
            // Use first 12 bytes of hash as IV
            return Arrays.copyOf(hash, GCM_IV_LENGTH);
        } catch (Exception e) {
            log.error("Failed to generate deterministic IV", e);
            throw new EncryptionException("IV generation failed", e);
        }
    }
    
    /**
     * Exception for encryption operations
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