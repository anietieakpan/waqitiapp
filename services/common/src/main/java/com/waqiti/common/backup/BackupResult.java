package com.waqiti.common.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Result of a backup operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupResult {
    
    public static class BackupResultBuilder {
        public BackupResultBuilder success(boolean success) {
            this.success = success;
            this.status = success ? BackupStatus.COMPLETED : BackupStatus.FAILED;
            return this;
        }
    }
    
    /**
     * Unique identifier for the backup
     */
    private String backupId;
    
    /**
     * Status of the backup operation
     */
    private BackupStatus status;
    
    /**
     * Start time of the backup
     */
    private Instant startTime;
    
    /**
     * End time of the backup
     */
    private Instant endTime;
    
    /**
     * Total size of the backup in bytes
     */
    private long sizeBytes;
    
    /**
     * Number of files backed up
     */
    private int fileCount;
    
    /**
     * Location where backup is stored
     */
    private String location;
    
    /**
     * List of components that were backed up
     */
    private List<BackupComponentResult> componentResults;
    
    /**
     * Error message if backup failed
     */
    private String errorMessage;
    
    /**
     * Backup manifest location
     */
    private String manifestLocation;
    
    /**
     * Checksum of the backup
     */
    private String checksum;
    
    /**
     * Duration of the backup in milliseconds
     */
    private long durationMs;
    
    /**
     * Whether the backup was encrypted
     */
    private boolean encrypted;
    
    /**
     * Whether the backup was compressed
     */
    private boolean compressed;
    
    /**
     * Compression ratio if compressed
     */
    private double compressionRatio;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
    
    // Additional fields for backward compatibility
    private boolean success;
    private String s3Key;
    private BackupManifest manifest;
    private boolean integrityVerified;
    private Instant completedAt;
    
    /**
     * Warnings encountered during backup
     */
    private List<String> warnings;
    
    public enum BackupStatus {
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        PARTIAL,
        CANCELLED
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackupComponentResult {
        private String componentName;
        private BackupStatus status;
        private long sizeBytes;
        private int itemCount;
        private String location;
        private String errorMessage;
        private long durationMs;
    }
}