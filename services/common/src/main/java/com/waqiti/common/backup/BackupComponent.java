package com.waqiti.common.backup;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a component that can be backed up
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupComponent {
    
    public static class BackupComponentBuilder {
        public BackupComponentBuilder componentType(String type) {
            // Convert string to enum
            try {
                this.type = ComponentType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                this.type = ComponentType.CUSTOM;
            }
            return this;
        }
        
        public BackupComponentBuilder fileName(String fileName) {
            this.name = fileName;
            return this;
        }
        
        public BackupComponentBuilder fileSize(long fileSize) {
            this.sizeBytes = fileSize;
            return this;
        }
        
        public BackupComponentBuilder recordCount(int recordCount) {
            this.itemCount = recordCount;
            return this;
        }
        
        public BackupComponentBuilder encrypted(boolean encrypted) {
            this.encrypted = encrypted;
            return this;
        }
        
        public BackupComponentBuilder compressed(boolean compressed) {
            this.compressed = compressed;
            return this;
        }
    }
    
    /**
     * Name of the component
     */
    private String name;
    
    /**
     * Type of component
     */
    private ComponentType type;
    
    /**
     * Location of the backup data
     */
    private String location;
    
    /**
     * Size of the component backup in bytes
     */
    private long sizeBytes;
    
    /**
     * Number of items in the component
     */
    private int itemCount;
    
    /**
     * Checksum of the component data
     */
    private String checksum;
    
    /**
     * Start time of component backup
     */
    private Instant startTime;
    
    /**
     * End time of component backup
     */
    private Instant endTime;
    
    /**
     * Status of the component backup
     */
    private BackupResult.BackupStatus status;
    
    /**
     * Error message if backup failed
     */
    private String errorMessage;
    
    /**
     * List of files in this component
     */
    private List<BackupFile> files;
    
    /**
     * Component-specific metadata
     */
    private Map<String, Object> metadata;
    
    /**
     * Whether the backup is encrypted
     */
    private boolean encrypted;
    
    /**
     * Whether the backup is compressed
     */
    private boolean compressed;
    
    /**
     * Get file size (alias for sizeBytes)
     */
    public long getFileSize() {
        return sizeBytes;
    }
    
    /**
     * Dependencies of this component
     */
    private List<String> dependencies;
    
    /**
     * Restore order priority
     */
    private int restoreOrder;
    
    public enum ComponentType {
        DATABASE,
        REDIS_CACHE,
        KAFKA_TOPICS,
        CONFIGURATION,
        USER_DATA,
        TRANSACTION_DATA,
        AUDIT_LOGS,
        MEDIA_FILES,
        CERTIFICATES,
        SECRETS,
        UPLOADS,
        APPLICATION_STATE,
        CUSTOM
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackupFile {
        private String fileName;
        private String path;
        private long sizeBytes;
        private String checksum;
        private String mimeType;
        private Instant lastModified;
        private Map<String, String> attributes;
    }
}