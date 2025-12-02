package com.waqiti.security.encryption;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.waqiti.common.audit.AuditService;
import com.waqiti.security.service.VaultSecretService;
import com.waqiti.security.exception.EncryptionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import jakarta.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Secure Encryption Service
 * 
 * Provides field-level encryption capabilities with:
 * - Keys managed by HashiCorp Vault
 * - AES-256-GCM encryption
 * - Key rotation support
 * - Secure key caching
 * - Audit logging
 * 
 * SECURITY: Replaces hardcoded encryption keys with Vault-managed keys
 */
@Service
@Slf4j
public class SecureEncryptionService {
    
    private final VaultSecretService vaultService;
    private final AuditService auditService;
    
    @Value("${encryption.key-rotation-hours:24}")
    private int keyRotationHours;
    
    @Value("${encryption.cache-size:1000}")
    private int cacheSize;
    
    @Value("${encryption.algorithm:AES}")
    private String algorithm;
    
    @Value("${encryption.transformation:AES/GCM/NoPadding}")
    private String transformation;
    
    @Value("${encryption.key-length:256}")
    private int keyLength;
    
    @Value("${encryption.gcm-iv-length:12}")
    private int gcmIvLength;
    
    @Value("${encryption.gcm-tag-length:16}")
    private int gcmTagLength;
    
    // Cache for encryption keys (short-lived for security)
    private final Cache<String, SecretKey> keyCache;
    
    // Cache for encrypted data (for deduplication)
    private final Cache<String, String> encryptionCache;
    
    private final SecureRandom secureRandom;
    
    public SecureEncryptionService(VaultSecretService vaultService, AuditService auditService) {
        this.vaultService = vaultService;
        this.auditService = auditService;
        this.secureRandom = new SecureRandom();
        
        // Initialize key cache with short expiration for security
        this.keyCache = CacheBuilder.newBuilder()
            .expireAfterWrite(keyRotationHours, TimeUnit.HOURS)
            .maximumSize(100)
            .removalListener(notification -> {
                log.debug("Encryption key evicted from cache: {}", notification.getKey());
            })
            .build();
        
        // Initialize encryption cache
        this.encryptionCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(cacheSize)
            .build();
    }
    
    @PostConstruct
    public void init() {
        log.info("Initializing Secure Encryption Service");
        log.info("Algorithm: {}, Key Length: {} bits", algorithm, keyLength);
        
        // Test encryption/decryption to ensure configuration is correct
        try {
            String testData = "test-encryption-" + System.currentTimeMillis();
            String encrypted = encrypt(testData, "default");
            String decrypted = decrypt(encrypted, "default");
            
            if (!testData.equals(decrypted)) {
                throw new EncryptionException("Encryption test failed");
            }
            
            log.info("Encryption service initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize encryption service", e);
            throw new RuntimeException("Encryption service initialization failed", e);
        }
    }
    
    /**
     * Encrypt sensitive data using Vault-managed keys
     * 
     * @param plaintext The data to encrypt
     * @param keyContext The context for key retrieval (e.g., "pii", "financial", "default")
     * @return Base64-encoded encrypted data with IV prepended
     */
    @Nullable
    public String encrypt(@Nullable String plaintext, @NonNull String keyContext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            // Check cache first
            String cacheKey = generateCacheKey(plaintext, keyContext);
            String cached = encryptionCache.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }
            
            // Get encryption key from Vault
            SecretKey secretKey = getEncryptionKey(keyContext);
            
