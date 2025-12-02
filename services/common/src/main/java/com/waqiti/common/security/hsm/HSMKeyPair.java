package com.waqiti.common.security.hsm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HSM Key Pair containing both private and public key handles
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HSMKeyPair {
    
    private HSMKeyHandle privateKey;
    private HSMKeyHandle publicKey;
    private String keyPairId;
    
    /**
     * Check if both keys are present and valid
     */
    public boolean isValid() {
        return privateKey != null && publicKey != null && 
               !privateKey.isExpired() && !publicKey.isExpired();
    }
    
    /**
     * Get the key ID (same for both keys in a pair)
     */
    public String getKeyId() {
        return keyPairId != null ? keyPairId : 
               (privateKey != null ? privateKey.getKeyId() : null);
    }
}