package com.waqiti.common.encryption;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration for migrating a specific field
 */
@Data
@Builder
public class FieldMigrationConfig {
    
    private String columnName;
    private AdvancedEncryptionService.DataClassification expectedClassification;
    private AdvancedEncryptionService.EncryptionMethod preferredMethod;
    private boolean searchable;
    private String customValidationPattern;
    
    /**
     * Create PII field configuration
     */
    public static FieldMigrationConfig pii(String columnName) {
        return FieldMigrationConfig.builder()
            .columnName(columnName)
            .expectedClassification(AdvancedEncryptionService.DataClassification.PII)
            .preferredMethod(AdvancedEncryptionService.EncryptionMethod.AES_256_GCM_HIGH_SECURITY)
            .searchable(false)
            .build();
    }
    
    /**
     * Create financial field configuration
     */
    public static FieldMigrationConfig financial(String columnName) {
        return FieldMigrationConfig.builder()
            .columnName(columnName)
            .expectedClassification(AdvancedEncryptionService.DataClassification.FINANCIAL)
            .preferredMethod(AdvancedEncryptionService.EncryptionMethod.AES_256_GCM_HIGH_SECURITY)
            .searchable(false)
            .build();
    }
    
    /**
     * Create confidential field configuration
     */
    public static FieldMigrationConfig confidential(String columnName) {
        return FieldMigrationConfig.builder()
            .columnName(columnName)
            .expectedClassification(AdvancedEncryptionService.DataClassification.CONFIDENTIAL)
            .preferredMethod(AdvancedEncryptionService.EncryptionMethod.AES_256_GCM_MAXIMUM_SECURITY)
            .searchable(false)
            .build();
    }
    
    /**
     * Create sensitive field configuration
     */
    public static FieldMigrationConfig sensitive(String columnName) {
        return FieldMigrationConfig.builder()
            .columnName(columnName)
            .expectedClassification(AdvancedEncryptionService.DataClassification.SENSITIVE)
            .preferredMethod(AdvancedEncryptionService.EncryptionMethod.AES_256_GCM_STANDARD)
            .searchable(false)
            .build();
    }
    
    /**
     * Create searchable field configuration (uses deterministic encryption)
     */
    public static FieldMigrationConfig searchable(String columnName, AdvancedEncryptionService.DataClassification classification) {
        return FieldMigrationConfig.builder()
            .columnName(columnName)
            .expectedClassification(classification)
            .preferredMethod(AdvancedEncryptionService.EncryptionMethod.AES_256_GCM_STANDARD)
            .searchable(true)
            .build();
    }
}