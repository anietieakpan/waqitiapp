package com.waqiti.security.repository;

import com.waqiti.security.entity.FraudCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for fraud case persistence and retrieval.
 * Provides comprehensive query methods for fraud investigation and reporting.
 */
@Repository
public interface FraudCaseRepository extends JpaRepository<FraudCase, String> {
    
    Optional<FraudCase> findByCaseId(String caseId);
    
    Optional<FraudCase> findByTransactionId(String transactionId);
    
    List<FraudCase> findByUserId(String userId);
    
    List<FraudCase> findByStatus(FraudCase.Status status);
    
    Page<FraudCase> findByStatus(FraudCase.Status status, Pageable pageable);
    
    List<FraudCase> findByAssignedAnalyst(String analyst);
    
    @Query("SELECT fc FROM FraudCase fc WHERE fc.createdAt BETWEEN :startDate AND :endDate")
    List<FraudCase> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                    @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT fc FROM FraudCase fc WHERE fc.severity = :severity AND fc.status = :status")
    List<FraudCase> findBySeverityAndStatus(@Param("severity") String severity, 
                                            @Param("status") FraudCase.Status status);
    
    @Query("SELECT fc FROM FraudCase fc WHERE fc.riskScore >= :minScore")
    List<FraudCase> findHighRiskCases(@Param("minScore") Double minScore);
    
    @Query("SELECT fc FROM FraudCase fc WHERE fc.amount >= :minAmount AND fc.currency = :currency")
    List<FraudCase> findHighValueCases(@Param("minAmount") BigDecimal minAmount, 
                                       @Param("currency") String currency);
    
    @Query("SELECT fc FROM FraudCase fc WHERE fc.falsePositive = true")
    List<FraudCase> findFalsePositiveCases();
    
    @Query("SELECT COUNT(fc) FROM FraudCase fc WHERE fc.status = :status")
    Long countByStatus(@Param("status") FraudCase.Status status);
    
    @Query("SELECT COUNT(fc) FROM FraudCase fc WHERE fc.createdAt >= :since AND fc.severity = :severity")
    Long countRecentBySeverity(@Param("since") LocalDateTime since, 
                               @Param("severity") String severity);
    
    @Query("SELECT SUM(fc.preventedLoss) FROM FraudCase fc WHERE fc.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal calculatePreventedLoss(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(fc.actualLoss) FROM FraudCase fc WHERE fc.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal calculateActualLoss(@Param("startDate") LocalDateTime startDate, 
                                  @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT fc FROM FraudCase fc WHERE fc.sarId IS NOT NULL")
    List<FraudCase> findCasesWithSAR();
    
    @Query("SELECT fc FROM FraudCase fc WHERE fc.status = 'PENDING_REVIEW' ORDER BY fc.createdAt ASC")
    List<FraudCase> findPendingReviewCases();
    
    @Query("SELECT fc FROM FraudCase fc WHERE fc.userId = :userId AND fc.createdAt >= :since")
    List<FraudCase> findRecentByUserId(@Param("userId") String userId, 
                                       @Param("since") LocalDateTime since);
    
    @Query("SELECT DISTINCT fc.fraudType FROM FraudCase fc WHERE fc.createdAt >= :since")
    List<String> findRecentFraudTypes(@Param("since") LocalDateTime since);
    
    @Query("SELECT fc FROM FraudCase fc WHERE fc.correlationId = :correlationId")
    Optional<FraudCase> findByCorrelationId(@Param("correlationId") String correlationId);
    
    boolean existsByEventId(String eventId);
    
    boolean existsByTransactionId(String transactionId);
}