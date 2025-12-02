package com.waqiti.common.vault;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback implementation of VaultSecretService for environments without HashiCorp Vault
 * Provides encrypted in-memory storage with AES-256-GCM encryption
 */
@Slf4j
@Service
@ConditionalOnProperty(value = "vault.enabled", havingValue = "false", matchIfMissing = true)
public class FallbackVaultSecretService extends VaultSecretService {
    
    public FallbackVaultSecretService() {
        super(null); // Pass null as VaultTemplate is not needed for fallback
    }
    
    private final Map<String, EncryptedData> secretStore = new ConcurrentHashMap<>();
    private SecretKey masterKey;
    private final SecureRandom secureRandom = new SecureRandom();
    
    @Value("${vault.fallback.master-key:#{null}}")
    private String configuredMasterKey;
    
    @PostConstruct
    @Override
    public void initialize() {
        try {
            if (configuredMasterKey != null && !configuredMasterKey.isEmpty()) {
                // Use configured master key (should be externally managed)
                byte[] keyBytes = Base64.getDecoder().decode(configuredMasterKey);
                masterKey = new SecretKeySpec(keyBytes, "AES");
            } else {
                // Generate a new master key (for development/testing only)
                KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                keyGen.init(256, secureRandom);
                masterKey = keyGen.generateKey();
                log.warn("Generated ephemeral master key - THIS IS NOT SUITABLE FOR PRODUCTION");
                log.warn("Configure vault.fallback.master-key for persistent secret storage");
            }
            
            log.info("FallbackVaultSecretService initialized - Using encrypted in-memory storage");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize FallbackVaultSecretService", e);
        }
    }
    
    @Override
    public String getSecret(String path, String context) {
        String fullPath = context + "/" + path;
        
        EncryptedData encryptedData = secretStore.get(fullPath);
        if (encryptedData == null) {
            log.debug("Secret not found: {}", fullPath);
            return null;
        }
        
        try {
            String decrypted = decrypt(encryptedData);
            log.debug("Retrieved secret: {}", fullPath);
            return decrypted;
        } catch (Exception e) {
            log.error("Failed to decrypt secret: {}", fullPath, e);
            throw new RuntimeException("Failed to retrieve secret: " + path, e);
        }
    }
    
    @Override
    public void saveSecret(String path, String value, String context) {
        String fullPath = context + "/" + path;
        
        try {
            EncryptedData encrypted = encrypt(value);
            secretStore.put(fullPath, encrypted);
            log.info("Stored secret: {}", fullPath);
        } catch (Exception e) {
            log.error("Failed to encrypt secret: {}", fullPath, e);
            throw new RuntimeException("Failed to store secret: " + path, e);
        }
    }
    
    @Override
    public void deleteSecret(String path, String context) {
        String fullPath = context + "/" + path;
        
        EncryptedData removed = secretStore.remove(fullPath);
        if (removed != null) {
            log.info("Deleted secret: {}", fullPath);
        } else {
            log.debug("Secret not found for deletion: {}", fullPath);
        }
    }
    
    @Override
    public boolean secretExists(String path, String context) {
        String fullPath = context + "/" + path;
        return secretStore.containsKey(fullPath);
    }
    
    @Override
    public String encryptData(String plaintext) {
        try {
            EncryptedData encrypted = encrypt(plaintext);
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(encrypted.iv.length + encrypted.ciphertext.length)
                    .put(encrypted.iv)
                    .put(encrypted.ciphertext)
                    .array()
            );
        } catch (Exception e) {
            log.error("Failed to encrypt data", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }
    
    @Override
    public String decryptData(String ciphertext) {
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            
            byte[] iv = new byte[12];
            buffer.get(iv);
            
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            
            return decrypt(new EncryptedData(iv, encrypted));
        } catch (Exception e) {
            log.error("Failed to decrypt data", e);
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }
    
    @Override
    public void rotateKeys() {
        try {
            // Generate new master key
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, secureRandom);
            SecretKey newKey = keyGen.generateKey();
            
            // Re-encrypt all secrets
            Map<String, String> decryptedSecrets = new ConcurrentHashMap<>();
            for (Map.Entry<String, EncryptedData> entry : secretStore.entrySet()) {
                String decrypted = decrypt(entry.getValue());
                decryptedSecrets.put(entry.getKey(), decrypted);
            }
            
            // Switch to new key
            masterKey = newKey;
            
            // Re-encrypt with new key
            for (Map.Entry<String, String> entry : decryptedSecrets.entrySet()) {
                EncryptedData encrypted = encrypt(entry.getValue());
                secretStore.put(entry.getKey(), encrypted);
            }
            
            log.info("Successfully rotated encryption keys - Re-encrypted {} secrets", decryptedSecrets.size());
        } catch (Exception e) {
            log.error("Failed to rotate keys", e);
            throw new RuntimeException("Failed to rotate keys", e);
        }
    }
    
    @Override
    public Map<String, Object> getSecretMetadata(String path, String context) {
        String fullPath = context + "/" + path;
        
        Map<String, Object> metadata = new ConcurrentHashMap<>();
        metadata.put("exists", secretStore.containsKey(fullPath));
        metadata.put("path", fullPath);
        metadata.put("encrypted", true);
        metadata.put("provider", "FallbackVaultSecretService");
        
        if (secretStore.containsKey(fullPath)) {
            try {
                EncryptedData data = secretStore.get(fullPath);
                metadata.put("size", data.ciphertext.length);
                metadata.put("hash", computeHash(fullPath));
            } catch (Exception e) {
                log.error("Failed to get metadata for: {}", fullPath, e);
            }
        }
        
        return metadata;
    }
    
    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        metrics.put("provider", "FallbackVaultSecretService");
        metrics.put("secretCount", secretStore.size());
        metrics.put("encryptionAlgorithm", "AES-256-GCM");
        metrics.put("healthy", true);
        
        long totalSize = secretStore.values().stream()
            .mapToLong(data -> data.ciphertext.length)
            .sum();
        metrics.put("totalSizeBytes", totalSize);
        
        return metrics;
    }
    
    private EncryptedData encrypt(String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        
        // Generate IV
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        
        // Initialize cipher
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);
        
        // Encrypt
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        
        return new EncryptedData(iv, ciphertext);
    }
    
    private String decrypt(EncryptedData encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        
        // Initialize cipher
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, encryptedData.iv);
        cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);
        
        // Decrypt
        byte[] plaintext = cipher.doFinal(encryptedData.ciphertext);
        
        return new String(plaintext, StandardCharsets.UTF_8);
    }
    
    private String computeHash(String data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
    
    private static class EncryptedData {
        final byte[] iv;
        final byte[] ciphertext;
        
        EncryptedData(byte[] iv, byte[] ciphertext) {
            this.iv = iv;
            this.ciphertext = ciphertext;
        }
    }
}