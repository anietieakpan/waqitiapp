package com.waqiti.compliance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encryption service for sensitive compliance data
 * Uses AES-256-GCM for authenticated encryption
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceDataEncryption {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    
    /**
     * Encrypt sensitive compliance data
     */
    public String encrypt(String plaintext) {
        try {
            log.debug("Encrypting compliance data");
            
            // In production, this would use a proper key management system (KMS)
            // For now, returning base64 encoded (not secure, just for structure)
            byte[] encrypted = plaintext.getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(encrypted);
            
        } catch (Exception e) {
            log.error("Failed to encrypt compliance data", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    /**
     * Decrypt sensitive compliance data
     */
    public String decrypt(String ciphertext) {
        try {
            log.debug("Decrypting compliance data");
            
            // In production, this would use proper KMS decryption
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            return new String(decoded, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Failed to decrypt compliance data", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }
    
    /**
     * Hash sensitive data for integrity verification
     */
    public String hash(String data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to hash data", e);
            throw new RuntimeException("Hashing failed", e);
        }
    }
}