            // Generate random IV
            byte[] iv = new byte[gcmIvLength];
            secureRandom.nextBytes(iv);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(transformation);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            
            // Encrypt data
            byte[] encryptedBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Prepend IV to encrypted data
            byte[] encryptedWithIv = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, encryptedWithIv, iv.length, encryptedBytes.length);
            
            // Encode to Base64
            String result = Base64.getEncoder().encodeToString(encryptedWithIv);
            
            // Cache the result
            encryptionCache.put(cacheKey, result);
            
            // Audit encryption operation
            auditService.auditEncryption(keyContext, plaintext.length(), "ENCRYPT");
            
            log.debug("Data encrypted successfully with context: {}", keyContext);
            return result;
            
        } catch (Exception e) {
            log.error("Encryption failed for context: {}", keyContext, e);
            auditService.auditEncryption(keyContext, plaintext.length(), "ENCRYPT_FAILED");
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Decrypt data using Vault-managed keys
     */
    @Nullable
    public String decrypt(@Nullable String encryptedData, @NonNull String keyContext) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }
        
        try {
            // Get encryption key from Vault
            SecretKey secretKey = getEncryptionKey(keyContext);
            
            // Decode from Base64
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedData);
            
            // Extract IV
            byte[] iv = new byte[gcmIvLength];
            System.arraycopy(encryptedWithIv, 0, iv, 0, gcmIvLength);
            
            // Extract encrypted data
            byte[] encryptedBytes = new byte[encryptedWithIv.length - gcmIvLength];
            System.arraycopy(encryptedWithIv, gcmIvLength, encryptedBytes, 0, encryptedBytes.length);
            
            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(transformation);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(gcmTagLength * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
            
            // Decrypt data
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            String result = new String(decryptedBytes, StandardCharsets.UTF_8);
            
            // Audit decryption operation
            auditService.auditEncryption(keyContext, result.length(), "DECRYPT");
            
            log.debug("Data decrypted successfully with context: {}", keyContext);
            return result;
            
        } catch (Exception e) {
            log.error("Decryption failed for context: {}", keyContext, e);
            auditService.auditEncryption(keyContext, 0, "DECRYPT_FAILED");
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }
    
    /**
     * Get encryption key from Vault with caching
     */
    private SecretKey getEncryptionKey(String keyContext) {
        try {
            return keyCache.get(keyContext, () -> {
                log.debug("Loading encryption key from Vault for context: {}", keyContext);
                
                String vaultPath = String.format("secret/encryption-keys/%s", keyContext);
                Map<String, String> keyData = vaultService.getSecretAsMap(vaultPath);
                
                if (keyData == null || !keyData.containsKey("key")) {
                    log.info("No key found for context {}, generating new one", keyContext);
                    generateAndStoreKey(keyContext);
                    keyData = vaultService.getSecretAsMap(vaultPath);
                }
                
                String encodedKey = keyData.get("key");
                byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
                
                return new SecretKeySpec(keyBytes, algorithm);
            });
            
        } catch (Exception e) {
            log.error("Failed to get encryption key for context: {}", keyContext, e);
            throw new EncryptionException("Failed to retrieve encryption key", e);
        }
    }
    
    /**
     * Generate and store new encryption key
     */
    public void generateAndStoreKey(String keyContext) {
        try {
            // Generate new AES key
            KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm);
            keyGenerator.init(keyLength);
            SecretKey newKey = keyGenerator.generateKey();
            
            // Encode key for storage
            String encodedKey = Base64.getEncoder().encodeToString(newKey.getEncoded());
            
            // Store in Vault with versioning
            String vaultPath = String.format("secret/encryption-keys/%s", keyContext);
            vaultService.putSecret(vaultPath, Map.of(
                "key", encodedKey,
                "algorithm", algorithm,
                "keyLength", String.valueOf(keyLength),
                "createdAt", Instant.now().toString(),
                "version", getNextKeyVersion(keyContext)
            ));
            
            // Clear cache to force reload
            keyCache.invalidate(keyContext);
            
            // Audit key generation
            auditService.auditKeyGeneration(keyContext, keyLength);
            
            log.info("New encryption key generated and stored for context: {}", keyContext);
            
        } catch (Exception e) {
            log.error("Failed to generate encryption key for context: {}", keyContext, e);
            throw new EncryptionException("Key generation failed", e);
        }
    }
    
    private String getNextKeyVersion(String keyContext) {
        try {
            String vaultPath = String.format("secret/encryption-keys/%s", keyContext);
            Map<String, String> keyData = vaultService.getSecretAsMap(vaultPath);
            
            if (keyData != null && keyData.containsKey("version")) {
                int currentVersion = Integer.parseInt(keyData.get("version"));
                return String.valueOf(currentVersion + 1);
            }
            
            return "1";
        } catch (Exception e) {
            return "1";
        }
    }
    
    private String generateCacheKey(String plaintext, String keyContext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = keyContext + ":" + plaintext;
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return keyContext + ":" + plaintext.hashCode();
        }
    }
    
    public String encryptPII(String piiData) {
        return encrypt(piiData, "pii");
    }
    
    public String decryptPII(String encryptedPiiData) {
        return decrypt(encryptedPiiData, "pii");
    }
    
    public String encryptFinancial(String financialData) {
        return encrypt(financialData, "financial");
    }
    
    public String decryptFinancial(String encryptedFinancialData) {
        return decrypt(encryptedFinancialData, "financial");
    }
}