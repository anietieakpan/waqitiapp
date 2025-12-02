package com.waqiti.investment.tax.repository;

import com.waqiti.common.repository.BaseRepository;
import com.waqiti.investment.tax.entity.TaxDocument;
import com.waqiti.investment.tax.entity.TaxDocument.DocumentType;
import com.waqiti.investment.tax.entity.TaxDocument.FilingStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Tax Documents.
 *
 * Provides queries for:
 * - Tax document generation and retrieval
 * - IRS filing management
 * - Recipient delivery tracking
 * - Compliance reporting
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Repository
public interface TaxDocumentRepository extends BaseRepository<TaxDocument, UUID> {

    /**
     * Find all tax documents for a user in a specific tax year.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.deleted = false ORDER BY t.documentType")
    List<TaxDocument> findByUserIdAndTaxYear(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Find all tax documents for an investment account in a specific tax year.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.investmentAccountId = :accountId AND t.taxYear = :taxYear AND t.deleted = false ORDER BY t.documentType")
    List<TaxDocument> findByAccountIdAndTaxYear(@Param("accountId") String accountId, @Param("taxYear") Integer taxYear);

    /**
     * Find specific tax document by type, user, and year.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.documentType = :documentType AND t.isCorrected = false AND t.deleted = false")
    Optional<TaxDocument> findByUserYearAndType(@Param("userId") UUID userId,
                                                  @Param("taxYear") Integer taxYear,
                                                  @Param("documentType") DocumentType documentType);

    /**
     * Find tax document by document number.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.documentNumber = :documentNumber AND t.deleted = false")
    Optional<TaxDocument> findByDocumentNumber(@Param("documentNumber") String documentNumber);

    /**
     * Find all documents pending IRS filing.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.filingStatus IN ('PENDING_IRS_FILING', 'REVIEWED') AND t.deleted = false ORDER BY t.taxYear DESC, t.generatedAt")
    List<TaxDocument> findPendingIrsFiling();

    /**
     * Find all documents pending recipient delivery.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.filingStatus = 'PENDING_RECIPIENT_DELIVERY' AND t.deleted = false ORDER BY t.generatedAt")
    List<TaxDocument> findPendingRecipientDelivery();

    /**
     * Find all documents by filing status.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.filingStatus = :status AND t.deleted = false ORDER BY t.generatedAt DESC")
    List<TaxDocument> findByFilingStatus(@Param("status") FilingStatus status);

    /**
     * Find all documents for a tax year.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.taxYear = :taxYear AND t.deleted = false ORDER BY t.userId, t.documentType")
    List<TaxDocument> findByTaxYear(@Param("taxYear") Integer taxYear);

    /**
     * Find all documents by type and year.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.documentType = :documentType AND t.taxYear = :taxYear AND t.deleted = false ORDER BY t.userId")
    List<TaxDocument> findByDocumentTypeAndTaxYear(@Param("documentType") DocumentType documentType,
                                                     @Param("taxYear") Integer taxYear);

    /**
     * Find corrected documents for an original document.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.originalDocumentId = :originalDocumentId AND t.isCorrected = true AND t.deleted = false ORDER BY t.correctionNumber DESC")
    List<TaxDocument> findCorrectionsForDocument(@Param("originalDocumentId") UUID originalDocumentId);

    /**
     * Find all documents filed with IRS in a date range.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.filedAt BETWEEN :startDate AND :endDate AND t.deleted = false ORDER BY t.filedAt")
    List<TaxDocument> findFiledBetweenDates(@Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate);

    /**
     * Find all documents delivered to recipients in a date range.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.deliveredToRecipientAt BETWEEN :startDate AND :endDate AND t.deleted = false ORDER BY t.deliveredToRecipientAt")
    List<TaxDocument> findDeliveredBetweenDates(@Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    /**
     * Count documents by type and year.
     */
    @Query("SELECT COUNT(t) FROM TaxDocument t WHERE t.documentType = :documentType AND t.taxYear = :taxYear AND t.deleted = false")
    Long countByDocumentTypeAndYear(@Param("documentType") DocumentType documentType,
                                     @Param("taxYear") Integer taxYear);

