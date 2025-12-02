package com.waqiti.customer.repository;

import com.waqiti.customer.entity.CustomerDocument;
import com.waqiti.customer.entity.CustomerDocument.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for CustomerDocument entity
 *
 * Provides data access methods for customer document management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Repository
public interface CustomerDocumentRepository extends JpaRepository<CustomerDocument, UUID> {

    /**
     * Find document by document ID
     *
     * @param documentId the unique document identifier
     * @return Optional containing the document if found
     */
    Optional<CustomerDocument> findByDocumentId(String documentId);

    /**
     * Find all documents for a customer
     *
     * @param customerId the customer ID
     * @return list of documents
     */
    List<CustomerDocument> findByCustomerId(String customerId);

    /**
     * Find documents by customer ID with pagination
     *
     * @param customerId the customer ID
     * @param pageable pagination information
     * @return page of documents
     */
    Page<CustomerDocument> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Find documents by type for a customer
     *
     * @param customerId the customer ID
     * @param documentType the document type
     * @return list of documents
     */
    List<CustomerDocument> findByCustomerIdAndDocumentType(String customerId, DocumentType documentType);

    /**
     * Find documents by type for a customer with pagination
     *
     * @param customerId the customer ID
     * @param documentType the document type
     * @param pageable pagination information
     * @return page of documents
     */
    Page<CustomerDocument> findByCustomerIdAndDocumentType(
        String customerId,
        DocumentType documentType,
        Pageable pageable
    );

    /**
     * Find all documents by type
     *
     * @param documentType the document type
     * @param pageable pagination information
     * @return page of documents
     */
    Page<CustomerDocument> findByDocumentType(DocumentType documentType, Pageable pageable);

    /**
     * Find sensitive documents for a customer
     *
     * @param customerId the customer ID
     * @return list of sensitive documents
     */
    @Query("SELECT d FROM CustomerDocument d WHERE d.customerId = :customerId AND d.isSensitive = true")
    List<CustomerDocument> findSensitiveDocuments(@Param("customerId") String customerId);

    /**
     * Find all sensitive documents
     *
     * @param pageable pagination information
     * @return page of sensitive documents
     */
    @Query("SELECT d FROM CustomerDocument d WHERE d.isSensitive = true")
    Page<CustomerDocument> findAllSensitiveDocuments(Pageable pageable);

    /**
     * Find encrypted documents
     *
     * @param pageable pagination information
     * @return page of encrypted documents
     */
    @Query("SELECT d FROM CustomerDocument d WHERE d.encryptionAlgorithm IS NOT NULL")
    Page<CustomerDocument> findEncryptedDocuments(Pageable pageable);

    /**
     * Find documents uploaded within date range
     *
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @param pageable pagination information
     * @return page of documents
     */
    @Query("SELECT d FROM CustomerDocument d WHERE d.uploadedAt BETWEEN :startDate AND :endDate")
    Page<CustomerDocument> findByUploadedAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Find documents uploaded by a specific user
     *
     * @param uploadedBy the user who uploaded
     * @param pageable pagination information
     * @return page of documents
     */
    Page<CustomerDocument> findByUploadedBy(String uploadedBy, Pageable pageable);

    /**
     * Find documents by MIME type
     *
     * @param mimeType the MIME type
     * @param pageable pagination information
     * @return page of documents
     */
    Page<CustomerDocument> findByMimeType(String mimeType, Pageable pageable);

    /**
     * Find expired documents for a customer
     *
     * @param customerId the customer ID
     * @param currentDate the current date
     * @return list of expired documents
     */
    @Query("SELECT d FROM CustomerDocument d WHERE d.customerId = :customerId " +
           "AND d.expiryDate IS NOT NULL AND d.expiryDate < :currentDate")
    List<CustomerDocument> findExpiredDocuments(
        @Param("customerId") String customerId,
        @Param("currentDate") LocalDate currentDate
    );

