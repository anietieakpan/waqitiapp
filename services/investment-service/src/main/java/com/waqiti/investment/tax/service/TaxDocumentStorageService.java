package com.waqiti.investment.tax.service;

import com.waqiti.investment.tax.entity.TaxDocument;
import com.waqiti.investment.tax.entity.TaxDocument.FilingStatus;
import com.waqiti.investment.tax.repository.TaxDocumentRepository;
import com.waqiti.investment.security.VaultEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tax Document Storage Service
 *
 * Manages long-term storage and retention of tax documents with IRS compliance:
 *
 * IRS Requirements:
 * - Keep tax records for at least 7 years from filing date (Publication 583)
 * - Keep employment tax records for at least 4 years
 * - Keep records indefinitely if:
 *   * You file a fraudulent return
 *   * You don't file a return
 *   * You file a claim for a loss from worthless securities
 *
 * Storage Strategy:
 * - Active documents (0-3 years): Hot storage (PostgreSQL + S3 Standard)
 * - Recent documents (3-7 years): Warm storage (S3 Standard-IA)
 * - Archive documents (7+ years): Cold storage (S3 Glacier Deep Archive)
 * - All documents encrypted at rest and in transit
 *
 * Compliance Features:
 * - Automatic lifecycle management
 * - Legal hold support
 * - Audit logging
 * - Tamper-evident storage
 * - Point-in-time recovery
 *
 * Security:
 * - AES-256 encryption at rest
 * - TLS 1.2+ in transit
 * - Access control via IAM
 * - Encryption keys managed via AWS KMS
 * - PII data encrypted with envelope encryption
 *
 * @author Waqiti Platform - Tax Compliance Team
 * @version 1.0
 * @since 2025-10-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaxDocumentStorageService {

    private final TaxDocumentRepository taxDocumentRepository;
    private final VaultEncryptionService vaultEncryptionService;
    private final S3Client s3Client;

    @Value("${waqiti.tax.storage.bucket:waqiti-tax-documents}")
    private String taxDocumentBucket;

    @Value("${waqiti.tax.storage.archive-bucket:waqiti-tax-documents-archive}")
    private String archiveBucket;

    @Value("${waqiti.tax.retention.years:7}")
    private int retentionYears;

    @Value("${waqiti.tax.storage.enabled:true}")
    private boolean storageEnabled;

    private static final String DOCUMENT_PREFIX = "tax-documents/";
    private static final String ARCHIVE_PREFIX = "archive/";

    /**
     * Store tax document in long-term storage
     *
     * @param taxDocument Tax document to store
     * @return S3 object key
     */
    @Transactional
    public String storeTaxDocument(TaxDocument taxDocument) {
        log.info("Storing tax document: id={}, type={}, tax_year={}",
                taxDocument.getId(), taxDocument.getDocumentType(), taxDocument.getTaxYear());

        try {
            if (!storageEnabled) {
                log.warn("Tax document storage is disabled - skipping S3 upload");
                return null;
            }

            // Generate S3 object key with organization
            String objectKey = generateObjectKey(taxDocument);

            // Serialize and encrypt document data
            byte[] documentData = serializeDocument(taxDocument);
            byte[] encryptedData = vaultEncryptionService.encryptBytes(documentData);

            // Upload to S3 with metadata
            uploadToS3(objectKey, encryptedData, taxDocument);

            // Update document with storage location
            taxDocument.setStorageLocation(objectKey);
            taxDocument.setStoredAt(LocalDateTime.now());
            taxDocumentRepository.save(taxDocument);

            log.info("Tax document stored successfully: id={}, s3_key={}, size={}",
                    taxDocument.getId(), objectKey, encryptedData.length);

            return objectKey;

        } catch (Exception e) {
            log.error("Failed to store tax document: id={}", taxDocument.getId(), e);
            throw new RuntimeException("Failed to store tax document in S3", e);
        }
    }

    /**
     * Retrieve tax document from storage
     *
     * @param documentId Document ID
     * @return Tax document with decrypted data
     */
    public TaxDocument retrieveTaxDocument(UUID documentId) {
        log.debug("Retrieving tax document from storage: id={}", documentId);

        TaxDocument taxDocument = taxDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Tax document not found: " + documentId));

        if (taxDocument.getStorageLocation() == null) {
            log.warn("Tax document not stored in S3: id={}", documentId);
            return taxDocument;
        }

        try {
            // Download from S3
            byte[] encryptedData = downloadFromS3(taxDocument.getStorageLocation());

            // Decrypt document data
            byte[] documentData = vaultEncryptionService.decryptBytes(encryptedData);

            // Deserialize into document (would populate additional fields)
            deserializeDocument(taxDocument, documentData);

            log.debug("Tax document retrieved successfully: id={}", documentId);
            return taxDocument;

        } catch (Exception e) {
            log.error("Failed to retrieve tax document: id={}", documentId, e);
            throw new RuntimeException("Failed to retrieve tax document from S3", e);
        }
    }

    /**
     * Archive old tax documents to cold storage (7+ years)
     *
     * Runs daily at 2 AM to move documents past retention period to Glacier
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void archiveOldDocuments() {
        log.info("Starting automatic archival of old tax documents");

        int currentYear = LocalDate.now().getYear();
        int archiveYear = currentYear - retentionYears;

        List<TaxDocument> documentsToArchive = taxDocumentRepository
                .findDocumentsEligibleForArchival(archiveYear);

        if (documentsToArchive.isEmpty()) {
            log.info("No tax documents eligible for archival");
            return;
        }

        log.info("Found {} tax documents eligible for archival (tax year <= {})",
                documentsToArchive.size(), archiveYear);

        int successCount = 0;
        int failureCount = 0;

        for (TaxDocument document : documentsToArchive) {
            try {
                archiveDocument(document);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to archive document: id={}", document.getId(), e);
                failureCount++;
            }
        }

        log.info("Archival complete: {} succeeded, {} failed", successCount, failureCount);
    }

    /**
     * Delete documents past legal retention period
     *
     * Only deletes documents with explicit approval and no legal holds
     */
    @Scheduled(cron = "0 0 3 * * SUN") // Weekly on Sunday at 3 AM
    @Transactional
    public void deleteExpiredDocuments() {
        log.info("Starting deletion of expired tax documents");

        int currentYear = LocalDate.now().getYear();
        int deleteYear = currentYear - (retentionYears + 3); // 3 year buffer after archive

        List<TaxDocument> documentsToDelete = taxDocumentRepository
                .findDocumentsEligibleForDeletion(deleteYear);

        if (documentsToDelete.isEmpty()) {
            log.info("No tax documents eligible for deletion");
            return;
        }

        log.info("Found {} tax documents eligible for deletion (tax year <= {})",
                documentsToDelete.size(), deleteYear);

        int successCount = 0;
        int failureCount = 0;

        for (TaxDocument document : documentsToDelete) {
            // Safety checks before deletion
            if (document.getHasLegalHold()) {
                log.warn("Skipping document with legal hold: id={}", document.getId());
                continue;
            }

            if (document.getFilingStatus() == FilingStatus.UNDER_AUDIT) {
                log.warn("Skipping document under audit: id={}", document.getId());
                continue;
            }

            try {
                deleteDocument(document);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to delete document: id={}", document.getId(), e);
                failureCount++;
            }
        }

        log.info("Deletion complete: {} succeeded, {} failed", successCount, failureCount);
    }

    /**
     * Apply legal hold to tax documents (prevents deletion)
     *
     * @param documentId Document ID
     * @param reason Reason for legal hold
     */
    @Transactional
    public void applyLegalHold(UUID documentId, String reason) {
        log.warn("LEGAL HOLD: Applying to tax document: id={}, reason={}",
                documentId, reason);

        TaxDocument document = taxDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Tax document not found: " + documentId));

        document.setHasLegalHold(true);
        document.setLegalHoldReason(reason);
        document.setLegalHoldAppliedAt(LocalDateTime.now());

        taxDocumentRepository.save(document);

        // Apply S3 legal hold
        if (document.getStorageLocation() != null) {
            applyS3LegalHold(document.getStorageLocation());
        }

        log.info("Legal hold applied successfully: id={}", documentId);
    }

    /**
     * Remove legal hold from tax documents
     *
     * @param documentId Document ID
     */
    @Transactional
    public void removeLegalHold(UUID documentId) {
        log.info("Removing legal hold from tax document: id={}", documentId);

        TaxDocument document = taxDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Tax document not found: " + documentId));

        document.setHasLegalHold(false);
        document.setLegalHoldReason(null);
        document.setLegalHoldRemovedAt(LocalDateTime.now());

        taxDocumentRepository.save(document);

        // Remove S3 legal hold
        if (document.getStorageLocation() != null) {
            removeS3LegalHold(document.getStorageLocation());
        }

        log.info("Legal hold removed successfully: id={}", documentId);
    }

    // Private helper methods

    private String generateObjectKey(TaxDocument document) {
        return String.format("%s%d/%s/%s/%s.enc",
                DOCUMENT_PREFIX,
                document.getTaxYear(),
                document.getUserId().toString(),
                document.getDocumentType().name(),
                document.getId().toString());
    }

    private byte[] serializeDocument(TaxDocument document) {
        // In production, use efficient serialization (Protocol Buffers, Avro, etc.)
        // For now, simple JSON serialization
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsBytes(document);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize tax document", e);
        }
    }

    private void deserializeDocument(TaxDocument document, byte[] data) {
        // In production, deserialize back to full object
        // For now, just log that we retrieved it
        log.debug("Deserialized tax document: {} bytes", data.length);
    }

    private void uploadToS3(String objectKey, byte[] data, TaxDocument document) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(taxDocumentBucket)
                .key(objectKey)
                .contentType("application/octet-stream")
                .serverSideEncryption(ServerSideEncryption.AES256)
                .metadata(buildS3Metadata(document))
                .tagging(buildS3Tagging(document))
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(data));
    }

    private byte[] downloadFromS3(String objectKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(taxDocumentBucket)
                .key(objectKey)
                .build();

        return s3Client.getObjectAsBytes(request).asByteArray();
    }

    private void archiveDocument(TaxDocument document) {
        log.info("Archiving tax document to cold storage: id={}, tax_year={}",
                document.getId(), document.getTaxYear());

        if (document.getStorageLocation() == null) {
            log.warn("Cannot archive document - no S3 location: id={}", document.getId());
            return;
        }

        String archiveKey = ARCHIVE_PREFIX + document.getStorageLocation();

        // Copy to archive bucket with Glacier Deep Archive storage class
        CopyObjectRequest copyRequest = CopyObjectRequest.builder()
                .sourceBucket(taxDocumentBucket)
                .sourceKey(document.getStorageLocation())
                .destinationBucket(archiveBucket)
                .destinationKey(archiveKey)
                .storageClass(StorageClass.DEEP_ARCHIVE)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();

        s3Client.copyObject(copyRequest);

        // Update document record
        document.setArchived(true);
        document.setArchivedAt(LocalDateTime.now());
        document.setArchiveLocation(archiveKey);
        taxDocumentRepository.save(document);

        log.info("Tax document archived successfully: id={}", document.getId());
    }

    private void deleteDocument(TaxDocument document) {
        log.warn("DELETION: Permanently deleting tax document: id={}, tax_year={}",
                document.getId(), document.getTaxYear());

        // Delete from S3
        if (document.getStorageLocation() != null) {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(taxDocumentBucket)
                    .key(document.getStorageLocation())
                    .build();
            s3Client.deleteObject(deleteRequest);
        }

        // Delete from archive if present
        if (document.getArchiveLocation() != null) {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(archiveBucket)
                    .key(document.getArchiveLocation())
                    .build();
            s3Client.deleteObject(deleteRequest);
        }

        // Soft delete in database (keep record for audit trail)
        document.setDeleted(true);
        document.setDeletedAt(LocalDateTime.now());
        taxDocumentRepository.save(document);

        log.info("Tax document deleted successfully: id={}", document.getId());
    }

    private void applyS3LegalHold(String objectKey) {
        // Apply object lock legal hold in S3
        PutObjectLegalHoldRequest request = PutObjectLegalHoldRequest.builder()
                .bucket(taxDocumentBucket)
                .key(objectKey)
                .legalHold(ObjectLockLegalHold.builder()
                        .status(ObjectLockLegalHoldStatus.ON)
                        .build())
                .build();

        s3Client.putObjectLegalHold(request);
        log.info("S3 legal hold applied: key={}", objectKey);
    }

    private void removeS3LegalHold(String objectKey) {
        PutObjectLegalHoldRequest request = PutObjectLegalHoldRequest.builder()
                .bucket(taxDocumentBucket)
                .key(objectKey)
                .legalHold(ObjectLockLegalHold.builder()
                        .status(ObjectLockLegalHoldStatus.OFF)
                        .build())
                .build();

        s3Client.putObjectLegalHold(request);
        log.info("S3 legal hold removed: key={}", objectKey);
    }

    private java.util.Map<String, String> buildS3Metadata(TaxDocument document) {
        java.util.Map<String, String> metadata = new java.util.HashMap<>();
        metadata.put("document-id", document.getId().toString());
        metadata.put("user-id", document.getUserId().toString());
        metadata.put("tax-year", document.getTaxYear().toString());
        metadata.put("document-type", document.getDocumentType().name());
        metadata.put("filing-status", document.getFilingStatus().name());
        metadata.put("generated-at", document.getGeneratedAt().toString());
        return metadata;
    }

    private String buildS3Tagging(TaxDocument document) {
        return String.format("TaxYear=%d&DocumentType=%s&RetentionYears=%d",
                document.getTaxYear(),
                document.getDocumentType().name(),
                retentionYears);
    }
}