    /**
     * Count documents by filing status and year.
     */
    @Query("SELECT COUNT(t) FROM TaxDocument t WHERE t.filingStatus = :status AND t.taxYear = :taxYear AND t.deleted = false")
    Long countByFilingStatusAndYear(@Param("status") FilingStatus status,
                                     @Param("taxYear") Integer taxYear);

    /**
     * Find documents pending compliance review.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.filingStatus = 'PENDING_REVIEW' AND t.reviewedBy IS NULL AND t.deleted = false ORDER BY t.generatedAt")
    List<TaxDocument> findPendingComplianceReview();

    /**
     * Find documents that failed generation or filing.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.filingStatus = 'FAILED' AND t.deleted = false ORDER BY t.generatedAt DESC")
    List<TaxDocument> findFailedDocuments();

    /**
     * Check if tax documents exist for user and year.
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TaxDocument t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.deleted = false")
    Boolean existsByUserIdAndTaxYear(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Check if specific document type exists for user and year.
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TaxDocument t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.documentType = :documentType AND t.deleted = false")
    Boolean existsByUserYearAndType(@Param("userId") UUID userId,
                                     @Param("taxYear") Integer taxYear,
                                     @Param("documentType") DocumentType documentType);

    /**
     * Get filing statistics for a tax year.
     */
    @Query("""
        SELECT NEW map(
            t.filingStatus as status,
            COUNT(t) as count
        )
        FROM TaxDocument t
        WHERE t.taxYear = :taxYear AND t.deleted = false
        GROUP BY t.filingStatus
    """)
    List<Object> getFilingStatisticsByYear(@Param("taxYear") Integer taxYear);

    /**
     * Get document type distribution for a tax year.
     */
    @Query("""
        SELECT NEW map(
            t.documentType as type,
            COUNT(t) as count
        )
        FROM TaxDocument t
        WHERE t.taxYear = :taxYear AND t.deleted = false
        GROUP BY t.documentType
    """)
    List<Object> getDocumentTypeStatistics(@Param("taxYear") Integer taxYear);

    /**
     * Find documents with missing IRS confirmation number.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.filingStatus = 'FILED_WITH_IRS' AND t.irsConfirmationNumber IS NULL AND t.deleted = false")
    List<TaxDocument> findFiledWithoutConfirmation();

    /**
     * Find documents with missing delivery confirmation.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.filingStatus = 'DELIVERED_TO_RECIPIENT' AND t.deliveredToRecipientAt IS NULL AND t.deleted = false")
    List<TaxDocument> findDeliveredWithoutDate();

    /**
     * Find all 1099-B documents for a tax year.
     */
    default List<TaxDocument> find1099BForYear(Integer taxYear) {
        return findByDocumentTypeAndTaxYear(DocumentType.FORM_1099_B, taxYear);
    }

    /**
     * Find all 1099-DIV documents for a tax year.
     */
    default List<TaxDocument> find1099DIVForYear(Integer taxYear) {
        return findByDocumentTypeAndTaxYear(DocumentType.FORM_1099_DIV, taxYear);
    }

    /**
     * Find documents that need to be filed by deadline.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.filingStatus IN ('PENDING_IRS_FILING', 'REVIEWED') AND t.generatedAt < :deadline AND t.deleted = false ORDER BY t.generatedAt")
    List<TaxDocument> findPendingFilingByDeadline(@Param("deadline") LocalDate deadline);

    /**
     * Find documents that need to be delivered by deadline.
     */
    @Query("SELECT t FROM TaxDocument t WHERE t.filingStatus = 'PENDING_RECIPIENT_DELIVERY' AND t.generatedAt < :deadline AND t.deleted = false ORDER BY t.generatedAt")
    List<TaxDocument> findPendingDeliveryByDeadline(@Param("deadline") LocalDate deadline);
}
