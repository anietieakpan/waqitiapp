package com.waqiti.common.storage;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Enterprise storage service abstraction layer providing unified access
 * to various storage backends with security, encryption, and auditing.
 */
public interface StorageService {
    
    /**
     * Store an object with automatic encryption and versioning
     */
    CompletableFuture<StorageResult> store(StorageRequest request);
    
    /**
     * Retrieve an object by key
     */
    CompletableFuture<Optional<StorageObject>> retrieve(String key, StorageOptions options);
    
    /**
     * Check if object exists
     */
    CompletableFuture<Boolean> exists(String key);
    
    /**
     * Delete an object (with audit trail)
     */
    CompletableFuture<StorageResult> delete(String key, DeletionOptions options);
    
    /**
     * List objects matching criteria
     */
    CompletableFuture<List<StorageObject>> list(StorageQuery query);
    
    /**
     * Update object metadata
     */
    CompletableFuture<StorageResult> updateMetadata(String key, Map<String, String> metadata);
    
    /**
     * Create backup of storage partition
     */
    CompletableFuture<BackupResult> createBackup(BackupRequest request);
    
    /**
     * Storage request
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class StorageRequest {
        private String key;
        private Object data;
        private Map<String, String> metadata;
        private StorageClass storageClass;
        private EncryptionLevel encryptionLevel;
        private RetentionPolicy retentionPolicy;
        private boolean enableVersioning;
        private List<String> tags;
    }
    
    /**
     * Storage options
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class StorageOptions {
        private boolean includeMetadata;
        private String version;
        private boolean decryptData;
        private ConsistencyLevel consistencyLevel;
    }
    
    /**
     * Storage object
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class StorageObject {
        private String key;
        private Object data;
        private Map<String, String> metadata;
        private LocalDateTime createdAt;
        private LocalDateTime modifiedAt;
        private String version;
        private long size;
        private String checksum;
        private boolean encrypted;
    }
    
    /**
     * Storage result
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class StorageResult {
        private boolean success;
        private String key;
        private String version;
        private String message;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;
        
        public static StorageResult success(String key, String version) {
            return StorageResult.builder()
                    .success(true)
                    .key(key)
                    .version(version)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
        
        public static StorageResult error(String key, String message) {
            return StorageResult.builder()
                    .success(false)
                    .key(key)
                    .message(message)
                    .timestamp(LocalDateTime.now())
                    .build();
        }
    }
    
    /**
     * Storage enums
     */
    enum StorageClass {
        STANDARD, FREQUENT_ACCESS, INFREQUENT_ACCESS, ARCHIVE, DEEP_ARCHIVE
    }
    
    enum EncryptionLevel {
        NONE, STANDARD, HIGH, MAXIMUM
    }
    
    enum ConsistencyLevel {
        EVENTUAL, STRONG, LINEAR
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class RetentionPolicy {
        private int retentionDays;
        private boolean autoDelete;
        private String legalHoldTag;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class DeletionOptions {
        private boolean permanentDelete;
        private String reason;
        private String deletedBy;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class StorageQuery {
        private String keyPrefix;
        private Map<String, String> metadataFilters;
        private List<String> tags;
        private LocalDateTime createdAfter;
        private LocalDateTime createdBefore;
        private int maxResults;
        private String continuationToken;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class BackupRequest {
        private String backupName;
        private List<String> keyPrefixes;
        private BackupType backupType;
        private String destinationPath;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class BackupResult {
        private boolean success;
        private String backupId;
        private String message;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long objectsBackedUp;
        private long totalSize;
    }
    
    enum BackupType {
        FULL, INCREMENTAL, DIFFERENTIAL
    }
}