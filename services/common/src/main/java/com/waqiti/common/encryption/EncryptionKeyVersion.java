package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;

/**
 * Encryption key version metadata
 */
@Data
@Builder
public class EncryptionKeyVersion {
    
    private int version;
    private SecretKey key;
    private LocalDateTime createdAt;
    private LocalDateTime deprecatedAt;
    private String algorithm;
    private AdvancedEncryptionService.KeyStatus status;
    
    // Explicit getters for compilation issues
    public int getVersion() { return version; }
    public SecretKey getKey() { return key; }
    
    /**
     * Check if key version is active
     */
    public boolean isActive() {
        return status == AdvancedEncryptionService.KeyStatus.ACTIVE;
    }
    
    /**
     * Check if key version is usable for decryption
     */
    public boolean isUsableForDecryption() {
        return status == AdvancedEncryptionService.KeyStatus.ACTIVE || 
               status == AdvancedEncryptionService.KeyStatus.DEPRECATED;
    }
    
    /**
     * Get safe representation for logging (without key material)
     */
    public String toSafeString() {
        return String.format("EncryptionKeyVersion{version=%d, algorithm='%s', status=%s, createdAt=%s}", 
            version, algorithm, status, createdAt);
    }
}