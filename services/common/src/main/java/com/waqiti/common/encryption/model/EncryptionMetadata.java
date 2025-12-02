package com.waqiti.common.encryption.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Metadata for encrypted data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionMetadata {
    
    private String metadataId;
    private String keyId;
    private String keyVersion;
    private String algorithm;
    private String transformation;
    private String encryptionMode;
    private String padding;
    private int ivLength;
    private int ivSize;
    private int tagSize; // GCM authentication tag size
    private String hashAlgorithm;
    private LocalDateTime encryptedAt;
    private String encryptedBy;
    private EncryptionContext.EncryptionType dataType;
    private String dataClassification;
    private Map<String, String> customAttributes;
    
    // Compliance and audit fields
    private String complianceFramework;
    private String retentionPolicy;
    private LocalDateTime expiresAt;
    private boolean isSearchable;
    private String searchableHashAlgorithm;
    
    /**
     * Create metadata for PII encryption
     */
    public static EncryptionMetadata forPII(String keyId, String algorithm) {
        return EncryptionMetadata.builder()
            .keyId(keyId)
            .algorithm(algorithm)
            .dataType(EncryptionContext.EncryptionType.PII)
            .dataClassification("CONFIDENTIAL")
            .complianceFramework("GDPR")
            .encryptedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create metadata for financial data encryption
     */
    public static EncryptionMetadata forFinancial(String keyId, String algorithm) {
        return EncryptionMetadata.builder()
            .keyId(keyId)
            .algorithm(algorithm)
            .dataType(EncryptionContext.EncryptionType.FINANCIAL)
            .dataClassification("RESTRICTED")
            .complianceFramework("PCI-DSS")
            .encryptedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create metadata for sensitive data encryption
     */
    public static EncryptionMetadata forSensitive(String keyId, String algorithm) {
        return EncryptionMetadata.builder()
            .keyId(keyId)
            .algorithm(algorithm)
            .dataType(EncryptionContext.EncryptionType.SENSITIVE)
            .dataClassification("CONFIDENTIAL")
            .encryptedAt(LocalDateTime.now())
            .build();
    }
    
    /**
     * Check if encryption metadata is valid
     */
    public boolean isValid() {
        return keyId != null && algorithm != null && encryptedAt != null;
    }
    
    /**
     * Check if encryption has expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}