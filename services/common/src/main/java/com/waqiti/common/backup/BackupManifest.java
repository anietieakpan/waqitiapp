package com.waqiti.common.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Manifest file containing metadata about a backup
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupManifest {
    
    /**
     * Unique backup identifier
     */
    private String backupId;
    
    /**
     * Timestamp when backup was created
     */
    private String timestamp;
    
    /**
     * Type of backup
     */
    private BackupRequest.BackupType backupType;
    
    /**
     * Version of the backup format
     */
    @Builder.Default
    private String version = "1.0";
    
    /**
     * Application version at time of backup
     */
    private String applicationVersion;
    
    /**
     * Who requested the backup
     */
    private String requestedBy;
    
    /**
     * List of components included in the backup
     */
    private List<BackupComponent> components;
    
    /**
     * Total size of the backup
     */
    private long totalSizeBytes;
    
    /**
     * Checksum of the entire backup
     */
    private String checksum;
    
    /**
     * Setter for backward compatibility
     */
    public void setTotalSize(long totalSize) {
        this.totalSizeBytes = totalSize;
    }
    
    /**
     * Algorithm used for checksum
     */
    @Builder.Default
    private String checksumAlgorithm = "SHA-256";
    
    /**
     * Encryption details
     */
    private EncryptionInfo encryptionInfo;
    
    /**
     * Compression details
     */
    private CompressionInfo compressionInfo;
    
    /**
     * Environment information
     */
    private EnvironmentInfo environmentInfo;
    
    /**
     * Restore instructions
     */
    private RestoreInstructions restoreInstructions;
    
    /**
     * Creation time
     */
    private Instant createdAt;
    
    /**
     * Expiration time
     */
    private Instant expiresAt;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EncryptionInfo {
        private boolean encrypted;
        private String algorithm;
        private String keyId;
        private String keyVersion;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompressionInfo {
        private boolean compressed;
        private String algorithm;
        private double compressionRatio;
        private long originalSize;
        private long compressedSize;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnvironmentInfo {
        private String environment;
        private String hostname;
        private String region;
        private String datacenter;
        private Map<String, String> systemProperties;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestoreInstructions {
        private String procedure;
        private List<String> prerequisites;
        private List<String> steps;
        private Map<String, String> configuration;
        private String estimatedDuration;
    }
}