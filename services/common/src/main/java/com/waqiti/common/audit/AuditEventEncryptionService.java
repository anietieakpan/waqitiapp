package com.waqiti.common.audit;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting audit event data for compliance
 */
@Service
@Slf4j
public class AuditEventEncryptionService {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    
    private final SecretKey encryptionKey;
    
    public AuditEventEncryptionService() {
        this.encryptionKey = generateKey();
    }
    
    /**
     * Encrypt audit event data
     */
    public String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("Failed to encrypt audit data", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    /**
     * Decrypt audit event data
     */
    public String decrypt(String encryptedText) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decryptedBytes);
        } catch (Exception e) {
            log.error("Failed to decrypt audit data", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
    
    /**
     * Generate integrity hash for audit event
     */
    public String generateIntegrityHash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to generate integrity hash", e);
            throw new RuntimeException("Hash generation failed", e);
        }
    }
    
    /**
     * Generate encryption key
     */
    private SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(256, new SecureRandom());
            return keyGen.generateKey();
        } catch (Exception e) {
            log.error("Failed to generate encryption key", e);
            throw new RuntimeException("Key generation failed", e);
        }
    }
}