package com.waqiti.common.security.hsm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Handle for HSM-managed keys
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HSMKeyHandle {
    
    private String keyId;
    private String label;
    private HSMKeyType keyType;
    private String algorithm;
    private int keySize;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean extractable;
    private boolean sensitive;
    private HSMKeyUsage[] usages;
    private String hsmSlotId;
    private long hsmObjectHandle;
    
    /**
     * HSM Key Types
     */
    public enum HSMKeyType {
        SECRET_KEY,     // Symmetric key (AES, DES, etc.)
        PRIVATE_KEY,    // Private key (RSA, ECC, etc.)
        PUBLIC_KEY,     // Public key (RSA, ECC, etc.)
        KEY_PAIR        // Key pair (both private and public)
    }
    
    /**
     * HSM Key Usage flags
     */
    public enum HSMKeyUsage {
        ENCRYPT,
        DECRYPT,
        SIGN,
        VERIFY,
        WRAP,
        UNWRAP,
        DERIVE,
        GENERATE
    }
    
    /**
     * Check if key supports specific usage
     */
    public boolean supportsUsage(HSMKeyUsage usage) {
        if (usages == null) return false;
        for (HSMKeyUsage u : usages) {
            if (u == usage) return true;
        }
        return false;
    }
    
    /**
     * Check if key has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Get encrypted data (placeholder for actual HSM operation)
     */
    public byte[] getEncryptedData() {
        return null;
    }
}