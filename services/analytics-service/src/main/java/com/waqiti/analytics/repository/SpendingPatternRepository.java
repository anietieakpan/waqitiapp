package com.waqiti.analytics.repository;

import com.waqiti.analytics.entity.SpendingPattern;
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
import java.util.UUID;

/**
 * Spending pattern repository with comprehensive analytics queries
 */
@Repository
public interface SpendingPatternRepository extends JpaRepository<SpendingPattern, UUID> {
    
    /**
     * Find spending patterns by user and date range
     */
    List<SpendingPattern> findByUserIdAndPatternDateBetween(
        UUID userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find spending patterns by user and category
     */
    Page<SpendingPattern> findByUserIdAndCategory(
        UUID userId, String category, Pageable pageable);
    
    /**
     * Find top spending categories for a user
     */
    @Query("SELECT sp FROM SpendingPattern sp WHERE sp.userId = :userId " +
           "AND sp.periodType = :periodType " +
           "ORDER BY sp.totalAmount DESC")
    List<SpendingPattern> findTopCategoriesByUserAndPeriod(
        @Param("userId") UUID userId,
        @Param("periodType") SpendingPattern.PeriodType periodType,
        Pageable pageable);
    
    /**
     * Calculate total spending for user in period
     */
    @Query("SELECT SUM(sp.totalAmount) FROM SpendingPattern sp " +
           "WHERE sp.userId = :userId " +
           "AND sp.patternDate BETWEEN :startDate AND :endDate")
    Optional<BigDecimal> calculateTotalSpending(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find patterns with significant changes
     */
    @Query("SELECT sp FROM SpendingPattern sp " +
           "WHERE sp.userId = :userId " +
           "AND ABS(sp.monthOverMonthChange) > :threshold " +
           "ORDER BY ABS(sp.monthOverMonthChange) DESC")
    List<SpendingPattern> findSignificantChanges(
        @Param("userId") UUID userId,
        @Param("threshold") BigDecimal threshold);
    
    /**
     * Get spending trend analysis
     */
    @Query("SELECT sp.category, sp.trend, COUNT(sp) as count " +
           "FROM SpendingPattern sp " +
           "WHERE sp.userId = :userId " +
           "AND sp.patternDate >= :sinceDate " +
           "GROUP BY sp.category, sp.trend")
    List<Object[]> getSpendingTrends(
        @Param("userId") UUID userId,
        @Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * Find patterns with high confidence predictions
     */
    List<SpendingPattern> findByUserIdAndConfidenceScoreGreaterThan(
        UUID userId, Double minConfidence);
    
    /**
     * Delete old patterns
     */
    void deleteByPatternDateBefore(LocalDateTime cutoffDate);
}