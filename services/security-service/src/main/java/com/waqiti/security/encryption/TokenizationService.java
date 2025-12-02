package com.waqiti.security.encryption;

import com.waqiti.security.audit.AuditService;
import com.waqiti.security.exception.PCIEncryptionException;
import com.waqiti.security.keymanagement.EncryptionKeyManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * PCI DSS Compliant Tokenization Service
 * 
 * CRITICAL SECURITY: Implements PCI DSS v4.0 tokenization requirements
 * 
 * This service provides:
 * - Format-preserving tokenization for PANs
 * - Secure token generation using strong cryptography
 * - Token-to-PAN mapping with encryption
 * - PCI DSS compliant token lifecycle management
 * - Comprehensive audit trails for all tokenization operations
 * 
 * TOKENIZATION REQUIREMENTS:
 * - Tokens must be cryptographically strong
 * - No mathematical relationship to original PAN
 * - Tokens must be format-preserving (same length as PAN)
 * - Token storage must be encrypted and access-controlled
 * - Complete audit trail for token lifecycle
 * 
 * SECURITY BENEFITS:
 * - Reduces PCI DSS scope by replacing sensitive data
 * - Eliminates cardholder data from most systems
 * - Maintains transaction processing capabilities
 * - Enables secure analytics and reporting
 * 
 * NON-COMPLIANCE PENALTIES:
 * - Level 1: $5,000 - $500,000 per month
 * - Data breach fines: $50 - $90 per compromised record
 * - Business termination by acquirers
 * - Legal liability and lawsuits
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenizationService {

    private final EncryptionKeyManager keyManager;
    private final AuditService auditService;

    @Value("${security.pci.tokenization.enabled:true}")
    private boolean tokenizationEnabled;

    @Value("${security.pci.tokenization.format.preserving:true}")
    private boolean formatPreserving;

    @Value("${security.pci.audit.enabled:true}")
    private boolean auditEnabled;

    // Encryption constants
    private static final String TOKENIZATION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int AES_KEY_LENGTH = 256;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    
    // Token format constants
    private static final String TOKEN_KEY_ID = "pan_tokenization";
    private static final String TOKEN_PREFIX = "4111"; // Safe test prefix for format-preserving tokens
    private static final Pattern PAN_PATTERN = Pattern.compile("^[0-9]{13,19}$");

    // In-memory token vault (in production, this would be HSM or secure database)
    private final Map<String, TokenVaultEntry> tokenVault = new ConcurrentHashMap<>();
    private final Map<String, String> reverseTokenVault = new ConcurrentHashMap<>();

    /**
     * Tokenizes a Primary Account Number (PAN)
     * 
     * @param pan Primary Account Number to tokenize
     * @param contextId Context identifier for audit trails
     * @return Format-preserving token
     */
    public String tokenizePAN(String pan, String contextId) {
        if (!tokenizationEnabled) {
            log.warn("PCI tokenization is disabled - returning original PAN");
            return pan;
        }

        validatePAN(pan);
        
        // Check if PAN is already tokenized
        String existingToken = reverseTokenVault.get(pan);
        if (existingToken != null) {
            log.debug("Returning existing token for PAN context: {}", contextId);
            auditTokenization("PAN_TOKENIZATION_EXISTING", contextId, existingToken, true);
            return existingToken;
        }

        try {
            log.debug("Tokenizing PAN for context: {}", contextId);

            // Generate format-preserving token
            String token = generateFormatPreservingToken(pan);
            
            // Create encrypted vault entry
            TokenVaultEntry vaultEntry = createTokenVaultEntry(pan, token, contextId);
            
            // Store in token vault
            tokenVault.put(token, vaultEntry);
            reverseTokenVault.put(pan, token);

            // Audit successful tokenization
            auditTokenization("PAN_TOKENIZED", contextId, token, true);
            
            log.debug("PAN successfully tokenized for context: {}, token length: {}", 
                contextId, token.length());
            
            return token;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to tokenize PAN for context: {}", contextId, e);
            auditTokenization("PAN_TOKENIZATION_FAILED", contextId, null, false);
            throw new PCIEncryptionException("Failed to tokenize PAN", e);
        }
    }

    /**
     * Detokenizes a token back to the original PAN
     * 
     * @param token Token to detokenize
     * @param contextId Context identifier for audit trails
     * @return Original Primary Account Number
     */
    public String detokenizePAN(String token, String contextId) {
        if (!tokenizationEnabled) {
            log.warn("PCI tokenization is disabled - returning token as-is");
            return token;
        }

        validateToken(token);

        try {
            log.debug("Detokenizing token for context: {}", contextId);

            // Retrieve token vault entry
            TokenVaultEntry vaultEntry = tokenVault.get(token);
            if (vaultEntry == null) {
                throw new PCIEncryptionException("Token not found in vault");
            }

            // Check token expiration
            if (vaultEntry.isExpired()) {
                log.warn("Token expired for context: {}", contextId);
                auditDetokenization("TOKEN_EXPIRED", contextId, token, false);
                throw new PCIEncryptionException("Token has expired");
            }

            // Decrypt original PAN
            String originalPAN = decryptPANFromVault(vaultEntry);
            
            // Validate decrypted PAN
            validatePAN(originalPAN);

            // Update access tracking
            vaultEntry.updateLastAccessed();

            // Audit successful detokenization
            auditDetokenization("PAN_DETOKENIZED", contextId, token, true);
            
            log.debug("Token successfully detokenized for context: {}", contextId);
            
            return originalPAN;

        } catch (Exception e) {
            log.error("CRITICAL: Failed to detokenize token for context: {}", contextId, e);
            auditDetokenization("DETOKENIZATION_FAILED", contextId, token, false);
            throw new PCIEncryptionException("Failed to detokenize token", e);
        }
    }

    /**
     * Validates if a string is a valid token format
     */
    public boolean isValidToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        // Check if token exists in vault
        return tokenVault.containsKey(token);
    }

    /**
     * Checks if a token is expired
     */
    public boolean isTokenExpired(String token) {
        TokenVaultEntry entry = tokenVault.get(token);
        return entry != null && entry.isExpired();
    }

    /**
     * Revokes a token (removes from vault)
     */
    public boolean revokeToken(String token, String contextId) {
        try {
            log.info("Revoking token for context: {}", contextId);
            
            TokenVaultEntry entry = tokenVault.remove(token);
            if (entry != null) {
                // Remove reverse mapping
                String originalPAN = decryptPANFromVault(entry);
                reverseTokenVault.remove(originalPAN);
                
                auditTokenization("TOKEN_REVOKED", contextId, token, true);
                log.info("Token successfully revoked for context: {}", contextId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Failed to revoke token for context: {}", contextId, e);
            auditTokenization("TOKEN_REVOCATION_FAILED", contextId, token, false);
            return false;
        }
    }

    /**
     * Gets token metadata without revealing the original PAN
     */
    public TokenMetadata getTokenMetadata(String token) {
        TokenVaultEntry entry = tokenVault.get(token);
        if (entry == null) {
            throw new TokenNotFoundException("Token not found: " + maskToken(token));
        }

        return TokenMetadata.builder()
            .tokenId(token)
            .createdAt(entry.getCreatedAt())
            .lastAccessed(entry.getLastAccessed())
            .accessCount(entry.getAccessCount())
            .expiresAt(entry.getExpiresAt())
            .isExpired(entry.isExpired())
            .contextId(entry.getContextId())
            .build();
    }

    /**
     * Performs token vault cleanup (removes expired tokens)
     */
    public int performTokenCleanup() {
        log.info("Performing token vault cleanup");
        
        int removedCount = 0;
        
        try {
            var iterator = tokenVault.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                TokenVaultEntry vaultEntry = entry.getValue();
                
                if (vaultEntry.isExpired()) {
                    String token = entry.getKey();
                    
                    // Remove reverse mapping
                    try {
                        String originalPAN = decryptPANFromVault(vaultEntry);
                        reverseTokenVault.remove(originalPAN);
                    } catch (Exception e) {
                        log.warn("Failed to decrypt PAN during cleanup for token: {}", token);
                    }
                    
                    // Remove from vault
                    iterator.remove();
                    removedCount++;
                    
                    log.debug("Removed expired token: {}", token);
                }
            }
            
            log.info("Token vault cleanup completed - Removed {} expired tokens", removedCount);
            
            return removedCount;
            
        } catch (Exception e) {
            log.error("Error during token vault cleanup", e);
            return removedCount;
        }
    }

    // Private helper methods

    private String generateFormatPreservingToken(String pan) {
        if (!formatPreserving) {
            return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16);
        }

        // Generate format-preserving token with same length as PAN
        StringBuilder token = new StringBuilder();
        SecureRandom random = new SecureRandom();
        
        // Use safe test prefix for format preservation
        token.append(TOKEN_PREFIX);
        
        // Generate random digits for the rest
        int remainingLength = pan.length() - TOKEN_PREFIX.length();
        for (int i = 0; i < remainingLength; i++) {
            token.append(random.nextInt(10));
        }
        
        // Ensure token doesn't accidentally match a real PAN
        String candidateToken = token.toString();
        while (isRealPAN(candidateToken) || tokenVault.containsKey(candidateToken)) {
            // Regenerate last digit
            int lastIndex = candidateToken.length() - 1;
            candidateToken = candidateToken.substring(0, lastIndex) + random.nextInt(10);
        }
        
        return candidateToken;
    }

    private boolean isRealPAN(String candidateToken) {
        // Simple check to avoid generating tokens that look like real PANs
        // In production, this would check against known BIN ranges
        return candidateToken.startsWith("4000") || 
               candidateToken.startsWith("5555") ||
               candidateToken.startsWith("3777");
    }

    private TokenVaultEntry createTokenVaultEntry(String pan, String token, String contextId) throws Exception {
        // Encrypt the PAN for storage
        SecretKey encryptionKey = keyManager.getOrGenerateKey(TOKEN_KEY_ID, AES_KEY_LENGTH);
        
        // Generate IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        
        // Encrypt PAN
        Cipher cipher = Cipher.getInstance(TOKENIZATION_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, parameterSpec);
        
        byte[] encryptedPAN = cipher.doFinal(pan.getBytes(StandardCharsets.UTF_8));
        
        return TokenVaultEntry.builder()
            .tokenId(token)
            .encryptedPAN(Base64.getEncoder().encodeToString(encryptedPAN))
            .iv(Base64.getEncoder().encodeToString(iv))
            .contextId(contextId)
            .createdAt(LocalDateTime.now())
            .lastAccessed(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(90)) // 90-day token expiration
            .accessCount(0)
            .build();
    }

    private String decryptPANFromVault(TokenVaultEntry vaultEntry) throws Exception {
        // Get decryption key
        SecretKey decryptionKey = keyManager.getKey(TOKEN_KEY_ID);
        if (decryptionKey == null) {
            throw new PCIEncryptionException("Token decryption key not found");
        }
        
        // Decode IV
        byte[] iv = Base64.getDecoder().decode(vaultEntry.getIv());
        
        // Decrypt PAN
        Cipher cipher = Cipher.getInstance(TOKENIZATION_ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, decryptionKey, parameterSpec);
        
        byte[] encryptedPAN = Base64.getDecoder().decode(vaultEntry.getEncryptedPAN());
        byte[] decryptedBytes = cipher.doFinal(encryptedPAN);
        
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    private void validatePAN(String pan) {
        if (pan == null || pan.trim().isEmpty()) {
            throw new PCIEncryptionException("PAN cannot be null or empty");
        }
        
        String cleanPAN = pan.replaceAll("[\\s\\-]", "");
        if (!PAN_PATTERN.matcher(cleanPAN).matches()) {
            throw new PCIEncryptionException("Invalid PAN format");
        }
    }

    private void validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new PCIEncryptionException("Token cannot be null or empty");
        }
        
        if (token.length() < 13 || token.length() > 19) {
            throw new PCIEncryptionException("Invalid token format");
        }
    }

    private void auditTokenization(String event, String contextId, String token, boolean success) {
        if (auditEnabled && auditService != null) {
            try {
                auditService.logSecurityEvent(event, Map.of(
                    "contextId", contextId != null ? contextId : "unknown",
                    "tokenId", token != null ? token.substring(0, Math.min(6, token.length())) + "****" : "null",
                    "success", success,
                    "timestamp", LocalDateTime.now(),
                    "operation", "TOKENIZATION"
                ));
            } catch (Exception e) {
                log.error("Failed to audit tokenization event", e);
            }
        }
    }

    private void auditDetokenization(String event, String contextId, String token, boolean success) {
        if (auditEnabled && auditService != null) {
            try {
                auditService.logSecurityEvent(event, Map.of(
                    "contextId", contextId != null ? contextId : "unknown",
                    "tokenId", token != null ? token.substring(0, Math.min(6, token.length())) + "****" : "null",
                    "success", success,
                    "timestamp", LocalDateTime.now(),
                    "operation", "DETOKENIZATION"
                ));
            } catch (Exception e) {
                log.error("Failed to audit detokenization event", e);
            }
        }
    }

    // Data structures

    /**
     * Token vault entry containing encrypted PAN and metadata
     */
    public static class TokenVaultEntry {
        private final String tokenId;
        private final String encryptedPAN;
        private final String iv;
        private final String contextId;
        private final LocalDateTime createdAt;
        private final LocalDateTime expiresAt;
        private LocalDateTime lastAccessed;
        private long accessCount;

        private TokenVaultEntry(TokenVaultEntryBuilder builder) {
            this.tokenId = builder.tokenId;
            this.encryptedPAN = builder.encryptedPAN;
            this.iv = builder.iv;
            this.contextId = builder.contextId;
            this.createdAt = builder.createdAt;
            this.expiresAt = builder.expiresAt;
            this.lastAccessed = builder.lastAccessed;
            this.accessCount = builder.accessCount;
        }

        public static TokenVaultEntryBuilder builder() {
            return new TokenVaultEntryBuilder();
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }

        public void updateLastAccessed() {
            this.lastAccessed = LocalDateTime.now();
            this.accessCount++;
        }

        // Getters
        public String getTokenId() { return tokenId; }
        public String getEncryptedPAN() { return encryptedPAN; }
        public String getIv() { return iv; }
        public String getContextId() { return contextId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public LocalDateTime getLastAccessed() { return lastAccessed; }
        public long getAccessCount() { return accessCount; }

        public static class TokenVaultEntryBuilder {
            private String tokenId;
            private String encryptedPAN;
            private String iv;
            private String contextId;
            private LocalDateTime createdAt;
            private LocalDateTime expiresAt;
            private LocalDateTime lastAccessed;
            private long accessCount;

            public TokenVaultEntryBuilder tokenId(String tokenId) {
                this.tokenId = tokenId;
                return this;
            }

            public TokenVaultEntryBuilder encryptedPAN(String encryptedPAN) {
                this.encryptedPAN = encryptedPAN;
                return this;
            }

            public TokenVaultEntryBuilder iv(String iv) {
                this.iv = iv;
                return this;
            }

            public TokenVaultEntryBuilder contextId(String contextId) {
                this.contextId = contextId;
                return this;
            }

            public TokenVaultEntryBuilder createdAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public TokenVaultEntryBuilder expiresAt(LocalDateTime expiresAt) {
                this.expiresAt = expiresAt;
                return this;
            }

            public TokenVaultEntryBuilder lastAccessed(LocalDateTime lastAccessed) {
                this.lastAccessed = lastAccessed;
                return this;
            }

            public TokenVaultEntryBuilder accessCount(long accessCount) {
                this.accessCount = accessCount;
                return this;
            }

            public TokenVaultEntry build() {
                return new TokenVaultEntry(this);
            }
        }
    }

    /**
     * Token metadata for monitoring and management
     */
    public static class TokenMetadata {
        private final String tokenId;
        private final LocalDateTime createdAt;
        private final LocalDateTime lastAccessed;
        private final LocalDateTime expiresAt;
        private final long accessCount;
        private final boolean isExpired;
        private final String contextId;

        private TokenMetadata(TokenMetadataBuilder builder) {
            this.tokenId = builder.tokenId;
            this.createdAt = builder.createdAt;
            this.lastAccessed = builder.lastAccessed;
            this.expiresAt = builder.expiresAt;
            this.accessCount = builder.accessCount;
            this.isExpired = builder.isExpired;
            this.contextId = builder.contextId;
        }

        public static TokenMetadataBuilder builder() {
            return new TokenMetadataBuilder();
        }

        // Getters
        public String getTokenId() { return tokenId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getLastAccessed() { return lastAccessed; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public long getAccessCount() { return accessCount; }
        public boolean isExpired() { return isExpired; }
        public String getContextId() { return contextId; }

        public static class TokenMetadataBuilder {
            private String tokenId;
            private LocalDateTime createdAt;
            private LocalDateTime lastAccessed;
            private LocalDateTime expiresAt;
            private long accessCount;
            private boolean isExpired;
            private String contextId;

            public TokenMetadataBuilder tokenId(String tokenId) {
                this.tokenId = tokenId;
                return this;
            }

            public TokenMetadataBuilder createdAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public TokenMetadataBuilder lastAccessed(LocalDateTime lastAccessed) {
                this.lastAccessed = lastAccessed;
                return this;
            }

            public TokenMetadataBuilder expiresAt(LocalDateTime expiresAt) {
                this.expiresAt = expiresAt;
                return this;
            }

            public TokenMetadataBuilder accessCount(long accessCount) {
                this.accessCount = accessCount;
                return this;
            }

            public TokenMetadataBuilder isExpired(boolean isExpired) {
                this.isExpired = isExpired;
                return this;
            }

            public TokenMetadataBuilder contextId(String contextId) {
                this.contextId = contextId;
                return this;
            }

            public TokenMetadata build() {
                return new TokenMetadata(this);
            }
        }
    }
    
    /**
     * Token not found exception for tokenization failures
     */
    public static class TokenNotFoundException extends RuntimeException {
        public TokenNotFoundException(String message) {
            super(message);
        }
        
        public TokenNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Mask token for secure logging
     */
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
}