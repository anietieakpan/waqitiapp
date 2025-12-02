package com.waqiti.config.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Production-grade encryption service for configuration values with Vault integration
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConfigEncryptionService {

    private final VaultTemplate vaultTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${config.encryption.vault.path:secret/config}")
    private String vaultEncryptionPath;

    @Value("${config.encryption.vault.key:encryption-key}")
    private String vaultEncryptionKey;

    @Value("${config.encryption.algorithm:AES/GCM/NoPadding}")
    private String encryptionAlgorithm;

    @Value("${config.encryption.key.size:256}")
    private int keySize;

    @Value("${config.encryption.gcm.iv.length:12}")
    private int gcmIvLength;

    @Value("${config.encryption.gcm.tag.length:16}")
    private int gcmTagLength;

    private static final String VAULT_KEY_NAME = "encryption-key";
    private static final String ENCRYPTED_PREFIX = "ENC(";
    private static final String ENCRYPTED_SUFFIX = ")";

    /**
     * Encrypt configuration value using AES-GCM with Vault-managed keys
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            // Check if already encrypted
            if (isEncrypted(plaintext)) {
                log.debug("Value is already encrypted, skipping encryption");
                return plaintext;
            }

            SecretKey secretKey = getOrCreateEncryptionKey();
            
            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            
            // Generate random IV for GCM
            byte[] iv = new byte[gcmIvLength];
            secureRandom.nextBytes(iv);
            
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            
            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV and encrypted data
            byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);
            
            String base64Encrypted = Base64.getEncoder().encodeToString(encryptedWithIv);
            String result = ENCRYPTED_PREFIX + base64Encrypted + ENCRYPTED_SUFFIX;
            
            log.debug("Successfully encrypted configuration value");
            return result;
            
        } catch (Exception e) {
            log.error("Failed to encrypt configuration value", e);
            throw new ConfigurationEncryptionException("Failed to encrypt configuration value", e);
        }
    }

    /**
     * Decrypt configuration value using AES-GCM with Vault-managed keys
     */
    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return encryptedValue;
        }

        try {
            // Check if actually encrypted
            if (!isEncrypted(encryptedValue)) {
                log.debug("Value is not encrypted, returning as-is");
                return encryptedValue;
            }

            // Extract encrypted data from wrapper
            String base64Data = encryptedValue.substring(
                ENCRYPTED_PREFIX.length(), 
                encryptedValue.length() - ENCRYPTED_SUFFIX.length()
            );
            
            byte[] encryptedWithIv = Base64.getDecoder().decode(base64Data);
            
            // Extract IV and encrypted data
            byte[] iv = new byte[gcmIvLength];
            byte[] encryptedData = new byte[encryptedWithIv.length - gcmIvLength];
            
            System.arraycopy(encryptedWithIv, 0, iv, 0, gcmIvLength);
            System.arraycopy(encryptedWithIv, gcmIvLength, encryptedData, 0, encryptedData.length);
            
            SecretKey secretKey = getOrCreateEncryptionKey();
            
            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
            
            byte[] decryptedData = cipher.doFinal(encryptedData);
            String result = new String(decryptedData, StandardCharsets.UTF_8);
            
            log.debug("Successfully decrypted configuration value");
            return result;
            
        } catch (Exception e) {
            log.error("Failed to decrypt configuration value", e);
            throw new ConfigurationEncryptionException("Failed to decrypt configuration value", e);
        }
    }

    /**
     * Check if a value is encrypted (wrapped in ENC())
     */
    public boolean isEncrypted(String value) {
        return value != null && 
               value.startsWith(ENCRYPTED_PREFIX) && 
               value.endsWith(ENCRYPTED_SUFFIX) &&
               value.length() > (ENCRYPTED_PREFIX.length() + ENCRYPTED_SUFFIX.length());
    }

    /**
     * Re-encrypt all configurations with new key (for key rotation)
     */
    public String reencrypt(String currentEncryptedValue, String newKeyId) {
        if (!isEncrypted(currentEncryptedValue)) {
            throw new IllegalArgumentException("Value is not encrypted");
        }

        try {
            // Decrypt with current key
            String plaintext = decrypt(currentEncryptedValue);
            
            // Encrypt with new key (after rotating keys in Vault)
            rotateEncryptionKey(newKeyId);
            
            return encrypt(plaintext);
            
        } catch (Exception e) {
            log.error("Failed to re-encrypt configuration value", e);
            throw new ConfigurationEncryptionException("Failed to re-encrypt configuration value", e);
        }
    }

    /**
     * Encrypt multiple values in batch for performance
     */
    public Map<String, String> encryptBatch(Map<String, String> plaintextValues) {
        log.info("Performing batch encryption of {} values", plaintextValues.size());
        
        try {
            SecretKey secretKey = getOrCreateEncryptionKey();
            
            return plaintextValues.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> {
                        try {
                            return encryptSingleValue(entry.getValue(), secretKey);
                        } catch (Exception e) {
                            log.warn("Failed to encrypt value for key: {}", entry.getKey(), e);
                            return entry.getValue(); // Return original on failure
                        }
                    }
                ));
                
        } catch (Exception e) {
            log.error("Failed to perform batch encryption", e);
            throw new ConfigurationEncryptionException("Failed to perform batch encryption", e);
        }
    }

    /**
     * Decrypt multiple values in batch for performance
     */
    public Map<String, String> decryptBatch(Map<String, String> encryptedValues) {
        log.info("Performing batch decryption of {} values", encryptedValues.size());
        
        try {
            SecretKey secretKey = getOrCreateEncryptionKey();
            
            return encryptedValues.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> {
                        try {
                            return decryptSingleValue(entry.getValue(), secretKey);
                        } catch (Exception e) {
                            log.warn("Failed to decrypt value for key: {}", entry.getKey(), e);
                            return entry.getValue(); // Return original on failure
                        }
                    }
                ));
                
        } catch (Exception e) {
            log.error("Failed to perform batch decryption", e);
            throw new ConfigurationEncryptionException("Failed to perform batch decryption", e);
        }
    }

    /**
     * Validate encryption key and connectivity to Vault
     */
    public boolean validateEncryption() {
        try {
            // Test encrypt/decrypt cycle
            String testValue = "test-encryption-" + System.currentTimeMillis();
            String encrypted = encrypt(testValue);
            String decrypted = decrypt(encrypted);
            
            boolean isValid = testValue.equals(decrypted);
            
            if (isValid) {
                log.info("Encryption validation successful");
            } else {
                log.error("Encryption validation failed: decrypt result doesn't match original");
            }
            
            return isValid;
            
        } catch (Exception e) {
            log.error("Encryption validation failed", e);
            return false;
        }
    }

    /**
     * Get encryption key statistics and health information
     */
    public EncryptionHealthInfo getEncryptionHealth() {
        try {
            boolean vaultConnected = testVaultConnection();
            boolean keyExists = checkEncryptionKeyExists();
            boolean encryptionWorking = validateEncryption();
            
            return EncryptionHealthInfo.builder()
                .vaultConnected(vaultConnected)
                .encryptionKeyExists(keyExists)
                .encryptionWorking(encryptionWorking)
                .algorithm(encryptionAlgorithm)
                .keySize(keySize)
                .vaultPath(vaultEncryptionPath)
                .lastValidated(java.time.Instant.now())
                .status(vaultConnected && keyExists && encryptionWorking ? "HEALTHY" : "UNHEALTHY")
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get encryption health info", e);
            return EncryptionHealthInfo.builder()
                .vaultConnected(false)
                .encryptionKeyExists(false)
                .encryptionWorking(false)
                .status("ERROR")
                .error(e.getMessage())
                .lastValidated(java.time.Instant.now())
                .build();
        }
    }

    private SecretKey getOrCreateEncryptionKey() {
        try {
            // Try to get existing key from Vault
            VaultResponse response = vaultTemplate.read(vaultEncryptionPath);
            
            if (response != null && response.getData() != null) {
                String base64Key = (String) response.getData().get(VAULT_KEY_NAME);
                if (base64Key != null) {
                    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
                    return new SecretKeySpec(keyBytes, "AES");
                }
            }
            
            // Generate new key if not found
            log.info("Generating new encryption key");
            return generateAndStoreNewKey();
            
        } catch (Exception e) {
            log.error("Failed to get encryption key from Vault", e);
            throw new ConfigurationEncryptionException("Failed to get encryption key", e);
        }
    }

    private SecretKey generateAndStoreNewKey() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(keySize);
        SecretKey secretKey = keyGenerator.generateKey();
        
        // Store in Vault
        String base64Key = Base64.getEncoder().encodeToString(secretKey.getEncoded());
        
        Map<String, Object> keyData = Map.of(
            VAULT_KEY_NAME, base64Key,
            "created", java.time.Instant.now().toString(),
            "algorithm", "AES",
            "keySize", keySize
        );
        
        vaultTemplate.write(vaultEncryptionPath, keyData);
        
        log.info("Successfully generated and stored new encryption key in Vault");
        return secretKey;
    }

    private void rotateEncryptionKey(String newKeyId) {
        try {
            // Generate new key
            SecretKey newKey = generateAndStoreNewKey();
            
            // Archive old key with timestamp
            String archivePath = vaultEncryptionPath + "/archive/" + java.time.Instant.now().toEpochMilli();
            VaultResponse oldKeyResponse = vaultTemplate.read(vaultEncryptionPath);
            if (oldKeyResponse != null) {
                vaultTemplate.write(archivePath, oldKeyResponse.getData());
            }
            
            log.info("Successfully rotated encryption key");
            
        } catch (Exception e) {
            log.error("Failed to rotate encryption key", e);
            throw new ConfigurationEncryptionException("Failed to rotate encryption key", e);
        }
    }

    private String encryptSingleValue(String plaintext, SecretKey secretKey) throws Exception {
        if (!isEncrypted(plaintext)) {
            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            
            byte[] iv = new byte[gcmIvLength];
            secureRandom.nextBytes(iv);
            
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            
            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            byte[] encryptedWithIv = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedData, 0, encryptedWithIv, iv.length, encryptedData.length);
            
            String base64Encrypted = Base64.getEncoder().encodeToString(encryptedWithIv);
            return ENCRYPTED_PREFIX + base64Encrypted + ENCRYPTED_SUFFIX;
        }
        
        return plaintext; // Already encrypted
    }

    private String decryptSingleValue(String encryptedValue, SecretKey secretKey) throws Exception {
        if (isEncrypted(encryptedValue)) {
            String base64Data = encryptedValue.substring(
                ENCRYPTED_PREFIX.length(), 
                encryptedValue.length() - ENCRYPTED_SUFFIX.length()
            );
            
            byte[] encryptedWithIv = Base64.getDecoder().decode(base64Data);
            
            byte[] iv = new byte[gcmIvLength];
            byte[] encryptedData = new byte[encryptedWithIv.length - gcmIvLength];
            
            System.arraycopy(encryptedWithIv, 0, iv, 0, gcmIvLength);
            System.arraycopy(encryptedWithIv, gcmIvLength, encryptedData, 0, encryptedData.length);
            
            Cipher cipher = Cipher.getInstance(encryptionAlgorithm);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
            
            byte[] decryptedData = cipher.doFinal(encryptedData);
            return new String(decryptedData, StandardCharsets.UTF_8);
        }
        
        return encryptedValue; // Not encrypted
    }

    private boolean testVaultConnection() {
        try {
            vaultTemplate.opsForSys().health();
            return true;
        } catch (Exception e) {
            log.warn("Vault connection test failed", e);
            return false;
        }
    }

    private boolean checkEncryptionKeyExists() {
        try {
            VaultResponse response = vaultTemplate.read(vaultEncryptionPath);
            return response != null && 
                   response.getData() != null && 
                   response.getData().containsKey(VAULT_KEY_NAME);
        } catch (Exception e) {
            log.warn("Failed to check encryption key existence", e);
            return false;
        }
    }

    // Health info DTO
    @lombok.Data
    @lombok.Builder
    public static class EncryptionHealthInfo {
        private boolean vaultConnected;
        private boolean encryptionKeyExists;
        private boolean encryptionWorking;
        private String algorithm;
        private int keySize;
        private String vaultPath;
        private String status;
        private String error;
        private java.time.Instant lastValidated;
    }

    // Custom exception
    public static class ConfigurationEncryptionException extends RuntimeException {
        public ConfigurationEncryptionException(String message) {
            super(message);
        }

        public ConfigurationEncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}