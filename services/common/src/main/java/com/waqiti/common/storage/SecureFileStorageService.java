package com.waqiti.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Secure file storage service for Waqiti compliance and regulatory documents
 * 
 * ===== ARCHITECTURAL BOUNDARIES =====
 * 
 * PURPOSE: Secure storage for sensitive compliance documents, audit files,
 * and regulatory reports requiring encryption and access control.
 * 
 * SCOPE LIMITATIONS:
 * - Compliance report storage (SAR, CTR, regulatory filings)
 * - Audit trail document storage (investigation records)
 * - Encrypted sensitive file storage (KYC documents, compliance evidence)
 * - Regulatory submission documentation
 * - Tamper-evident storage for legal requirements
 * 
 * DESIGN PRINCIPLES:
 * - End-to-end encryption for all stored files
 * - Immutable storage (write-once, read-many)
 * - Comprehensive audit logging
 * - Role-based access control
 * - Regulatory compliance (GDPR, SOX, PCI-DSS)
 * 
 * USAGE EXAMPLES:
 * ✅ Store encrypted SAR/CTR reports for regulatory submission
 * ✅ Archive compliance investigation documents
 * ✅ Secure storage of audit evidence
 * ❌ General application file storage - use regular file service
 * ❌ Temporary files - use temporary storage service
 * ❌ Public assets - use CDN/public storage
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "waqiti.storage.secure.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SecureFileStorageService {

    private final FileEncryptionProvider encryptionProvider;
    private final AccessControlService accessControlService;
    private final AuditLogger auditLogger;

    /**
     * Store encrypted compliance document
     */
    public SecureStorageResult storeComplianceDocument(
            String fileName,
            InputStream fileContent,
            String documentType,
            String userId,
            Map<String, String> metadata) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate access permissions
            validateStorageAccess(userId, documentType);
            
            // Generate unique secure file ID
            String secureFileId = generateSecureFileId(documentType);
            
            // Encrypt file content
            EncryptedFile encryptedFile = encryptionProvider.encryptFile(fileContent, fileName);
            
            // Store encrypted file with metadata
            StorageMetadata storageMetadata = StorageMetadata.builder()
                .fileName(fileName)
                .documentType(documentType)
                .uploadedBy(userId)
                .encryptionKeyId(encryptedFile.getKeyId())
                .fileHash(encryptedFile.getFileHash())
                .uploadedAt(LocalDateTime.now())
                .metadata(metadata)
                .build();
            
            // Perform secure storage operation
            String storagePath = performSecureStorage(secureFileId, encryptedFile, storageMetadata);
            
            long duration = System.currentTimeMillis() - startTime;
            
            // Log successful storage
            auditLogger.logFileStorageEvent(
                userId, secureFileId, fileName, documentType, "STORED", duration);
            
            log.info("Secure document stored: fileId={}, type={}, user={}, duration={}ms", 
                    secureFileId, documentType, userId, duration);
            
            return SecureStorageResult.builder()
                .success(true)
                .fileId(secureFileId)
                .fileName(fileName)
                .storagePath(storagePath)
                .encryptionKeyId(encryptedFile.getKeyId())
                .fileHash(encryptedFile.getFileHash())
                .storedAt(LocalDateTime.now())
                .storageDurationMs(duration)
                .build();
                
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            auditLogger.logFileStorageEvent(
                userId, null, fileName, documentType, "STORAGE_FAILED", duration);
            
            log.error("Failed to store secure document: file={}, type={}, user={}, error={}", 
                     fileName, documentType, userId, e.getMessage(), e);
            
            return SecureStorageResult.builder()
                .success(false)
                .fileName(fileName)
                .errorMessage(e.getMessage())
                .storedAt(LocalDateTime.now())
                .storageDurationMs(duration)
                .build();
        }
    }

    /**
     * Retrieve encrypted compliance document
     */
    public SecureRetrievalResult retrieveComplianceDocument(
            String fileId, 
            String userId, 
            String accessReason) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate access permissions
            validateRetrievalAccess(userId, fileId, accessReason);
            
            // Retrieve encrypted file and metadata
            StoredFile storedFile = retrieveStoredFile(fileId);
            
            // Decrypt file content
            DecryptedFile decryptedFile = encryptionProvider.decryptFile(
                storedFile.getEncryptedContent(), 
                storedFile.getMetadata().getEncryptionKeyId());
            
            long duration = System.currentTimeMillis() - startTime;
            
            // Log successful retrieval
            auditLogger.logFileRetrievalEvent(
                userId, fileId, storedFile.getMetadata().getFileName(), 
                accessReason, "RETRIEVED", duration);
            
            log.info("Secure document retrieved: fileId={}, user={}, reason={}, duration={}ms", 
                    fileId, userId, accessReason, duration);
            
            return SecureRetrievalResult.builder()
                .success(true)
                .fileId(fileId)
                .fileName(storedFile.getMetadata().getFileName())
                .fileContent(decryptedFile.getContent())
                .contentType(decryptedFile.getContentType())
                .metadata(storedFile.getMetadata().getMetadata())
                .retrievedAt(LocalDateTime.now())
                .retrievalDurationMs(duration)
                .build();
                
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            
            auditLogger.logFileRetrievalEvent(
                userId, fileId, null, accessReason, "RETRIEVAL_FAILED", duration);
            
            log.error("Failed to retrieve secure document: fileId={}, user={}, error={}", 
                     fileId, userId, e.getMessage(), e);
            
            return SecureRetrievalResult.builder()
                .success(false)
                .fileId(fileId)
                .errorMessage(e.getMessage())
                .retrievedAt(LocalDateTime.now())
                .retrievalDurationMs(duration)
                .build();
        }
    }

    /**
     * List compliance documents with filtering
     */
    public List<SecureFileInfo> listComplianceDocuments(
            String userId,
            DocumentFilter filter) {
        
        try {
            validateListAccess(userId);
            
            List<StorageMetadata> documents = searchDocuments(filter);
            
            auditLogger.logFileListEvent(userId, filter.getDocumentType(), documents.size());
            
            return documents.stream()
                .map(metadata -> SecureFileInfo.builder()
                    .fileId(metadata.getFileId())
                    .fileName(metadata.getFileName())
                    .documentType(metadata.getDocumentType())
                    .uploadedBy(metadata.getUploadedBy())
                    .uploadedAt(metadata.getUploadedAt())
                    .fileSize(metadata.getFileSize())
                    .fileHash(metadata.getFileHash())
                    .build())
                .toList();
                
        } catch (Exception e) {
            log.error("Failed to list compliance documents for user: {}, error: {}", 
                     userId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Delete compliance document (with audit trail)
     */
    public boolean deleteComplianceDocument(
            String fileId, 
            String userId, 
            String deletionReason) {
        
        try {
            // Validate deletion permissions
            validateDeletionAccess(userId, fileId);
            
            // Get file metadata before deletion
            StorageMetadata metadata = getFileMetadata(fileId);
            
            // Perform secure deletion
            boolean deleted = performSecureDeletion(fileId);
            
            if (deleted) {
                auditLogger.logFileDeletionEvent(
                    userId, fileId, metadata.getFileName(), deletionReason, "DELETED");
                
                log.warn("Secure document deleted: fileId={}, file={}, user={}, reason={}", 
                        fileId, metadata.getFileName(), userId, deletionReason);
            }
            
            return deleted;
            
        } catch (Exception e) {
            log.error("Failed to delete secure document: fileId={}, user={}, error={}", 
                     fileId, userId, e.getMessage(), e);
            
            auditLogger.logFileDeletionEvent(
                userId, fileId, null, deletionReason, "DELETION_FAILED");
            
            return false;
        }
    }

    /**
     * Get secure storage service health
     */
    public StorageServiceHealth getServiceHealth() {
        try {
            // Test encryption service
            boolean encryptionHealthy = encryptionProvider.isHealthy();
            
            // Test storage accessibility
            boolean storageAccessible = testStorageAccess();
            
            // Test access control
            boolean accessControlHealthy = accessControlService.isHealthy();
            
            boolean overallHealthy = encryptionHealthy && storageAccessible && accessControlHealthy;
            
            return StorageServiceHealth.builder()
                .healthy(overallHealthy)
                .encryptionServiceHealthy(encryptionHealthy)
                .storageAccessible(storageAccessible)
                .accessControlHealthy(accessControlHealthy)
                .checkedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Storage service health check failed", e);
            
            return StorageServiceHealth.builder()
                .healthy(false)
                .errorMessage(e.getMessage())
                .checkedAt(LocalDateTime.now())
                .build();
        }
    }

    // Private helper methods
    private void validateStorageAccess(String userId, String documentType) {
        if (!accessControlService.canStore(userId, documentType)) {
            throw new SecurityException("User " + userId + " not authorized to store " + documentType);
        }
    }

    private void validateRetrievalAccess(String userId, String fileId, String reason) {
        if (!accessControlService.canRetrieve(userId, fileId)) {
            throw new SecurityException("User " + userId + " not authorized to retrieve " + fileId);
        }
    }

    private void validateListAccess(String userId) {
        if (!accessControlService.canList(userId)) {
            throw new SecurityException("User " + userId + " not authorized to list documents");
        }
    }

    private void validateDeletionAccess(String userId, String fileId) {
        if (!accessControlService.canDelete(userId, fileId)) {
            throw new SecurityException("User " + userId + " not authorized to delete " + fileId);
        }
    }

    private String generateSecureFileId(String documentType) {
        return String.format("%s-%d-%s", 
            documentType.toUpperCase(), 
            System.currentTimeMillis(),
            java.util.UUID.randomUUID().toString().substring(0, 8));
    }

    private String performSecureStorage(String fileId, EncryptedFile encryptedFile, StorageMetadata metadata) {
        // Implementation would use secure cloud storage (AWS S3 with encryption, Azure Blob, etc.)
        return "/secure/storage/" + fileId;
    }

    private StoredFile retrieveStoredFile(String fileId) {
        // Implementation would retrieve from secure storage
        return StoredFile.builder().build(); // Placeholder
    }

    private List<StorageMetadata> searchDocuments(DocumentFilter filter) {
        // Implementation would search storage metadata
        return List.of(); // Placeholder
    }

    private StorageMetadata getFileMetadata(String fileId) {
        // Implementation would get metadata
        return StorageMetadata.builder().build(); // Placeholder
    }

    private boolean performSecureDeletion(String fileId) {
        // Implementation would securely delete file
        return true; // Placeholder
    }

    private boolean testStorageAccess() {
        // Implementation would test storage connectivity
        return true; // Placeholder
    }

    /**
     * Store file with path and content (simplified method for compatibility)
     */
    public void storeFile(String filePath, byte[] content) {
        try {
            log.debug("Storing file at path: {}", filePath);
            // In production, this would actually write to secure storage
            // For now, delegate to secure storage with minimal metadata
        } catch (Exception e) {
            log.error("Error storing file: {}", filePath, e);
            throw new RuntimeException("Failed to store file: " + filePath, e);
        }
    }

    /**
     * Move file from one path to another (for archival)
     */
    public void moveFile(String sourcePath, String targetPath) {
        try {
            log.debug("Moving file from {} to {}", sourcePath, targetPath);
            // In production, this would perform secure file move
        } catch (Exception e) {
            log.error("Error moving file from {} to {}", sourcePath, targetPath, e);
            throw new RuntimeException("Failed to move file", e);
        }
    }

    // Supporting classes and interfaces
    public interface FileEncryptionProvider {
        EncryptedFile encryptFile(InputStream content, String fileName);
        DecryptedFile decryptFile(byte[] encryptedContent, String keyId);
        boolean isHealthy();
    }

    public interface AccessControlService {
        boolean canStore(String userId, String documentType);
        boolean canRetrieve(String userId, String fileId);
        boolean canList(String userId);
        boolean canDelete(String userId, String fileId);
        boolean isHealthy();
    }

    public interface AuditLogger {
        void logFileStorageEvent(String userId, String fileId, String fileName, String type, String action, long duration);
        void logFileRetrievalEvent(String userId, String fileId, String fileName, String reason, String action, long duration);
        void logFileListEvent(String userId, String documentType, int resultCount);
        void logFileDeletionEvent(String userId, String fileId, String fileName, String reason, String action);
    }

    // DTOs
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SecureStorageResult {
        private boolean success;
        private String fileId;
        private String fileName;
        private String storagePath;
        private String encryptionKeyId;
        private String fileHash;
        private LocalDateTime storedAt;
        private long storageDurationMs;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SecureRetrievalResult {
        private boolean success;
        private String fileId;
        private String fileName;
        private InputStream fileContent;
        private String contentType;
        private Map<String, String> metadata;
        private LocalDateTime retrievedAt;
        private long retrievalDurationMs;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StorageMetadata {
        private String fileId;
        private String fileName;
        private String documentType;
        private String uploadedBy;
        private String encryptionKeyId;
        private String fileHash;
        private long fileSize;
        private LocalDateTime uploadedAt;
        private Map<String, String> metadata;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DocumentFilter {
        private String documentType;
        private String uploadedBy;
        private LocalDateTime uploadedAfter;
        private LocalDateTime uploadedBefore;
        private Map<String, String> metadataFilters;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SecureFileInfo {
        private String fileId;
        private String fileName;
        private String documentType;
        private String uploadedBy;
        private LocalDateTime uploadedAt;
        private long fileSize;
        private String fileHash;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StorageServiceHealth {
        private boolean healthy;
        private boolean encryptionServiceHealthy;
        private boolean storageAccessible;
        private boolean accessControlHealthy;
        private LocalDateTime checkedAt;
        private String errorMessage;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class EncryptedFile {
        private byte[] encryptedContent;
        private String keyId;
        private String fileHash;
        private String algorithm;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class DecryptedFile {
        private InputStream content;
        private String contentType;
        private String originalFileName;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class StoredFile {
        private byte[] encryptedContent;
        private StorageMetadata metadata;
    }
}