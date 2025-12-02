package com.waqiti.common.security.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Service for encryption and decryption operations
 */
@Service
public class EncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int SALT_LENGTH = 16;
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;
    
    @Value("${encryption.master.key}")
    private String masterKey;
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Encrypt data using AES-GCM
     */
    public String encrypt(String plaintext) {
        try {
            byte[] salt = new byte[SALT_LENGTH];
            secureRandom.nextBytes(salt);
            
            SecretKey secretKey = deriveKey(masterKey, salt);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine salt + iv + ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(salt.length + iv.length + cipherText.length);
            byteBuffer.put(salt);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    /**
     * Decrypt data encrypted with AES-GCM
     */
    public String decrypt(String encryptedData) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedData);
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            
            byte[] salt = new byte[SALT_LENGTH];
            byteBuffer.get(salt);
            
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);
            
            SecretKey secretKey = deriveKey(masterKey, salt);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
    
    /**
     * Derive key from master key and salt using PBKDF2
     */
    private SecretKey deriveKey(String masterKey, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(masterKey.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }
    
    /**
     * Generate a new encryption key
     */
    public String generateKey() {
        byte[] key = new byte[32]; // 256 bits
        secureRandom.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}