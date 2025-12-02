package com.waqiti.compliance.repository;

import com.waqiti.compliance.domain.ComplianceAuditEntry;
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

/**
 * Compliance Audit Repository
 * Handles audit trail data persistence with comprehensive search capabilities
 */
@Repository
public interface ComplianceAuditRepository extends JpaRepository<ComplianceAuditEntry, UUID> {

    Optional<ComplianceAuditEntry> findTopByTransactionIdOrderByPerformedAtDesc(String transactionId);
    
    List<ComplianceAuditEntry> findByTransactionIdOrderByPerformedAt(String transactionId);
    
    List<ComplianceAuditEntry> findByPerformedAtBetweenOrderByPerformedAt(
        LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT c FROM ComplianceAuditEntry c WHERE " +
           "(:transactionId IS NULL OR c.transactionId = :transactionId) AND " +
           "(:decisionType IS NULL OR c.decisionType = :decisionType) AND " +
           "(:performedBy IS NULL OR c.performedBy = :performedBy) AND " +
           "(:startDate IS NULL OR c.performedAt >= :startDate) AND " +
           "(:endDate IS NULL OR c.performedAt <= :endDate) AND " +
           "(:riskScoreMin IS NULL OR c.riskScore >= :riskScoreMin) AND " +
           "(:riskScoreMax IS NULL OR c.riskScore <= :riskScoreMax) " +
           "ORDER BY c.performedAt DESC")
    Page<ComplianceAuditEntry> searchByCriteria(
        @Param("transactionId") String transactionId,
        @Param("decisionType") String decisionType,
        @Param("performedBy") String performedBy,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("riskScoreMin") Integer riskScoreMin,
        @Param("riskScoreMax") Integer riskScoreMax,
        Pageable pageable);
    
    @Query("SELECT COUNT(c) FROM ComplianceAuditEntry c WHERE " +
           "c.performedAt >= :startDate AND c.performedAt <= :endDate")
    long countByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
        
    @Query("SELECT c FROM ComplianceAuditEntry c WHERE " +
           "c.requiresSecondReview = true AND c.secondReviewCompletedAt IS NULL " +
           "ORDER BY c.performedAt ASC")
    Page<ComplianceAuditEntry> findPendingSecondReviews(Pageable pageable);
}
