package com.waqiti.common.security.hsm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Information about HSM keys
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HSMKeyInfo {
    
    private String keyId;
    private String label;
    private HSMKeyHandle.HSMKeyType keyType;
    private String algorithm;
    private int keySize;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsed;
    private LocalDateTime expiresAt;
    private boolean active;
    private boolean extractable;
    private boolean sensitive;
    private HSMKeyHandle.HSMKeyUsage[] usages;
    private String description;
    private String owner;
    private int usageCount;
    
    /**
     * Check if key is currently usable
     */
    public boolean isUsable() {
        return active && !isExpired();
    }
    
    /**
     * Check if key has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * Check if key supports specific usage
     */
    public boolean supportsUsage(HSMKeyHandle.HSMKeyUsage usage) {
        if (usages == null) return false;
        for (HSMKeyHandle.HSMKeyUsage u : usages) {
            if (u == usage) return true;
        }
        return false;
    }
}