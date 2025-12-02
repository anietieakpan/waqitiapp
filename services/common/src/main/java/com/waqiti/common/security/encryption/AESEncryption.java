package com.waqiti.common.security.encryption;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES encryption utility for secure data encryption and decryption.
 * Uses AES-256-GCM for authenticated encryption with additional data (AEAD).
 */
@Slf4j
@Component
public class AESEncryption {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH = 256;
    private static final int IV_LENGTH = 12; // 96 bits for GCM
    private static final int TAG_LENGTH = 16; // 128 bits for GCM tag
    
    private final SecureRandom secureRandom;
    
    public AESEncryption() {
        this.secureRandom = new SecureRandom();
    }
    
    /**
     * Generate a new AES-256 key
     */
    public SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_LENGTH);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            log.error("Failed to generate AES key", e);
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }
    
    /**
     * Create SecretKey from byte array
     */
    public SecretKey createKeyFromBytes(byte[] keyBytes) {
        if (keyBytes.length != 32) { // 256 bits
            throw new IllegalArgumentException("Key must be 256 bits (32 bytes)");
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
    
    /**
     * Create SecretKey from Base64 encoded string
     */
    public SecretKey createKeyFromString(String keyString) {
        byte[] keyBytes = Base64.getDecoder().decode(keyString);
        return createKeyFromBytes(keyBytes);
    }
    
    /**
     * Encrypt plaintext using AES-GCM
     */
    public EncryptionResult encrypt(String plaintext, SecretKey key) {
        return encrypt(plaintext.getBytes(), key);
    }
    
    /**
     * Encrypt data using AES-GCM
     */
    public EncryptionResult encrypt(byte[] plaintext, SecretKey key) {
        try {
            // Generate random IV
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
            
            // Encrypt data
            byte[] ciphertext = cipher.doFinal(plaintext);
            
            return new EncryptionResult(ciphertext, iv);
            
        } catch (Exception e) {
            log.error("Failed to encrypt data", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Decrypt ciphertext using AES-GCM
     */
    public String decryptToString(EncryptionResult encryptionResult, SecretKey key) {
        byte[] decrypted = decrypt(encryptionResult, key);
        return new String(decrypted);
    }
    
    /**
     * Decrypt data using AES-GCM
     */
    public byte[] decrypt(EncryptionResult encryptionResult, SecretKey key) {
        try {
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec ivSpec = new IvParameterSpec(encryptionResult.getIv());
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
            
            // Decrypt data
            return cipher.doFinal(encryptionResult.getCiphertext());
            
        } catch (Exception e) {
            log.error("Failed to decrypt data", e);
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }
    
    /**
     * Encrypt and encode to Base64 string
     */
    public String encryptToBase64(String plaintext, SecretKey key) {
        EncryptionResult result = encrypt(plaintext, key);
        return result.toBase64();
    }
    
    /**
     * Decrypt from Base64 encoded string
     */
    public String decryptFromBase64(String base64Data, SecretKey key) {
        EncryptionResult result = EncryptionResult.fromBase64(base64Data);
        return decryptToString(result, key);
    }
    
    /**
     * Encryption result containing ciphertext and IV
     */
    public static class EncryptionResult {
        private final byte[] ciphertext;
        private final byte[] iv;
        
        public EncryptionResult(byte[] ciphertext, byte[] iv) {
            this.ciphertext = ciphertext;
            this.iv = iv;
        }
        
        public byte[] getCiphertext() {
            return ciphertext;
        }
        
        public byte[] getIv() {
            return iv;
        }
        
        /**
         * Encode to Base64 string (IV + Ciphertext)
         */
        public String toBase64() {
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        }
        
        /**
         * Decode from Base64 string
         */
        public static EncryptionResult fromBase64(String base64Data) {
            byte[] combined = Base64.getDecoder().decode(base64Data);
            
            if (combined.length < IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted data format");
            }
            
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);
            
            return new EncryptionResult(ciphertext, iv);
        }
    }
    
    /**
     * Securely clear sensitive data from memory
     */
    public static void clearSensitiveData(byte[] data) {
        if (data != null) {
            java.util.Arrays.fill(data, (byte) 0);
        }
    }
    
    /**
     * Get key as Base64 encoded string
     */
    public static String keyToBase64(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
}