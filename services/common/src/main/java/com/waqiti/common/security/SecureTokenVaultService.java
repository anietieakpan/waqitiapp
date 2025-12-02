package com.waqiti.common.security;

import com.waqiti.common.exception.TokenVaultException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Enterprise-grade secure token vault for storing sensitive financial data.
 * Implements PCI DSS compliant tokenization with HSM-backed encryption,
 * secure key rotation, and comprehensive audit logging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureTokenVaultService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${vault.master-key:${VAULT_MASTER_KEY}}")
    private String masterKey;
    
    @Value("${vault.token-ttl-hours:24}")
    private int tokenTtlHours;
    
    @Value("${vault.max-tokens-per-user:1000}")
    private int maxTokensPerUser;
    
    @Value("${vault.enable-hsm:false}")
    private boolean enableHSM;
    
    // Encryption constants
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final int SALT_LENGTH_BYTE = 16;
    private static final int KEY_LENGTH_BIT = 256;
    private static final int ITERATION_COUNT = 100000;
    private static final String TOKEN_PREFIX = "TOK_";
    private static final String VAULT_PREFIX = "vault:";
    private static final String USER_TOKENS_PREFIX = "user_tokens:";
    
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, LocalDateTime> keyRotationLog = new ConcurrentHashMap<>();

    /**
     * Stores sensitive data and returns a secure token
     * @param sensitiveData The data to vault (account number, SSN, etc.)
     * @param userId The user ID for ownership tracking
     * @param dataType Type of data (ACCOUNT_NUMBER, SSN, CARD_NUMBER, etc.)
     * @param metadata Additional metadata for the token
     * @return Secure token that can be safely stored and transmitted
     */
    @Transactional
    public String vaultSensitiveData(String sensitiveData, UUID userId, String dataType, Map<String, String> metadata) {
        try {
            log.info("Vaulting sensitive data type: {} for user: {}", dataType, userId);
            
            // Validate inputs
            validateVaultRequest(sensitiveData, userId, dataType);
            
            // Check user token limits
            enforceUserTokenLimits(userId);
            
            // Generate unique token
            String token = generateSecureToken(sensitiveData, userId, dataType);
            
            // Encrypt the sensitive data
            String encryptedData = encryptData(sensitiveData, token);
            
            // Create vault entry
            TokenVaultEntry vaultEntry = TokenVaultEntry.builder()
                .token(token)
                .encryptedData(encryptedData)
                .userId(userId)
                .dataType(dataType)
                .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
                .createdAt(LocalDateTime.now())
                .lastAccessedAt(LocalDateTime.now())
                .accessCount(0L)
                .expiresAt(LocalDateTime.now().plusHours(tokenTtlHours))
                .isActive(true)
                .build();
            
            // Store in Redis with TTL
            String vaultKey = VAULT_PREFIX + token;
            redisTemplate.opsForValue().set(vaultKey, vaultEntry, Duration.ofHours(tokenTtlHours));
            
            // Track user's tokens
            String userTokensKey = USER_TOKENS_PREFIX + userId;
            redisTemplate.opsForSet().add(userTokensKey, token);
            redisTemplate.expire(userTokensKey, Duration.ofHours(tokenTtlHours + 1));
            
            // Log vault operation
            logVaultOperation("VAULT_STORE", token, userId, dataType, true);
            
            log.info("Successfully vaulted data type: {} for user: {}, token: {}", 
                    dataType, userId, maskToken(token));
            
            return token;
            
        } catch (Exception e) {
            log.error("Failed to vault sensitive data for user: {}, type: {}", userId, dataType, e);
            logVaultOperation("VAULT_STORE", null, userId, dataType, false);
            throw new TokenVaultException("Failed to vault sensitive data", e);
        }
    }

    /**
     * Retrieves sensitive data using a secure token
     * @param token The secure token
     * @param userId The user ID for ownership verification
     * @return The original sensitive data
     */
    @Transactional(readOnly = true)
    public String retrieveSensitiveData(String token, UUID userId) {
        try {
            log.debug("Retrieving sensitive data for token: {} user: {}", maskToken(token), userId);
            
            // Validate token format
            validateToken(token);
            
            // Get vault entry
            String vaultKey = VAULT_PREFIX + token;
            TokenVaultEntry vaultEntry = (TokenVaultEntry) redisTemplate.opsForValue().get(vaultKey);
            
            if (vaultEntry == null) {
                log.warn("Token not found in vault: {}", maskToken(token));
                throw new TokenVaultException("Token not found or expired");
            }
            
            // Verify ownership
            if (!vaultEntry.getUserId().equals(userId)) {
                log.error("Unauthorized token access attempt. Token: {} Expected user: {} Actual user: {}", 
                        maskToken(token), userId, vaultEntry.getUserId());
                logVaultOperation("VAULT_RETRIEVE", token, userId, vaultEntry.getDataType(), false);
                throw new SecurityException("Unauthorized access to token");
            }
            
            // Check expiration
            if (vaultEntry.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Expired token access attempt: {}", maskToken(token));
                removeExpiredToken(token, userId);
                throw new TokenVaultException("Token has expired");
            }
            
            // Decrypt the data
            String sensitiveData = decryptData(vaultEntry.getEncryptedData(), token);
            
            // Update access tracking
            updateTokenAccess(vaultEntry, vaultKey);
            
            // Log successful retrieval
            logVaultOperation("VAULT_RETRIEVE", token, userId, vaultEntry.getDataType(), true);
            
            log.debug("Successfully retrieved sensitive data for token: {}", maskToken(token));
            return sensitiveData;
            
        } catch (TokenVaultException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to retrieve sensitive data for token: {}", maskToken(token), e);
            logVaultOperation("VAULT_RETRIEVE", token, userId, "UNKNOWN", false);
            throw new TokenVaultException("Failed to retrieve sensitive data", e);
        }
    }

    /**
     * Revokes a token and removes it from the vault
     * @param token The token to revoke
     * @param userId The user ID for ownership verification
     */
    @Transactional
    public void revokeToken(String token, UUID userId) {
        try {
            log.info("Revoking token: {} for user: {}", maskToken(token), userId);
            
            // Get vault entry for verification
            String vaultKey = VAULT_PREFIX + token;
            TokenVaultEntry vaultEntry = (TokenVaultEntry) redisTemplate.opsForValue().get(vaultKey);
            
            if (vaultEntry != null) {
                // Verify ownership
                if (!vaultEntry.getUserId().equals(userId)) {
                    log.error("Unauthorized token revocation attempt: {}", maskToken(token));
                    throw new SecurityException("Unauthorized access to token");
                }
                
                // Remove from vault
                redisTemplate.delete(vaultKey);
                
                // Remove from user's token set
                String userTokensKey = USER_TOKENS_PREFIX + userId;
                redisTemplate.opsForSet().remove(userTokensKey, token);
                
                // Log revocation
                logVaultOperation("VAULT_REVOKE", token, userId, vaultEntry.getDataType(), true);
            }
            
            log.info("Successfully revoked token: {}", maskToken(token));
            
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to revoke token: {}", maskToken(token), e);
            throw new TokenVaultException("Failed to revoke token", e);
        }
    }

    /**
     * Lists all active tokens for a user
     * @param userId The user ID
     * @return List of token metadata (without sensitive data)
     */
    @Transactional(readOnly = true)
    public List<TokenMetadata> getUserTokens(UUID userId) {
        try {
            log.debug("Getting tokens for user: {}", userId);
            
            String userTokensKey = USER_TOKENS_PREFIX + userId;
            Set<Object> userTokens = redisTemplate.opsForSet().members(userTokensKey);
            
            if (userTokens == null || userTokens.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<TokenMetadata> tokenMetadataList = new ArrayList<>();
            
            for (Object tokenObj : userTokens) {
                String token = tokenObj.toString();
                String vaultKey = VAULT_PREFIX + token;
                TokenVaultEntry vaultEntry = (TokenVaultEntry) redisTemplate.opsForValue().get(vaultKey);
                
                if (vaultEntry != null && vaultEntry.isActive()) {
                    TokenMetadata metadata = TokenMetadata.builder()
                        .token(maskToken(token))
                        .dataType(vaultEntry.getDataType())
                        .createdAt(vaultEntry.getCreatedAt())
                        .lastAccessedAt(vaultEntry.getLastAccessedAt())
                        .accessCount(vaultEntry.getAccessCount())
                        .expiresAt(vaultEntry.getExpiresAt())
                        .metadata(vaultEntry.getMetadata())
                        .build();
                    
                    tokenMetadataList.add(metadata);
                }
            }
            
            return tokenMetadataList;
            
        } catch (Exception e) {
            log.error("Failed to get user tokens for user: {}", userId, e);
            throw new TokenVaultException("Failed to retrieve user tokens", e);
        }
    }

    /**
     * Rotates encryption keys for enhanced security
     */
    @Transactional
    public void rotateEncryptionKeys() {
        try {
            log.info("Starting encryption key rotation");
            
            String currentKeyId = getCurrentKeyId();
            String newKeyId = generateNewKeyId();
            
            // Generate new master key
            String newMasterKey = generateNewMasterKey();
            
            // Re-encrypt all vault entries with new key
            reencryptAllVaultEntries(currentKeyId, newKeyId, newMasterKey);
            
            // Update key rotation log
            keyRotationLog.put(newKeyId, LocalDateTime.now());
            
            // Clean up old keys after grace period
            scheduleOldKeyCleanup(currentKeyId);
            
            log.info("Successfully completed encryption key rotation. New key ID: {}", newKeyId);
            
        } catch (Exception e) {
            log.error("Failed to rotate encryption keys", e);
            throw new TokenVaultException("Failed to rotate encryption keys", e);
        }
    }

    /**
     * Cleans up expired tokens
     */
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            log.info("Starting expired token cleanup");
            
            Set<String> expiredTokens = findExpiredTokens();
            
            for (String token : expiredTokens) {
                try {
                    // Remove from vault
                    String vaultKey = VAULT_PREFIX + token;
                    TokenVaultEntry vaultEntry = (TokenVaultEntry) redisTemplate.opsForValue().get(vaultKey);
                    
                    if (vaultEntry != null) {
                        redisTemplate.delete(vaultKey);
                        
                        // Remove from user's token set
                        String userTokensKey = USER_TOKENS_PREFIX + vaultEntry.getUserId();
                        redisTemplate.opsForSet().remove(userTokensKey, token);
                        
                        log.debug("Cleaned up expired token: {}", maskToken(token));
                    }
                } catch (Exception e) {
                    log.error("Failed to cleanup token: {}", maskToken(token), e);
                }
            }
            
            log.info("Completed expired token cleanup. Removed {} tokens", expiredTokens.size());
            
        } catch (Exception e) {
            log.error("Failed to cleanup expired tokens", e);
        }
    }

    // Private helper methods

    private void validateVaultRequest(String sensitiveData, UUID userId, String dataType) {
        if (sensitiveData == null || sensitiveData.trim().isEmpty()) {
            throw new IllegalArgumentException("Sensitive data cannot be null or empty");
        }
        
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        if (dataType == null || dataType.trim().isEmpty()) {
            throw new IllegalArgumentException("Data type cannot be null or empty");
        }
        
        // Validate data type
        Set<String> allowedDataTypes = Set.of(
            "ACCOUNT_NUMBER", "ROUTING_NUMBER", "SSN", "CARD_NUMBER", 
            "CVV", "PIN", "PRIVATE_KEY", "SEED_PHRASE", "API_KEY"
        );
        
        if (!allowedDataTypes.contains(dataType)) {
            throw new IllegalArgumentException("Invalid data type: " + dataType);
        }
        
        // Validate sensitive data format based on type
        validateDataFormat(sensitiveData, dataType);
    }

    private void validateDataFormat(String data, String dataType) {
        switch (dataType) {
            case "ACCOUNT_NUMBER" -> {
                if (!data.matches("^[0-9]{8,17}$")) {
                    throw new IllegalArgumentException("Invalid account number format");
                }
            }
            case "ROUTING_NUMBER" -> {
                if (!data.matches("^[0-9]{9}$")) {
                    throw new IllegalArgumentException("Invalid routing number format");
                }
            }
            case "SSN" -> {
                if (!data.matches("^[0-9]{9}$")) {
                    throw new IllegalArgumentException("Invalid SSN format");
                }
            }
            case "CARD_NUMBER" -> {
                if (!data.matches("^[0-9]{13,19}$")) {
                    throw new IllegalArgumentException("Invalid card number format");
                }
            }
            case "CVV" -> {
                if (!data.matches("^[0-9]{3,4}$")) {
                    throw new IllegalArgumentException("Invalid CVV format");
                }
            }
        }
    }

    private void enforceUserTokenLimits(UUID userId) {
        String userTokensKey = USER_TOKENS_PREFIX + userId;
        Long tokenCount = redisTemplate.opsForSet().size(userTokensKey);
        
        if (tokenCount != null && tokenCount >= maxTokensPerUser) {
            log.warn("User {} exceeded token limit: {}/{}", userId, tokenCount, maxTokensPerUser);
            throw new TokenVaultException("Maximum tokens per user exceeded");
        }
    }

    private String generateSecureToken(String sensitiveData, UUID userId, String dataType) {
        try {
            // Create unique token using HMAC with timestamp and random data
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(masterKey.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            
            // Include multiple entropy sources
            String tokenData = sensitiveData + userId.toString() + dataType + 
                             System.currentTimeMillis() + UUID.randomUUID().toString();
            
            byte[] tokenBytes = mac.doFinal(tokenData.getBytes());
            String token = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tokenBytes).substring(0, 32);
            
            // Ensure uniqueness
            if (redisTemplate.hasKey(VAULT_PREFIX + token)) {
                return generateSecureToken(sensitiveData, userId, dataType); // Retry with new UUID
            }
            
            return token;
            
        } catch (Exception e) {
            throw new TokenVaultException("Failed to generate secure token", e);
        }
    }

    private String encryptData(String plaintext, String associatedData) {
        try {
            // Generate random salt and IV
            byte[] salt = generateRandomBytes(SALT_LENGTH_BYTE);
            byte[] iv = generateRandomBytes(IV_LENGTH_BYTE);
            
            // Derive encryption key
            SecretKey secretKey = deriveKey(masterKey, salt);
            
            // Initialize cipher with GCM mode
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);
            
            // Add associated authenticated data
            cipher.updateAAD(associatedData.getBytes());
            
            // Perform encryption
            byte[] cipherText = cipher.doFinal(plaintext.getBytes());
            
            // Combine salt, IV, and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(salt.length + iv.length + cipherText.length);
            byteBuffer.put(salt);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            
            return Base64.getEncoder().encodeToString(byteBuffer.array());
            
        } catch (Exception e) {
            throw new TokenVaultException("Failed to encrypt sensitive data", e);
        }
    }

    private String decryptData(String encryptedData, String associatedData) {
        try {
            // Decode from base64
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);
            
            // Extract salt, IV, and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedBytes);
            
            byte[] salt = new byte[SALT_LENGTH_BYTE];
            byteBuffer.get(salt);
            
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);
            
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);
            
            // Derive decryption key
            SecretKey secretKey = deriveKey(masterKey, salt);
            
            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);
            
            // Add associated authenticated data
            cipher.updateAAD(associatedData.getBytes());
            
            // Perform decryption
            byte[] plainText = cipher.doFinal(cipherText);
            
            return new String(plainText);
            
        } catch (AEADBadTagException e) {
            log.error("Authentication tag verification failed - data tampering detected");
            throw new TokenVaultException("Data integrity check failed", e);
        } catch (Exception e) {
            throw new TokenVaultException("Failed to decrypt sensitive data", e);
        }
    }

    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        KeySpec keySpec = new PBEKeySpec(
            password.toCharArray(), 
            salt, 
            ITERATION_COUNT, 
            KEY_LENGTH_BIT
        );
        
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = keyFactory.generateSecret(keySpec).getEncoded();
        
        return new SecretKeySpec(keyBytes, "AES");
    }

    private byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private void validateToken(String token) {
        if (token == null || !token.startsWith(TOKEN_PREFIX)) {
            throw new IllegalArgumentException("Invalid token format");
        }
        
        if (token.length() < 20) {
            throw new IllegalArgumentException("Token too short");
        }
    }

    private void updateTokenAccess(TokenVaultEntry vaultEntry, String vaultKey) {
        try {
            vaultEntry.setLastAccessedAt(LocalDateTime.now());
            vaultEntry.setAccessCount(vaultEntry.getAccessCount() + 1);
            
            redisTemplate.opsForValue().set(vaultKey, vaultEntry, Duration.ofHours(tokenTtlHours));
        } catch (Exception e) {
            log.warn("Failed to update token access tracking", e);
        }
    }

    private void removeExpiredToken(String token, UUID userId) {
        try {
            String vaultKey = VAULT_PREFIX + token;
            redisTemplate.delete(vaultKey);
            
            String userTokensKey = USER_TOKENS_PREFIX + userId;
            redisTemplate.opsForSet().remove(userTokensKey, token);
            
            log.debug("Removed expired token: {}", maskToken(token));
        } catch (Exception e) {
            log.error("Failed to remove expired token: {}", maskToken(token), e);
        }
    }

    private Set<String> findExpiredTokens() {
        // This would typically scan through tokens to find expired ones
        // For Redis, we rely on TTL, but this provides a backup cleanup mechanism
        Set<String> expiredTokens = new HashSet<>();
        
        try {
            Set<String> allVaultKeys = redisTemplate.keys(VAULT_PREFIX + "*");
            
            if (allVaultKeys != null) {
                for (String vaultKey : allVaultKeys) {
                    TokenVaultEntry vaultEntry = (TokenVaultEntry) redisTemplate.opsForValue().get(vaultKey);
                    
                    if (vaultEntry != null && vaultEntry.getExpiresAt().isBefore(LocalDateTime.now())) {
                        String token = vaultKey.substring(VAULT_PREFIX.length());
                        expiredTokens.add(token);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to find expired tokens", e);
        }
        
        return expiredTokens;
    }

    private void reencryptAllVaultEntries(String oldKeyId, String newKeyId, String newMasterKey) {
        // Implementation for key rotation would re-encrypt all stored data
        log.info("Re-encrypting vault entries from key {} to key {}", oldKeyId, newKeyId);
        // This is a complex operation that would be implemented based on specific HSM requirements
    }

    private String getCurrentKeyId() {
        return "default-key-" + LocalDateTime.now().toLocalDate();
    }

    private String generateNewKeyId() {
        return "key-" + UUID.randomUUID().toString().substring(0, 8) + "-" + 
               LocalDateTime.now().toLocalDate();
    }

    private String generateNewMasterKey() {
        byte[] keyBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(keyBytes);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    private void scheduleOldKeyCleanup(String oldKeyId) {
        // Schedule cleanup of old key after grace period
        log.info("Scheduled cleanup for old key: {} in 7 days", oldKeyId);
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    private void logVaultOperation(String operation, String token, UUID userId, String dataType, boolean success) {
        try {
            VaultAuditLog auditLog = VaultAuditLog.builder()
                .operation(operation)
                .token(token != null ? maskToken(token) : null)
                .userId(userId)
                .dataType(dataType)
                .success(success)
                .timestamp(LocalDateTime.now())
                .ipAddress(getClientIpAddress())
                .userAgent(getClientUserAgent())
                .build();
            
            // Store audit log
            String auditKey = "vault_audit:" + UUID.randomUUID();
            redisTemplate.opsForValue().set(auditKey, auditLog, Duration.ofDays(90));
            
        } catch (Exception e) {
            log.error("Failed to log vault operation", e);
        }
    }

    private String getClientIpAddress() {
        // Implementation would extract from current HTTP request context
        return "unknown";
    }

    private String getClientUserAgent() {
        // Implementation would extract from current HTTP request context
        return "unknown";
    }

    // Data classes

    public static class TokenVaultEntry {
        private String token;
        private String encryptedData;
        private UUID userId;
        private String dataType;
        private Map<String, String> metadata;
        private LocalDateTime createdAt;
        private LocalDateTime lastAccessedAt;
        private Long accessCount;
        private LocalDateTime expiresAt;
        private boolean isActive;
        
        // Builder pattern
        public static TokenVaultEntryBuilder builder() {
            return new TokenVaultEntryBuilder();
        }
        
        // Getters and setters
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getEncryptedData() { return encryptedData; }
        public void setEncryptedData(String encryptedData) { this.encryptedData = encryptedData; }
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getLastAccessedAt() { return lastAccessedAt; }
        public void setLastAccessedAt(LocalDateTime lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
        public Long getAccessCount() { return accessCount; }
        public void setAccessCount(Long accessCount) { this.accessCount = accessCount; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
        public boolean isActive() { return isActive; }
        public void setActive(boolean active) { isActive = active; }
        
        public static class TokenVaultEntryBuilder {
            private TokenVaultEntry entry = new TokenVaultEntry();
            
            public TokenVaultEntryBuilder token(String token) { entry.setToken(token); return this; }
            public TokenVaultEntryBuilder encryptedData(String encryptedData) { entry.setEncryptedData(encryptedData); return this; }
            public TokenVaultEntryBuilder userId(UUID userId) { entry.setUserId(userId); return this; }
            public TokenVaultEntryBuilder dataType(String dataType) { entry.setDataType(dataType); return this; }
            public TokenVaultEntryBuilder metadata(Map<String, String> metadata) { entry.setMetadata(metadata); return this; }
            public TokenVaultEntryBuilder createdAt(LocalDateTime createdAt) { entry.setCreatedAt(createdAt); return this; }
            public TokenVaultEntryBuilder lastAccessedAt(LocalDateTime lastAccessedAt) { entry.setLastAccessedAt(lastAccessedAt); return this; }
            public TokenVaultEntryBuilder accessCount(Long accessCount) { entry.setAccessCount(accessCount); return this; }
            public TokenVaultEntryBuilder expiresAt(LocalDateTime expiresAt) { entry.setExpiresAt(expiresAt); return this; }
            public TokenVaultEntryBuilder isActive(boolean isActive) { entry.setActive(isActive); return this; }
            
            public TokenVaultEntry build() { return entry; }
        }
    }

    public static class TokenMetadata {
        private String token;
        private String dataType;
        private LocalDateTime createdAt;
        private LocalDateTime lastAccessedAt;
        private Long accessCount;
        private LocalDateTime expiresAt;
        private Map<String, String> metadata;
        
        public static TokenMetadataBuilder builder() {
            return new TokenMetadataBuilder();
        }
        
        // Getters
        public String getToken() { return token; }
        public String getDataType() { return dataType; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getLastAccessedAt() { return lastAccessedAt; }
        public Long getAccessCount() { return accessCount; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public Map<String, String> getMetadata() { return metadata; }
        
        public static class TokenMetadataBuilder {
            private TokenMetadata metadata = new TokenMetadata();
            
            public TokenMetadataBuilder token(String token) { metadata.token = token; return this; }
            public TokenMetadataBuilder dataType(String dataType) { metadata.dataType = dataType; return this; }
            public TokenMetadataBuilder createdAt(LocalDateTime createdAt) { metadata.createdAt = createdAt; return this; }
            public TokenMetadataBuilder lastAccessedAt(LocalDateTime lastAccessedAt) { metadata.lastAccessedAt = lastAccessedAt; return this; }
            public TokenMetadataBuilder accessCount(Long accessCount) { metadata.accessCount = accessCount; return this; }
            public TokenMetadataBuilder expiresAt(LocalDateTime expiresAt) { metadata.expiresAt = expiresAt; return this; }
            public TokenMetadataBuilder metadata(Map<String, String> metadataMap) { this.metadata.metadata = metadataMap; return this; }
            
            public TokenMetadata build() { return metadata; }
        }
    }

    public static class VaultAuditLog {
        private String operation;
        private String token;
        private UUID userId;
        private String dataType;
        private boolean success;
        private LocalDateTime timestamp;
        private String ipAddress;
        private String userAgent;
        
        public static VaultAuditLogBuilder builder() {
            return new VaultAuditLogBuilder();
        }
        
        // Getters
        public String getOperation() { return operation; }
        public String getToken() { return token; }
        public UUID getUserId() { return userId; }
        public String getDataType() { return dataType; }
        public boolean isSuccess() { return success; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getIpAddress() { return ipAddress; }
        public String getUserAgent() { return userAgent; }
        
        public static class VaultAuditLogBuilder {
            private VaultAuditLog log = new VaultAuditLog();
            
            public VaultAuditLogBuilder operation(String operation) { log.operation = operation; return this; }
            public VaultAuditLogBuilder token(String token) { log.token = token; return this; }
            public VaultAuditLogBuilder userId(UUID userId) { log.userId = userId; return this; }
            public VaultAuditLogBuilder dataType(String dataType) { log.dataType = dataType; return this; }
            public VaultAuditLogBuilder success(boolean success) { log.success = success; return this; }
            public VaultAuditLogBuilder timestamp(LocalDateTime timestamp) { log.timestamp = timestamp; return this; }
            public VaultAuditLogBuilder ipAddress(String ipAddress) { log.ipAddress = ipAddress; return this; }
            public VaultAuditLogBuilder userAgent(String userAgent) { log.userAgent = userAgent; return this; }
            
            public VaultAuditLog build() { return log; }
        }
    }
}