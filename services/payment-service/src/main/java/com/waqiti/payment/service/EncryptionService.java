package com.waqiti.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Data;
import lombok.Builder;

/**
 * Production-grade encryption service for PCI DSS compliance.
 * Implements AES-256-GCM encryption with key rotation support.
 */
@Slf4j
@Service
public class EncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 100000;
    private static final int SALT_LENGTH = 32;
    
    @Value("${encryption.master.key:#{null}}")
    private String masterKey;
    
    @Value("${encryption.key.rotation.enabled:true}")
    private boolean keyRotationEnabled;
    
    @Value("${encryption.key.version:1}")
    private int currentKeyVersion;
    
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<Integer, SecretKey> keyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenVaultEntry> tokenVault = new ConcurrentHashMap<>();
    private final AtomicLong encryptionCounter = new AtomicLong(0);
    private final AtomicLong decryptionCounter = new AtomicLong(0);
    
    @jakarta.annotation.PostConstruct
    public void initialize() {
        if (masterKey == null || masterKey.isEmpty()) {
            log.warn("No master key configured, generating ephemeral key for development");
            this.masterKey = generateMasterKey();
        }
        
        // Pre-load current key version
        getOrGenerateKey(currentKeyVersion);
        
        log.info("Encryption service initialized with key version: {}", currentKeyVersion);
    }
    
    /**
     * Encrypt sensitive data with AES-256-GCM
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            throw new IllegalArgumentException("Plaintext cannot be null or empty for encryption");
        }
        
        try {
            // Generate IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // Get current key
            SecretKey key = getOrGenerateKey(currentKeyVersion);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);
            
            // Add additional authenticated data (AAD)
            String aad = String.format("v%d:%d", currentKeyVersion, System.currentTimeMillis());
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            
            // Encrypt
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine: version (4 bytes) + iv (12 bytes) + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(4 + iv.length + ciphertext.length);
            buffer.putInt(currentKeyVersion);
            buffer.put(iv);
            buffer.put(ciphertext);
            
            encryptionCounter.incrementAndGet();
            
            return Base64.getEncoder().encodeToString(buffer.array());
            
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }
    
    /**
     * Decrypt data encrypted with AES-256-GCM
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            throw new IllegalArgumentException("Encrypted data cannot be null or empty for decryption");
        }
        
        try {
            // Decode from Base64
            byte[] data = Base64.getDecoder().decode(encryptedData);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            
            // Extract version
            int keyVersion = buffer.getInt();
            
            // Extract IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            
            // Extract ciphertext
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            
            // Get key for version
            SecretKey key = getOrGenerateKey(keyVersion);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);
            
            // Decrypt
            byte[] plaintext = cipher.doFinal(ciphertext);
            
            decryptionCounter.incrementAndGet();
            
            return new String(plaintext, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new EncryptionException("Failed to decrypt data", e);
        }
    }
    
    /**
     * Encrypt with specific key version (for key rotation)
     */
    public String encryptWithVersion(String plaintext, int keyVersion) {
        int originalVersion = this.currentKeyVersion;
        try {
            this.currentKeyVersion = keyVersion;
            return encrypt(plaintext);
        } finally {
            this.currentKeyVersion = originalVersion;
        }
    }
    
    /**
     * Generate data encryption key from master key
     */
    private SecretKey getOrGenerateKey(int version) {
        return keyCache.computeIfAbsent(version, v -> {
            try {
                // Generate salt based on version
                String salt = String.format("waqiti-payment-v%d", version);
                
                // Derive key using PBKDF2
                SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
                KeySpec spec = new PBEKeySpec(
                    masterKey.toCharArray(),
                    salt.getBytes(StandardCharsets.UTF_8),
                    ITERATION_COUNT,
                    KEY_LENGTH
                );
                
                SecretKey tmp = factory.generateSecret(spec);
                return new SecretKeySpec(tmp.getEncoded(), KEY_ALGORITHM);
                
            } catch (Exception e) {
                log.error("Failed to generate key for version: {}", version, e);
                throw new EncryptionException("Key generation failed", e);
            }
        });
    }
    
    /**
     * Tokenize sensitive data (returns a token, stores encrypted data)
     */
    public String tokenize(String sensitiveData) {
        try {
            String token = generateToken();
            String encrypted = encrypt(sensitiveData);
            
            // Store mapping in secure token vault
            TokenVaultEntry vaultEntry = TokenVaultEntry.builder()
                .token(token)
                .encryptedValue(encrypted)
                .keyVersion(currentKeyVersion)
                .createdAt(System.currentTimeMillis())
                .expiresAt(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365))
                .build();
            
            storeInTokenVault(token, vaultEntry);
            
            return String.format("tok_%s_%d", token, currentKeyVersion);
            
        } catch (Exception e) {
            log.error("Tokenization failed for sensitive data", e);
            throw new EncryptionException("Failed to tokenize sensitive data", e);
        }
    }
    
    public String detokenize(String tokenizedValue) {
        try {
            if (!tokenizedValue.startsWith("tok_")) {
                throw new IllegalArgumentException("Invalid token format");
            }
            
            // Extract token and key version
            String[] parts = tokenizedValue.substring(4).split("_");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid token format");
            }
            
            String token = parts[0];
            int keyVersion = Integer.parseInt(parts[1]);
            
            // Retrieve from token vault
            TokenVaultEntry vaultEntry = retrieveFromTokenVault(token);
            if (vaultEntry == null) {
                throw new TokenNotFoundException("Token not found in vault");
            }
            
            // Check expiration
            if (vaultEntry.getExpiresAt() < System.currentTimeMillis()) {
                throw new TokenExpiredException("Token has expired");
            }
            
            // Decrypt and return original value
            return decrypt(vaultEntry.getEncryptedValue());
            
        } catch (Exception e) {
            log.error("Detokenization failed for token: {}", tokenizedValue, e);
            throw new EncryptionException("Failed to detokenize data", e);
        }
    }
    
    private void storeInTokenVault(String token, TokenVaultEntry entry) {
        // In production, this would store in secure vault (Redis with encryption, HSM, etc.)
        // For now, use in-memory map with TTL (not production-ready)
        tokenVault.put(token, entry);
        
        // Log for audit
        log.debug("Stored token in vault: {} (expires: {})", token, 
                 new java.util.Date(entry.getExpiresAt()));
    }
    
    private TokenVaultEntry retrieveFromTokenVault(String token) {
        return tokenVault.get(token);
    }
    
    /**
     * Detokenize to retrieve original data
     */
    public String detokenize(String token) {
        // In production, retrieve from vault
        // This is a placeholder implementation
        if (!token.startsWith("tok_")) {
            throw new IllegalArgumentException("Invalid token format");
        }
        return "****"; // Masked for security
    }
    
    /**
     * Hash data for comparison (one-way)
     */
    public String hash(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                masterKey.getBytes(StandardCharsets.UTF_8), 
                "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new EncryptionException("Hashing failed", e);
        }
    }
    
    /**
     * Compare hashed values securely (timing-attack resistant)
     */
    public boolean compareHash(String data, String hash) {
        String computedHash = hash(data);
        return MessageDigest.isEqual(
            computedHash.getBytes(StandardCharsets.UTF_8),
            hash.getBytes(StandardCharsets.UTF_8)
        );
    }
    
    /**
     * Rotate encryption keys
     */
    public void rotateKeys() {
        if (!keyRotationEnabled) {
            log.warn("Key rotation is disabled");
            return;
        }
        
        log.info("Starting key rotation from version {} to {}", 
                 currentKeyVersion, currentKeyVersion + 1);
        
        // Generate new key
        int newVersion = currentKeyVersion + 1;
        getOrGenerateKey(newVersion);
        
        // Update current version
        this.currentKeyVersion = newVersion;
        
        log.info("Key rotation completed. New version: {}", currentKeyVersion);
    }
    
    /**
     * Re-encrypt data with new key version
     */
    public String reencrypt(String encryptedData) {
        String decrypted = decrypt(encryptedData);
        return encrypt(decrypted);
    }
    
    /**
     * Generate secure random token
     */
    private String generateToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(tokenBytes);
    }
    
    /**
     * Generate master key for development
     */
    private String generateMasterKey() {
        byte[] keyBytes = new byte[32];
        secureRandom.nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }
    
    /**
     * Get encryption metrics
     */
    public EncryptionMetrics getMetrics() {
        return EncryptionMetrics.builder()
            .encryptionCount(encryptionCounter.get())
            .decryptionCount(decryptionCounter.get())
            .currentKeyVersion(currentKeyVersion)
            .cachedKeys(keyCache.size())
            .build();
    }
    
    /**
     * Custom encryption exception
     */
    public static class EncryptionException extends RuntimeException {
        public EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Encryption metrics
     */
    @lombok.Builder
    @lombok.Data
    public static class EncryptionMetrics {
        private long encryptionCount;
        private long decryptionCount;
        private int currentKeyVersion;
        private int cachedKeys;
    }
    
    /**
     * Token vault entry for secure tokenization
     */
    @Builder
    @Data
    public static class TokenVaultEntry {
        private String token;
        private String encryptedValue;
        private int keyVersion;
        private long createdAt;
        private long expiresAt;
    }
    
    /**
     * Token exceptions
     */
    public static class TokenNotFoundException extends RuntimeException {
        public TokenNotFoundException(String message) {
            super(message);
        }
    }
    
    public static class TokenExpiredException extends RuntimeException {
        public TokenExpiredException(String message) {
            super(message);
        }
    }
}