package com.waqiti.virtualcard.security;

import com.waqiti.common.exception.EncryptionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for encrypting and decrypting sensitive card data
 * Uses AES-256-GCM encryption for maximum security
 */
@Service
@Slf4j
public class CardEncryptionService {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    
    private final SecretKey encryptionKey;
    private final SecureRandom secureRandom;
    private final ConcurrentHashMap<String, String> hashCache;
    
    @Value("${card.encryption.key}")
    private String masterKey;
    
    @Value("${card.encryption.salt:waqiti-card-salt}")
    private String salt;
    
    @Value("${card.encryption.iterations:100000}")
    private int iterations;
    
    @Value("${card.encryption.cache-enabled:true}")
    private boolean cacheEnabled;
    
    public CardEncryptionService(@Value("${card.encryption.key}") String masterKey) {
        this.masterKey = masterKey;
        this.encryptionKey = deriveKey(masterKey);
        this.secureRandom = new SecureRandom();
        this.hashCache = new ConcurrentHashMap<>();
        log.info("Card encryption service initialized with AES-256-GCM");
    }
    
    /**
     * Encrypt card number with additional authentication data
     */
    public String encryptCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Card number cannot be null or empty");
        }
        
        try {
            // Remove any non-digit characters
            String cleanCardNumber = cardNumber.replaceAll("\\D", "");
            
            if (cleanCardNumber.length() < 13 || cleanCardNumber.length() > 19) {
                throw new IllegalArgumentException("Invalid card number length");
            }
            
            return encrypt(cleanCardNumber, "CARD_NUMBER");
            
        } catch (Exception e) {
            log.error("Failed to encrypt card number", e);
            throw new EncryptionException("Failed to encrypt card number", e);
        }
    }
    
    /**
     * Decrypt card number
     */
    public String decryptCardNumber(String encryptedCardNumber) {
        if (encryptedCardNumber == null || encryptedCardNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Encrypted card number cannot be null or empty");
        }
        
        try {
            return decrypt(encryptedCardNumber, "CARD_NUMBER");
        } catch (Exception e) {
            log.error("Failed to decrypt card number", e);
            throw new EncryptionException("Failed to decrypt card number", e);
        }
    }
    
    /**
     * Encrypt CVV
     */
    public String encryptCVV(String cvv) {
        if (cvv == null || cvv.trim().isEmpty()) {
            throw new IllegalArgumentException("CVV cannot be null or empty");
        }
        
        try {
            // Validate CVV format
            if (!cvv.matches("^\\d{3,4}$")) {
                throw new IllegalArgumentException("Invalid CVV format");
            }
            
            return encrypt(cvv, "CVV");
            
        } catch (Exception e) {
            log.error("Failed to encrypt CVV", e);
            throw new EncryptionException("Failed to encrypt CVV", e);
        }
    }
    
    /**
     * Decrypt CVV
     */
    public String decryptCVV(String encryptedCvv) {
        if (encryptedCvv == null || encryptedCvv.trim().isEmpty()) {
            throw new IllegalArgumentException("Encrypted CVV cannot be null or empty");
        }
        
        try {
            return decrypt(encryptedCvv, "CVV");
        } catch (Exception e) {
            log.error("Failed to decrypt CVV", e);
            throw new EncryptionException("Failed to decrypt CVV", e);
        }
    }
    
    /**
     * Encrypt PIN
     */
    public String encryptPIN(String pin) {
        if (pin == null || pin.trim().isEmpty()) {
            throw new IllegalArgumentException("PIN cannot be null or empty");
        }
        
        try {
            // Validate PIN format
            if (!pin.matches("^\\d{4,8}$")) {
                throw new IllegalArgumentException("Invalid PIN format");
            }
            
            return encrypt(pin, "PIN");
            
        } catch (Exception e) {
            log.error("Failed to encrypt PIN", e);
            throw new EncryptionException("Failed to encrypt PIN", e);
        }
    }
    
    /**
     * Decrypt PIN
     */
    public String decryptPIN(String encryptedPin) {
        if (encryptedPin == null || encryptedPin.trim().isEmpty()) {
            throw new IllegalArgumentException("Encrypted PIN cannot be null or empty");
        }
        
        try {
            return decrypt(encryptedPin, "PIN");
        } catch (Exception e) {
            log.error("Failed to decrypt PIN", e);
            throw new EncryptionException("Failed to decrypt PIN", e);
        }
    }
    
    /**
     * Create secure hash of sensitive data for indexing/searching
     */
    public String createHash(String data, String type) {
        if (data == null || data.trim().isEmpty()) {
            throw new IllegalArgumentException("Data cannot be null or empty for hashing");
        }
        
        String cacheKey = type + ":" + data;
        
        if (cacheEnabled && hashCache.containsKey(cacheKey)) {
            return hashCache.get(cacheKey);
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            digest.update(type.getBytes(StandardCharsets.UTF_8));
            digest.update(data.getBytes(StandardCharsets.UTF_8));
            
            byte[] hashBytes = digest.digest();
            String hash = Base64.getEncoder().encodeToString(hashBytes);
            
            if (cacheEnabled) {
                hashCache.put(cacheKey, hash);
            }
            
            return hash;
            
        } catch (Exception e) {
            log.error("Failed to create hash for data type: {}", type, e);
            throw new EncryptionException("Failed to create hash", e);
        }
    }
    
    /**
     * Validate that encrypted data can be decrypted (integrity check)
     */
    public boolean validateEncryptedData(String encryptedData, String type) {
        try {
            String decrypted = decrypt(encryptedData, type);
            return decrypted != null && !decrypted.trim().isEmpty();
        } catch (Exception e) {
            log.warn("Encrypted data validation failed for type: {}", type);
            return false;
        }
    }
    
    /**
     * Re-encrypt data with new key (for key rotation)
     */
    public String reencrypt(String encryptedData, String type, SecretKey newKey) {
        try {
            // Decrypt with current key
            String plaintext = decrypt(encryptedData, type);
            
            // Encrypt with new key
            return encryptWithKey(plaintext, type, newKey);
            
        } catch (Exception e) {
            log.error("Failed to re-encrypt data for type: {}", type, e);
            throw new EncryptionException("Failed to re-encrypt data", e);
        }
    }
    
    /**
     * Generate new encryption key for key rotation
     */
    public SecretKey generateNewKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_LENGTH);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            log.error("Failed to generate new encryption key", e);
            throw new EncryptionException("Failed to generate new encryption key", e);
        }
    }
    
    /**
     * Clear sensitive data from cache
     */
    public void clearCache() {
        hashCache.clear();
        log.info("Encryption cache cleared");
    }
    
    // Private helper methods
    
    private String encrypt(String plaintext, String additionalData) {
        return encryptWithKey(plaintext, additionalData, encryptionKey);
    }
    
    private String encryptWithKey(String plaintext, String additionalData, SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
            
            // Add additional authenticated data
            if (additionalData != null) {
                cipher.updateAAD(additionalData.getBytes(StandardCharsets.UTF_8));
            }
            
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and ciphertext
            byte[] encryptedData = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, encryptedData, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, encryptedData, GCM_IV_LENGTH, ciphertext.length);
            
            return Base64.getEncoder().encodeToString(encryptedData);
            
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }
    
    private String decrypt(String encryptedData, String additionalData) {
        try {
            byte[] decodedData = Base64.getDecoder().decode(encryptedData);
            
            if (decodedData.length < GCM_IV_LENGTH) {
                throw new EncryptionException("Invalid encrypted data format");
            }
            
            // Extract IV and ciphertext
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[decodedData.length - GCM_IV_LENGTH];
            
            System.arraycopy(decodedData, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(decodedData, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, gcmSpec);
            
            // Add additional authenticated data
            if (additionalData != null) {
                cipher.updateAAD(additionalData.getBytes(StandardCharsets.UTF_8));
            }
            
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed", e);
        }
    }
    
    private SecretKey deriveKey(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            
            byte[] key = password.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < iterations; i++) {
                digest.update(key);
                key = digest.digest();
                digest.reset();
            }
            
            // Use only first 32 bytes for AES-256
            byte[] aesKey = new byte[32];
            System.arraycopy(key, 0, aesKey, 0, 32);
            
            return new SecretKeySpec(aesKey, ALGORITHM);
            
        } catch (Exception e) {
            throw new EncryptionException("Failed to derive encryption key", e);
        }
    }
}