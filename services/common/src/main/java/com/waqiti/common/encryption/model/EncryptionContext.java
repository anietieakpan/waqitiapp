package com.waqiti.common.encryption.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Context information for encryption operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionContext {
    
    private String contextId;
    private EncryptionType encryptionType;
    private String fieldType;
    private String keyId;
    private String algorithm;
    private String userId;
    private String entityId;
    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime timestamp;
    private Map<String, String> additionalMetadata;
    private String operation; // encrypt, decrypt, rotate, etc.
    private String error; // Error message if encryption/decryption failed
    
    /**
     * Encryption context types
     */
    public enum EncryptionType {
        PII,            // Personally Identifiable Information
        FINANCIAL,      // Financial data
        SENSITIVE,      // Other sensitive data
        MEDICAL,        // Medical/health data
        LEGAL,          // Legal documents
        TEMPORARY       // Temporary encryption for transit
    }
    
    /**
     * Create context for PII encryption
     */
    public static EncryptionContext forPII(String userId) {
        return EncryptionContext.builder()
            .encryptionType(EncryptionType.PII)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create context for financial data encryption
     */
    public static EncryptionContext forFinancial(String userId) {
        return EncryptionContext.builder()
            .encryptionType(EncryptionType.FINANCIAL)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    /**
     * Create context for sensitive data encryption
     */
    public static EncryptionContext forSensitive(String userId) {
        return EncryptionContext.builder()
            .encryptionType(EncryptionType.SENSITIVE)
            .userId(userId)
            .timestamp(LocalDateTime.now())
            .build();
    }
}