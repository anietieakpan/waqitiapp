package com.waqiti.business.repository;

import com.waqiti.business.domain.BusinessTaxDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessTaxDocumentRepository extends JpaRepository<BusinessTaxDocument, UUID> {
    
    List<BusinessTaxDocument> findByAccountId(UUID accountId);
    
    List<BusinessTaxDocument> findByAccountIdAndYear(UUID accountId, Integer year);
    
    Page<BusinessTaxDocument> findByAccountId(UUID accountId, Pageable pageable);
    
    Optional<BusinessTaxDocument> findByIdAndAccountId(UUID id, UUID accountId);
    
    Optional<BusinessTaxDocument> findByDocumentNumber(String documentNumber);
    
    boolean existsByDocumentNumber(String documentNumber);
    
    @Query("SELECT td FROM BusinessTaxDocument td WHERE td.accountId = :accountId AND " +
           "(:documentType IS NULL OR td.documentType = :documentType) AND " +
           "(:year IS NULL OR td.year = :year) AND " +
           "(:status IS NULL OR td.status = :status)")
    Page<BusinessTaxDocument> findByFilters(@Param("accountId") UUID accountId,
                                           @Param("documentType") String documentType,
                                           @Param("year") Integer year,
                                           @Param("status") String status,
                                           Pageable pageable);
    
    @Query("SELECT td.documentType, COUNT(td) FROM BusinessTaxDocument td " +
           "WHERE td.accountId = :accountId GROUP BY td.documentType")
    List<Object[]> getDocumentCountByType(@Param("accountId") UUID accountId);
    
    @Query("SELECT td.year, COUNT(td) FROM BusinessTaxDocument td " +
           "WHERE td.accountId = :accountId GROUP BY td.year ORDER BY td.year DESC")
    List<Object[]> getDocumentCountByYear(@Param("accountId") UUID accountId);
    
    @Query("SELECT td FROM BusinessTaxDocument td WHERE td.status = 'GENERATED' AND " +
           "td.generatedAt < :cutoffDate")
    List<BusinessTaxDocument> findUnsubmittedDocumentsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Query("SELECT SUM(td.fileSize) FROM BusinessTaxDocument td WHERE td.accountId = :accountId")
    Long getTotalFileSizeByAccount(@Param("accountId") UUID accountId);
    
    List<BusinessTaxDocument> findByYearOrderByGeneratedAtDesc(Integer year);
}