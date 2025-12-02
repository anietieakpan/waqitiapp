package com.waqiti.common.security.encryption;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Container for encrypted data with metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedData {
    
    /**
     * The encrypted ciphertext (Base64 encoded)
     */
    private String ciphertext;
    
    /**
     * The encrypted data key (Base64 encoded) - used in envelope encryption
     */
    private String encryptedDataKey;
    
    /**
     * Initialization vector (Base64 encoded)
     */
    private String iv;
    
    /**
     * Encryption algorithm used
     */
    private String algorithm;
    
    /**
     * Key identifier (KMS key ID or local key alias)
     */
    private String keyId;
    
    /**
     * Version of the encryption format
     */
    @Builder.Default
    private int version = 1;
    
    /**
     * Timestamp when the data was encrypted
     */
    @Builder.Default
    private Instant encryptedAt = Instant.now();
    
    /**
     * Additional authentication data (AAD) for authenticated encryption
     */
    private String additionalData;
    
    /**
     * Validates that all required fields are present
     */
    public boolean isValid() {
        return ciphertext != null && !ciphertext.isEmpty() &&
               iv != null && !iv.isEmpty() &&
               algorithm != null && !algorithm.isEmpty() &&
               keyId != null && !keyId.isEmpty();
    }
}