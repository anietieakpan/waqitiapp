package com.waqiti.tax.repository;

import com.waqiti.tax.domain.TaxEstimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaxEstimateRepository extends JpaRepository<TaxEstimate, UUID> {
    
    /**
     * Find current estimate for a tax return
     */
    Optional<TaxEstimate> findByTaxReturnIdAndIsCurrentTrue(UUID taxReturnId);
    
    /**
     * Find all estimates for a tax return ordered by calculation date
     */
    List<TaxEstimate> findByTaxReturnIdOrderByCalculatedAtDesc(UUID taxReturnId);
    
    /**
     * Find estimates by calculation method
     */
    List<TaxEstimate> findByCalculationMethod(String calculationMethod);
    
    /**
     * Find estimates with high confidence scores
     */
    @Query("SELECT te FROM TaxEstimate te WHERE te.confidenceScore >= :minScore")
    List<TaxEstimate> findByConfidenceScoreGreaterThanEqual(@Param("minScore") Double minScore);
    
    /**
     * Find expired estimates
     */
    @Query("SELECT te FROM TaxEstimate te WHERE te.validUntil IS NOT NULL AND te.validUntil < :now")
    List<TaxEstimate> findExpiredEstimates(@Param("now") LocalDateTime now);
    
    /**
     * Mark all previous estimates as not current
     */
    @Modifying
    @Query("UPDATE TaxEstimate te SET te.isCurrent = false WHERE te.taxReturn.id = :taxReturnId AND te.isCurrent = true")
    int markPreviousEstimatesAsNotCurrent(@Param("taxReturnId") UUID taxReturnId);
    
    /**
     * Get estimate history for a tax return
     */
    @Query("SELECT te FROM TaxEstimate te WHERE te.taxReturn.id = :taxReturnId ORDER BY te.calculatedAt DESC")
    List<TaxEstimate> getEstimateHistory(@Param("taxReturnId") UUID taxReturnId);
    
    /**
     * Find estimates with optimization suggestions
     */
    @Query("SELECT te FROM TaxEstimate te WHERE te.optimizationSuggestions IS NOT NULL AND te.optimizationSuggestions <> ''")
    List<TaxEstimate> findWithOptimizationSuggestions();
    
    /**
     * Get average refund amount for estimates
     */
    @Query("SELECT AVG(te.estimatedRefund) FROM TaxEstimate te WHERE te.estimatedRefund > 0")
    Optional<Double> getAverageRefundAmount();
    
    /**
     * Count estimates by confidence score ranges
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN te.confidenceScore >= 0.9 THEN 1 END) as highConfidence, " +
           "COUNT(CASE WHEN te.confidenceScore >= 0.7 AND te.confidenceScore < 0.9 THEN 1 END) as mediumConfidence, " +
           "COUNT(CASE WHEN te.confidenceScore < 0.7 THEN 1 END) as lowConfidence " +
           "FROM TaxEstimate te")
    Object[] getConfidenceDistribution();
    
    /**
     * Find recent estimates for analytics
     */
    @Query("SELECT te FROM TaxEstimate te WHERE te.calculatedAt >= :since ORDER BY te.calculatedAt DESC")
    List<TaxEstimate> findRecentEstimates(@Param("since") LocalDateTime since);
    
    /**
     * Delete old estimates (cleanup)
     */
    @Modifying
    @Query("DELETE FROM TaxEstimate te WHERE te.isCurrent = false AND te.calculatedAt < :cutoffDate")
    int deleteOldEstimates(@Param("cutoffDate") LocalDateTime cutoffDate);
}