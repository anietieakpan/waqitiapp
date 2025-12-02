package com.waqiti.common.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request object for backup operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRequest {
    
    /**
     * Type of backup to perform
     */
    private BackupType backupType;
    
    /**
     * Components to include in the backup
     */
    private List<String> components;
    
    /**
     * User or system that requested the backup
     */
    private String requestedBy;
    
    /**
     * Additional metadata for the backup
     */
    private Map<String, Object> metadata;
    
    /**
     * Whether to encrypt the backup
     */
    @Builder.Default
    private boolean encrypt = true;
    
    /**
     * Whether to compress the backup
     */
    @Builder.Default
    private boolean compress = true;
    
    /**
     * Retention period in days
     */
    @Builder.Default
    private int retentionDays = 30;
    
    /**
     * Priority of the backup job
     */
    @Builder.Default
    private BackupPriority priority = BackupPriority.NORMAL;
    
    /**
     * Backup destination type
     */
    @Builder.Default
    private BackupDestination destination = BackupDestination.S3;
    
    /**
     * Whether to include sensitive data
     */
    @Builder.Default
    private boolean includeSensitiveData = false;
    
    /**
     * Custom backup path (optional)
     */
    private String customPath;
    
    /**
     * Tags for the backup
     */
    private List<String> tags;
    
    public enum BackupType {
        FULL,
        INCREMENTAL,
        DIFFERENTIAL,
        SELECTIVE,
        EMERGENCY
    }
    
    public enum BackupPriority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }
    
    public enum BackupDestination {
        S3,
        AZURE_BLOB,
        GCS,
        LOCAL,
        MULTIPLE
    }
    
    /**
     * Helper methods to check if specific components should be included
     */
    public boolean includeUserData() {
        return components != null && components.contains("user_data");
    }
    
    public boolean includeTransactionData() {
        return components != null && components.contains("transaction_data");
    }
    
    public boolean includePaymentData() {
        return components != null && components.contains("payment_data");
    }
    
    public boolean includeAuditData() {
        return components != null && components.contains("audit_data");
    }
    
    public boolean includeConfiguration() {
        return components != null && components.contains("configuration");
    }
    
    public boolean includeSecrets() {
        return components != null && components.contains("secrets");
    }
    
    /**
     * Whether to cleanup local files after backup
     */
    @Builder.Default
    private boolean cleanupLocal = true;
    
    public boolean isCleanupLocal() {
        return cleanupLocal;
    }
}