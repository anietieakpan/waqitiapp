package com.waqiti.common.security.encryption;

import lombok.Builder;
import lombok.Data;

/**
 * Data encryption key for envelope encryption
 */
@Data
@Builder
public class DataKey {
    /**
     * Plaintext key material (should be cleared after use)
     */
    private byte[] plaintext;
    
    /**
     * Encrypted key material (safe to store)
     */
    private byte[] encrypted;
    
    /**
     * Key identifier
     */
    private String keyId;
    
    /**
     * Encryption algorithm
     */
    private String algorithm;
    
    /**
     * Clears the plaintext key from memory
     */
    public void clearPlaintext() {
        if (plaintext != null) {
            java.util.Arrays.fill(plaintext, (byte) 0);
            plaintext = null;
        }
    }
}