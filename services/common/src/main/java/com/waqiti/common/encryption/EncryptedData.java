package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Encrypted data container with metadata
 */
@Data
@Builder
public class EncryptedData {
    
    private String encryptedValue;
    private String iv;
    private int keyVersion;
    private String algorithm;
    private AdvancedEncryptionService.DataClassification classification;
    private AdvancedEncryptionService.EncryptionMethod method;
    private LocalDateTime encryptedAt;
    private String aad; // Additional Authenticated Data
    
    // Explicit getters for compilation issues
    public String getEncryptedValue() { return encryptedValue; }
    public String getIv() { return iv; }
    public int getKeyVersion() { return keyVersion; }
    public String getAlgorithm() { return algorithm; }
    public AdvancedEncryptionService.DataClassification getClassification() { return classification; }
    public String getAad() { return aad; }
    
    /**
     * Get safe representation for logging (without sensitive data)
     */
    public String toSafeString() {
        return String.format("EncryptedData{keyVersion=%d, algorithm='%s', classification=%s, method=%s, encryptedAt=%s}", 
            keyVersion, algorithm, classification, method, encryptedAt);
    }
}