    /**
     * Find all expired documents
     *
     * @param currentDate the current date
     * @param pageable pagination information
     * @return page of expired documents
     */
    @Query("SELECT d FROM CustomerDocument d WHERE d.expiryDate IS NOT NULL AND d.expiryDate < :currentDate")
    Page<CustomerDocument> findAllExpiredDocuments(@Param("currentDate") LocalDate currentDate, Pageable pageable);

    /**
     * Find documents expiring soon
     *
     * @param currentDate the current date
     * @param expiryThreshold the expiry threshold date
     * @param pageable pagination information
     * @return page of documents expiring soon
     */
    @Query("SELECT d FROM CustomerDocument d WHERE " +
           "d.expiryDate IS NOT NULL AND " +
           "d.expiryDate > :currentDate AND " +
           "d.expiryDate <= :expiryThreshold")
    Page<CustomerDocument> findExpiringSoon(
        @Param("currentDate") LocalDate currentDate,
        @Param("expiryThreshold") LocalDate expiryThreshold,
        Pageable pageable
    );

    /**
     * Find documents past retention period
     *
     * @param currentDate the current date
     * @param pageable pagination information
     * @return page of documents past retention period
     */
    @Query("SELECT d FROM CustomerDocument d WHERE " +
           "d.retentionPeriodDays IS NOT NULL AND " +
           "d.createdAt < :thresholdDate")
    Page<CustomerDocument> findPastRetentionPeriod(
        @Param("thresholdDate") LocalDateTime currentDate,
        Pageable pageable
    );

    /**
     * Find documents by tag
     *
     * @param tag the tag to search for
     * @param pageable pagination information
     * @return page of documents
     */
    @Query(value = "SELECT * FROM customer_document WHERE :tag = ANY(tags)", nativeQuery = true)
    Page<CustomerDocument> findByTag(@Param("tag") String tag, Pageable pageable);

    /**
     * Find documents by file size range
     *
     * @param minSize minimum file size in bytes
     * @param maxSize maximum file size in bytes
     * @param pageable pagination information
     * @return page of documents
     */
    @Query("SELECT d FROM CustomerDocument d WHERE d.fileSizeBytes BETWEEN :minSize AND :maxSize")
    Page<CustomerDocument> findByFileSizeRange(
        @Param("minSize") Long minSize,
        @Param("maxSize") Long maxSize,
        Pageable pageable
    );

    /**
     * Find large documents (over 10 MB)
     *
     * @param pageable pagination information
     * @return page of large documents
     */
    @Query("SELECT d FROM CustomerDocument d WHERE d.fileSizeBytes > 10485760") // 10 MB in bytes
    Page<CustomerDocument> findLargeDocuments(Pageable pageable);

    /**
     * Count documents by customer ID
     *
     * @param customerId the customer ID
     * @return count of documents
     */
    long countByCustomerId(String customerId);

    /**
     * Count documents by type for a customer
     *
     * @param customerId the customer ID
     * @param documentType the document type
     * @return count of documents
     */
    long countByCustomerIdAndDocumentType(String customerId, DocumentType documentType);

    /**
     * Count documents by type
     *
     * @param documentType the document type
     * @return count of documents
     */
    long countByDocumentType(DocumentType documentType);

    /**
     * Count sensitive documents
     *
     * @return count of sensitive documents
     */
    @Query("SELECT COUNT(d) FROM CustomerDocument d WHERE d.isSensitive = true")
    long countSensitiveDocuments();

    /**
     * Get total storage size for customer documents
     *
     * @param customerId the customer ID
     * @return total storage size in bytes
     */
    @Query("SELECT COALESCE(SUM(d.fileSizeBytes), 0) FROM CustomerDocument d WHERE d.customerId = :customerId")
    Long getTotalStorageSize(@Param("customerId") String customerId);

    /**
     * Get total storage size across all documents
     *
     * @return total storage size in bytes
     */
    @Query("SELECT COALESCE(SUM(d.fileSizeBytes), 0) FROM CustomerDocument d")
    Long getTotalStorageSizeAll();
}
