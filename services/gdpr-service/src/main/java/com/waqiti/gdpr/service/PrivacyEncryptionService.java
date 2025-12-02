package com.waqiti.gdpr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Privacy Encryption Service with Compression Support
 *
 * Production-ready encryption service for GDPR data exports with:
 * - AES-256-GCM encryption
 * - GZIP compression
 * - Secure key management
 * - Checksum generation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivacyEncryptionService {

    @Value("${gdpr.encryption.algorithm:AES}")
    private String algorithm;

    @Value("${gdpr.encryption.key-size:256}")
    private int keySize;

    @Value("${gdpr.encryption.transformation:AES/GCM/NoPadding}")
    private String transformation;

    @Value("${gdpr.encryption.gcm-tag-length:128}")
    private int gcmTagLength;

    private static final int GCM_IV_LENGTH = 12; // 96 bits recommended for GCM
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypt data with AES-256-GCM
     *
     * @param data plaintext data
     * @param key encryption key (base64 encoded)
     * @return encrypted result with IV and encrypted data
     */
    public EncryptionResult encrypt(byte[] data, String key) {
        try {
            long startTime = System.currentTimeMillis();

            // Decode key
            byte[] decodedKey = Base64.getDecoder().decode(key);
            SecretKey secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, algorithm);

            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(transformation);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            // Encrypt
            byte[] encryptedData = cipher.doFinal(data);

            // Combine IV + encrypted data
            byte[] combined = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

            long processingTime = System.currentTimeMillis() - startTime;

            log.debug("Data encrypted: originalSize={}, encryptedSize={}, time={}ms",
                    data.length, combined.length, processingTime);

            return EncryptionResult.builder()
                    .encryptedData(combined)
                    .iv(Base64.getEncoder().encodeToString(iv))
                    .keyId(generateKeyId(key))
                    .algorithm(transformation)
                    .processingTimeMs(processingTime)
                    .build();

        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage(), e);
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }

    /**
     * Decrypt data encrypted with AES-256-GCM
     *
     * @param encryptedData encrypted data (IV + ciphertext)
     * @param key encryption key (base64 encoded)
     * @return decrypted plaintext
     */
    public byte[] decrypt(byte[] encryptedData, String key) {
        try {
            long startTime = System.currentTimeMillis();

            // Decode key
            byte[] decodedKey = Base64.getDecoder().decode(key);
            SecretKey secretKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, algorithm);

            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH);

            // Extract ciphertext
            byte[] ciphertext = new byte[encryptedData.length - GCM_IV_LENGTH];
            System.arraycopy(encryptedData, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(transformation);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            // Decrypt
            byte[] decryptedData = cipher.doFinal(ciphertext);

            long processingTime = System.currentTimeMillis() - startTime;

            log.debug("Data decrypted: encryptedSize={}, decryptedSize={}, time={}ms",
                    encryptedData.length, decryptedData.length, processingTime);

            return decryptedData;

        } catch (Exception e) {
            log.error("Decryption failed: {}", e.getMessage(), e);
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }

    /**
     * Generate a new encryption key
     *
     * @return base64 encoded encryption key
     */
    public String generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(algorithm);
            keyGen.init(keySize, secureRandom);
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            log.error("Key generation failed: {}", e.getMessage(), e);
            throw new EncryptionException("Failed to generate encryption key", e);
        }
    }

    /**
     * Compress data using GZIP
     *
     * @param data uncompressed data
     * @return compressed data
     */
    public byte[] compress(byte[] data) {
        try {
            long startTime = System.currentTimeMillis();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(data);
            }

            byte[] compressed = baos.toByteArray();
            long processingTime = System.currentTimeMillis() - startTime;

            double compressionRatio = (1.0 - ((double) compressed.length / data.length)) * 100;

            log.debug("Data compressed: originalSize={}, compressedSize={}, ratio={:.2f}%, time={}ms",
                    data.length, compressed.length, compressionRatio, processingTime);

            return compressed;

        } catch (Exception e) {
            log.error("Compression failed: {}", e.getMessage(), e);
            throw new EncryptionException("Failed to compress data", e);
        }
    }

    /**
     * Decompress GZIP data
     *
     * @param compressedData compressed data
     * @return decompressed data
     */
    public byte[] decompress(byte[] compressedData) {
        try {
            long startTime = System.currentTimeMillis();

            ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            try (GZIPInputStream gzip = new GZIPInputStream(bais)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = gzip.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
            }

            byte[] decompressed = baos.toByteArray();
            long processingTime = System.currentTimeMillis() - startTime;

            log.debug("Data decompressed: compressedSize={}, decompressedSize={}, time={}ms",
                    compressedData.length, decompressed.length, processingTime);

            return decompressed;

        } catch (Exception e) {
            log.error("Decompression failed: {}", e.getMessage(), e);
            throw new EncryptionException("Failed to decompress data", e);
        }
    }

    /**
     * Compress and encrypt data (recommended for data exports)
     *
     * @param data plaintext data
     * @param key encryption key
     * @return encryption result with compressed + encrypted data
     */
    public EncryptionResult compressAndEncrypt(byte[] data, String key) {
        byte[] compressed = compress(data);
        EncryptionResult result = encrypt(compressed, key);
        result.setCompressed(true);
        result.setOriginalSize((long) data.length);
        result.setCompressedSize((long) compressed.length);
        return result;
    }

    /**
     * Decrypt and decompress data
     *
     * @param encryptedData encrypted data
     * @param key encryption key
     * @return decompressed plaintext
     */
    public byte[] decryptAndDecompress(byte[] encryptedData, String key) {
        byte[] decrypted = decrypt(encryptedData, key);
        return decompress(decrypted);
    }

    /**
     * Generate SHA-256 checksum for data integrity
     *
     * @param data data to checksum
     * @return base64 encoded SHA-256 checksum
     */
    public String generateChecksum(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Checksum generation failed: {}", e.getMessage(), e);
            throw new EncryptionException("Failed to generate checksum", e);
        }
    }

    /**
     * Verify checksum
     *
     * @param data data to verify
     * @param expectedChecksum expected checksum
     * @return true if checksum matches
     */
    public boolean verifyChecksum(byte[] data, String expectedChecksum) {
        String actualChecksum = generateChecksum(data);
        return actualChecksum.equals(expectedChecksum);
    }

    /**
     * Generate unique key ID from key (for tracking)
     *
     * @param key base64 encoded key
     * @return key ID (first 8 chars of SHA-256 hash)
     */
    private String generateKeyId(String key) {
        String hash = generateChecksum(key.getBytes());
        return hash.substring(0, 8);
    }

    // Result classes

    @lombok.Data
    @lombok.Builder
    public static class EncryptionResult {
        private byte[] encryptedData;
        private String iv;
        private String keyId;
        private String algorithm;
        private boolean compressed;
        private Long originalSize;
        private Long compressedSize;
        private Long processingTimeMs;
    }

    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
