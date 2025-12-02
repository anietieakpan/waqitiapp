package com.waqiti.tax.repository;

import com.waqiti.tax.domain.TaxDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxDocumentRepository extends JpaRepository<TaxDocument, UUID> {
    
    /**
     * Find all documents for a tax return
     */
    List<TaxDocument> findByTaxReturnIdOrderByUploadedAtDesc(UUID taxReturnId);
    
    /**
     * Find documents by tax return and type
     */
    List<TaxDocument> findByTaxReturnIdAndDocumentType(UUID taxReturnId, 
                                                      TaxDocument.DocumentType documentType);
    
    /**
     * Find documents by tax year and type
     */
    List<TaxDocument> findByTaxYearAndDocumentType(Integer taxYear, 
                                                  TaxDocument.DocumentType documentType);
    
    /**
     * Find verified documents for a tax return
     */
    List<TaxDocument> findByTaxReturnIdAndIsVerifiedTrue(UUID taxReturnId);
    
    /**
     * Find unverified documents
     */
    List<TaxDocument> findByIsVerifiedFalse();
    
    /**
     * Find documents by issuer
     */
    List<TaxDocument> findByIssuerNameContainingIgnoreCase(String issuerName);
    
    /**
     * Find document by form ID
     */
    Optional<TaxDocument> findByFormId(String formId);
    
    /**
     * Find documents by source
     */
    List<TaxDocument> findBySource(String source);
    
    /**
     * Find documents pending processing
     */
    @Query("SELECT td FROM TaxDocument td WHERE td.isVerified = false AND td.processedAt IS NULL")
    List<TaxDocument> findPendingProcessing();
    
    /**
     * Count documents by type for a tax return
     */
    @Query("SELECT td.documentType, COUNT(td) FROM TaxDocument td WHERE td.taxReturn.id = :taxReturnId GROUP BY td.documentType")
    List<Object[]> countByDocumentType(@Param("taxReturnId") UUID taxReturnId);
    
    /**
     * Find documents with validation errors
     */
    @Query("SELECT td FROM TaxDocument td WHERE td.verificationStatus LIKE '%ERROR%' OR td.verificationStatus LIKE '%FAILED%'")
    List<TaxDocument> findWithValidationErrors();
    
    /**
     * Find documents by checksum (for duplicate detection)
     */
    Optional<TaxDocument> findByChecksumAndTaxReturnId(String checksum, UUID taxReturnId);
    
    /**
     * Get document statistics for a user
     */
    @Query("SELECT " +
           "COUNT(td) as totalDocuments, " +
           "COUNT(CASE WHEN td.isVerified = true THEN 1 END) as verifiedDocuments, " +
           "COUNT(DISTINCT td.documentType) as documentTypes, " +
           "COALESCE(SUM(td.fileSize), 0) as totalSize " +
           "FROM TaxDocument td WHERE td.taxReturn.userId = :userId")
    Object[] getUserDocumentStatistics(@Param("userId") UUID userId);
    
    /**
     * Find paginated documents for a tax return
     */
    Page<TaxDocument> findByTaxReturnIdOrderByUploadedAtDesc(UUID taxReturnId, Pageable pageable);
}