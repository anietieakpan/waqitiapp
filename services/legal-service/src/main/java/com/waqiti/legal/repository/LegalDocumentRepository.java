package com.waqiti.legal.repository;

import com.waqiti.legal.domain.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legal Document Repository
 *
 * Complete data access layer for LegalDocument entities with custom query methods
 * Supports document lifecycle, version control, and compliance tracking
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface LegalDocumentRepository extends JpaRepository<LegalDocument, UUID> {

    /**
     * Find document by document ID
     */
    Optional<LegalDocument> findByDocumentId(String documentId);

    /**
     * Find documents by type
     */
    List<LegalDocument> findByDocumentType(LegalDocument.DocumentType documentType);

    /**
     * Find documents by category
     */
    List<LegalDocument> findByDocumentCategory(String documentCategory);

    /**
     * Find documents by status
     */
    List<LegalDocument> findByDocumentStatus(LegalDocument.DocumentStatus documentStatus);

    /**
     * Find documents by jurisdiction
     */
    List<LegalDocument> findByJurisdiction(String jurisdiction);

    /**
     * Find documents by confidentiality level
     */
    List<LegalDocument> findByConfidentialityLevel(LegalDocument.ConfidentialityLevel confidentialityLevel);

    /**
     * Find all active documents (ACTIVE, EXECUTED, FULLY_EXECUTED)
     */
    @Query("SELECT d FROM LegalDocument d WHERE d.documentStatus IN ('ACTIVE', 'EXECUTED', 'FULLY_EXECUTED') " +
           "AND (d.expirationDate IS NULL OR d.expirationDate > :currentDate)")
    List<LegalDocument> findActiveDocuments(@Param("currentDate") LocalDate currentDate);

    /**
     * Find expired documents
     */
    @Query("SELECT d FROM LegalDocument d WHERE d.expirationDate IS NOT NULL " +
           "AND d.expirationDate < :currentDate " +
           "AND d.documentStatus NOT IN ('EXPIRED', 'TERMINATED', 'ARCHIVED')")
    List<LegalDocument> findExpiredDocuments(@Param("currentDate") LocalDate currentDate);

    /**
     * Find documents approaching expiration (within specified days)
     */
    @Query("SELECT d FROM LegalDocument d WHERE d.expirationDate BETWEEN :startDate AND :endDate " +
           "AND d.documentStatus IN ('ACTIVE', 'EXECUTED', 'FULLY_EXECUTED')")
    List<LegalDocument> findDocumentsApproachingExpiration(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find documents requiring renewal
     */
    @Query("SELECT d FROM LegalDocument d WHERE d.renewalDate IS NOT NULL " +
           "AND d.renewalDate <= :thresholdDate " +
           "AND d.documentStatus IN ('ACTIVE', 'EXECUTED', 'FULLY_EXECUTED')")
    List<LegalDocument> findDocumentsRequiringRenewal(@Param("thresholdDate") LocalDate thresholdDate);

    /**
     * Find documents with auto-renewal enabled
     */
    List<LegalDocument> findByAutoRenewalTrue();

    /**
     * Find documents pending approval
     */
    @Query("SELECT d FROM LegalDocument d WHERE d.requiresApproval = true " +
           "AND d.documentStatus IN ('PENDING_REVIEW', 'UNDER_REVIEW')")
    List<LegalDocument> findPendingApproval();

    /**
     * Find approved documents
     */
    List<LegalDocument> findByDocumentStatusAndApprovedByNotNull(
        LegalDocument.DocumentStatus status,
        String approvedBy
    );

    /**
     * Find documents by approved by user
     */
    List<LegalDocument> findByApprovedBy(String approvedBy);

    /**
     * Find documents by created by user
     */
    List<LegalDocument> findByCreatedBy(String createdBy);

    /**
     * Find documents requiring destruction per retention policy
     */
    @Query("SELECT d FROM LegalDocument d WHERE d.destructionDate IS NOT NULL " +
           "AND d.destructionDate < :currentDate " +
           "AND d.documentStatus NOT IN ('ARCHIVED', 'TERMINATED')")
    List<LegalDocument> findDocumentsRequiringDestruction(@Param("currentDate") LocalDate currentDate);

    /**
     * Find encrypted documents
     */
    List<LegalDocument> findByEncryptedTrue();

    /**
     * Find documents by encryption key ID
     */
    List<LegalDocument> findByEncryptionKeyId(String encryptionKeyId);

    /**
     * Find documents by file format
     */
    List<LegalDocument> findByFileFormat(String fileFormat);

    /**
     * Find documents by version
     */
    List<LegalDocument> findByVersion(String version);

    /**
     * Find documents with a specific previous version
     */
    List<LegalDocument> findByPreviousVersionId(String previousVersionId);

    /**
     * Find latest version of documents by name
     */
    @Query("SELECT d FROM LegalDocument d WHERE d.documentName = :documentName " +
           "AND d.previousVersionId IS NULL " +
           "ORDER BY d.createdAt DESC")
    Optional<LegalDocument> findLatestVersionByName(@Param("documentName") String documentName);

    /**
     * Find all versions of a document
     */
    @Query("WITH RECURSIVE document_versions AS (" +
           "  SELECT * FROM legal_document WHERE document_id = :documentId " +
           "  UNION ALL " +
           "  SELECT d.* FROM legal_document d " +
           "  INNER JOIN document_versions dv ON d.previous_version_id = dv.document_id" +
           ") SELECT * FROM document_versions")
    List<LegalDocument> findAllVersions(@Param("documentId") String documentId);

    /**
     * Find documents by applicable law
     */
    List<LegalDocument> findByApplicableLaw(String applicableLaw);

    /**
     * Find documents by language
     */
    List<LegalDocument> findByDocumentLanguage(String documentLanguage);

    /**
     * Find documents effective within a date range
     */
    @Query("SELECT d FROM LegalDocument d WHERE d.effectiveDate BETWEEN :startDate AND :endDate " +
           "ORDER BY d.effectiveDate DESC")
    List<LegalDocument> findByEffectiveDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find documents by approval workflow ID
     */
    List<LegalDocument> findByApprovalWorkflowId(String approvalWorkflowId);

    /**
     * Find rejected documents
     */
    @Query("SELECT d FROM LegalDocument d WHERE d.documentStatus = 'REJECTED' " +
           "AND d.rejectionReason IS NOT NULL")
    List<LegalDocument> findRejectedDocuments();

    /**
     * Count documents by type
     */
    long countByDocumentType(LegalDocument.DocumentType documentType);

    /**
     * Count documents by status
     */
    long countByDocumentStatus(LegalDocument.DocumentStatus documentStatus);

    /**
     * Count active documents
     */
    @Query("SELECT COUNT(d) FROM LegalDocument d WHERE d.documentStatus IN ('ACTIVE', 'EXECUTED', 'FULLY_EXECUTED') " +
           "AND (d.expirationDate IS NULL OR d.expirationDate > :currentDate)")
    long countActiveDocuments(@Param("currentDate") LocalDate currentDate);

    /**
     * Check if document ID exists
     */
    boolean existsByDocumentId(String documentId);

    /**
     * Check if document name and version combination exists
     */
    boolean existsByDocumentNameAndVersion(String documentName, String version);

    /**
     * Find documents by retention years
     */
    List<LegalDocument> findByRetentionYears(Integer retentionYears);

    /**
     * Find documents created within date range
     */
    @Query("SELECT d FROM LegalDocument d WHERE d.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY d.createdAt DESC")
    List<LegalDocument> findByCreatedAtBetween(
        @Param("startDate") java.time.LocalDateTime startDate,
        @Param("endDate") java.time.LocalDateTime endDate
    );

    /**
     * Search documents by name (case-insensitive)
     */
    @Query("SELECT d FROM LegalDocument d WHERE LOWER(d.documentName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<LegalDocument> searchByDocumentName(@Param("searchTerm") String searchTerm);

    /**
     * Find documents requiring immediate attention
     */
    @Query("SELECT d FROM LegalDocument d WHERE " +
           "(d.requiresApproval = true AND d.documentStatus IN ('PENDING_REVIEW', 'UNDER_REVIEW')) " +
           "OR (d.expirationDate IS NOT NULL AND d.expirationDate BETWEEN :today AND :thresholdDate) " +
           "OR (d.destructionDate IS NOT NULL AND d.destructionDate < :today) " +
           "ORDER BY d.expirationDate ASC, d.destructionDate ASC")
    List<LegalDocument> findDocumentsRequiringAttention(
        @Param("today") LocalDate today,
        @Param("thresholdDate") LocalDate thresholdDate
    );
}
