package com.waqiti.tax.repository;

import com.waqiti.tax.domain.TaxReturn;
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
public interface TaxReturnRepository extends JpaRepository<TaxReturn, UUID> {
    
    /**
     * Find tax return by user ID and tax year
     */
    Optional<TaxReturn> findByUserIdAndTaxYear(UUID userId, Integer taxYear);
    
    /**
     * Find all tax returns for a user
     */
    Page<TaxReturn> findByUserIdOrderByTaxYearDesc(UUID userId, Pageable pageable);
    
    /**
     * Find tax returns by status
     */
    List<TaxReturn> findByStatus(TaxReturn.TaxReturnStatus status);
    
    /**
     * Find tax returns by user and status
     */
    List<TaxReturn> findByUserIdAndStatus(UUID userId, TaxReturn.TaxReturnStatus status);
    
    /**
     * Find tax returns filed within a date range
     */
    @Query("SELECT tr FROM TaxReturn tr WHERE tr.filedAt BETWEEN :startDate AND :endDate")
    List<TaxReturn> findByFiledAtBetween(@Param("startDate") LocalDateTime startDate, 
                                        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find tax returns that need refund tracking
     */
    @Query("SELECT tr FROM TaxReturn tr WHERE tr.status = 'FILED' AND tr.estimatedRefund > 0 AND tr.refundReceived = false")
    List<TaxReturn> findPendingRefunds();
    
    /**
     * Find draft tax returns older than specified days (for cleanup)
     */
    @Query("SELECT tr FROM TaxReturn tr WHERE tr.status = 'DRAFT' AND tr.createdAt < :cutoffDate")
    List<TaxReturn> findOldDraftReturns(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Count tax returns by year
     */
    @Query("SELECT tr.taxYear, COUNT(tr) FROM TaxReturn tr GROUP BY tr.taxYear ORDER BY tr.taxYear DESC")
    List<Object[]> countByTaxYear();
    
    /**
     * Find tax returns with premium features
     */
    List<TaxReturn> findByIsPremiumTrue();
    
    /**
     * Check if user has filed return for specific year
     */
    boolean existsByUserIdAndTaxYearAndStatusIn(UUID userId, Integer taxYear, 
                                               List<TaxReturn.TaxReturnStatus> statuses);
    
    /**
     * Find tax returns requiring state filing
     */
    @Query("SELECT tr FROM TaxReturn tr WHERE tr.isStateReturnRequired = true AND tr.stateConfirmationNumber IS NULL AND tr.status = 'FILED'")
    List<TaxReturn> findPendingStateFilings();
    
    /**
     * Get user's tax return statistics
     */
    @Query("SELECT " +
           "COUNT(tr) as totalReturns, " +
           "COUNT(CASE WHEN tr.status = 'FILED' THEN 1 END) as filedReturns, " +
           "COALESCE(SUM(tr.estimatedRefund), 0) as totalRefunds, " +
           "COALESCE(AVG(tr.estimatedRefund), 0) as averageRefund " +
           "FROM TaxReturn tr WHERE tr.userId = :userId")
    Object[] getUserTaxStatistics(@Param("userId") UUID userId);
}