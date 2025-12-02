package com.waqiti.payment.qrcode.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * QR Code Encryption Service
 * 
 * Provides AES-256-GCM encryption for sensitive QR code data
 * with authenticated encryption and integrity protection.
 * 
 * @version 2.0.0
 * @since 2025-01-15
 */
@Slf4j
@Service
public class QRCodeEncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int AES_KEY_SIZE = 256;
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Encrypt data using AES-256-GCM
     */
    public String encrypt(String plaintext, String keyString) {
        try {
            // Generate IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // Decode key
            byte[] keyBytes = Base64.getDecoder().decode(keyString);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);
            
            // Encrypt data
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = cipher.doFinal(plaintextBytes);
            
            // Combine IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);
            
            // Encode to Base64
            return Base64.getEncoder().encodeToString(byteBuffer.array());
            
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new QRCodeEncryptionException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Decrypt data using AES-256-GCM
     */
    public String decrypt(String encryptedData, String keyString) {
        try {
            // Decode from Base64
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
            
            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);
            
            // Decode key
            byte[] keyBytes = Base64.getDecoder().decode(keyString);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
            
            // Decrypt data
            byte[] plaintextBytes = cipher.doFinal(ciphertext);
            
            return new String(plaintextBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new QRCodeEncryptionException("Failed to decrypt data", e);
        }
    }
    
    /**
     * Generate a new encryption key
     */
    public String generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(AES_KEY_SIZE, secureRandom);
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            log.error("Key generation failed", e);
            throw new QRCodeEncryptionException("Failed to generate encryption key", e);
        }
    }
    
    /**
     * Generate a secure random token
     */
    public String generateSecureToken(int length) {
        byte[] token = new byte[length];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
    
    /**
     * Encrypt sensitive fields in QR code data
     */
    public String encryptSensitiveData(String data, String key) {
        try {
            // Parse data and identify sensitive fields
            String[] parts = data.split("\\|");
            StringBuilder encrypted = new StringBuilder();
            
            for (String part : parts) {
                if (isSensitiveField(part)) {
                    encrypted.append(encrypt(part, key));
                } else {
                    encrypted.append(part);
                }
                encrypted.append("|");
            }
            
            // Remove trailing separator
            if (encrypted.length() > 0) {
                encrypted.setLength(encrypted.length() - 1);
            }
            
            return encrypted.toString();
            
        } catch (Exception e) {
            log.error("Failed to encrypt sensitive data", e);
            throw new QRCodeEncryptionException("Failed to encrypt sensitive data", e);
        }
    }
    
    /**
     * Check if field contains sensitive data
     */
    private boolean isSensitiveField(String field) {
        // Fields containing these keywords are considered sensitive
        String[] sensitiveKeywords = {
            "account", "card", "pin", "cvv", "ssn", "password",
            "secret", "private", "bank", "routing"
        };
        
        String fieldLower = field.toLowerCase();
        for (String keyword : sensitiveKeywords) {
            if (fieldLower.contains(keyword)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Validate encryption key format
     */
    public boolean isValidKey(String keyString) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyString);
            return keyBytes.length == AES_KEY_SIZE / 8;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Generate QR code signature for integrity
     */
    public String generateSignature(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                Base64.getDecoder().decode(key), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
            
        } catch (Exception e) {
            log.error("Signature generation failed", e);
            throw new QRCodeEncryptionException("Failed to generate signature", e);
        }
    }
    
    /**
     * Verify QR code signature
     */
    public boolean verifySignature(String data, String signature, String key) {
        try {
            String calculatedSignature = generateSignature(data, key);
            return calculatedSignature.equals(signature);
        } catch (Exception e) {
            log.error("Signature verification failed", e);
            return false;
        }
    }
    
    /**
     * Encryption exception class
     */
    public static class QRCodeEncryptionException extends RuntimeException {
        public QRCodeEncryptionException(String message) {
            super(message);
        }
        
        public QRCodeEncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}