package com.waqiti.legal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legal Analytics Repository
 *
 * Complete data access layer for LegalAnalytics entities with custom query methods
 * Supports legal metrics, KPIs, and analytics tracking
 *
 * Note: This repository is designed for a LegalAnalytics entity that should be created
 * to track legal department analytics and metrics
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface LegalAnalyticsRepository extends JpaRepository<LegalAnalytics, UUID> {

    /**
     * Find analytics by analytics ID
     */
    Optional<LegalAnalytics> findByAnalyticsId(String analyticsId);

    /**
     * Find analytics by metric type
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.metricType = :metricType " +
           "ORDER BY a.recordDate DESC")
    List<LegalAnalytics> findByMetricType(@Param("metricType") String metricType);

    /**
     * Find analytics by category
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.category = :category")
    List<LegalAnalytics> findByCategory(@Param("category") String category);

    /**
     * Find analytics by period type
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.periodType = :periodType " +
           "ORDER BY a.periodStartDate DESC")
    List<LegalAnalytics> findByPeriodType(@Param("periodType") String periodType);

    /**
     * Find daily analytics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.periodType = 'DAILY' " +
           "ORDER BY a.periodStartDate DESC")
    List<LegalAnalytics> findDailyAnalytics();

    /**
     * Find monthly analytics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.periodType = 'MONTHLY' " +
           "ORDER BY a.periodStartDate DESC")
    List<LegalAnalytics> findMonthlyAnalytics();

    /**
     * Find quarterly analytics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.periodType = 'QUARTERLY' " +
           "ORDER BY a.periodStartDate DESC")
    List<LegalAnalytics> findQuarterlyAnalytics();

    /**
     * Find annual analytics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.periodType = 'ANNUAL' " +
           "ORDER BY a.periodStartDate DESC")
    List<LegalAnalytics> findAnnualAnalytics();

    /**
     * Find analytics within date range
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.periodStartDate >= :startDate " +
           "AND a.periodEndDate <= :endDate " +
           "ORDER BY a.periodStartDate ASC")
    List<LegalAnalytics> findByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find analytics by department
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.department = :department")
    List<LegalAnalytics> findByDepartment(@Param("department") String department);

    /**
     * Find analytics by business unit
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.businessUnit = :businessUnit")
    List<LegalAnalytics> findByBusinessUnit(@Param("businessUnit") String businessUnit);

    /**
     * Find analytics by geographic region
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.geographicRegion = :region")
    List<LegalAnalytics> findByGeographicRegion(@Param("region") String region);

    /**
     * Find litigation metrics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.category = 'LITIGATION'")
    List<LegalAnalytics> findLitigationMetrics();

    /**
     * Find contract metrics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.category = 'CONTRACT'")
    List<LegalAnalytics> findContractMetrics();

    /**
     * Find compliance metrics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.category = 'COMPLIANCE'")
    List<LegalAnalytics> findComplianceMetrics();

    /**
     * Find cost metrics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.category = 'COST' OR a.metricType LIKE '%COST%'")
    List<LegalAnalytics> findCostMetrics();

    /**
     * Find risk metrics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.category = 'RISK'")
    List<LegalAnalytics> findRiskMetrics();

    /**
     * Find performance metrics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.category = 'PERFORMANCE'")
    List<LegalAnalytics> findPerformanceMetrics();

    /**
     * Find analytics with numeric value greater than threshold
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.numericValue >= :threshold " +
           "ORDER BY a.numericValue DESC")
    List<LegalAnalytics> findByNumericValueGreaterThan(@Param("threshold") BigDecimal threshold);

    /**
     * Find analytics with numeric value less than threshold
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.numericValue < :threshold " +
           "ORDER BY a.numericValue ASC")
    List<LegalAnalytics> findByNumericValueLessThan(@Param("threshold") BigDecimal threshold);

    /**
     * Find analytics with percentage value
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.percentageValue IS NOT NULL " +
           "ORDER BY a.percentageValue DESC")
    List<LegalAnalytics> findAnalyticsWithPercentage();

    /**
     * Find analytics by trend direction
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.trendDirection = :direction")
    List<LegalAnalytics> findByTrendDirection(@Param("direction") String direction);

    /**
     * Find improving trends
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.trendDirection = 'IMPROVING' " +
           "ORDER BY a.recordDate DESC")
    List<LegalAnalytics> findImprovingTrends();

    /**
     * Find declining trends
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.trendDirection = 'DECLINING' " +
           "ORDER BY a.recordDate DESC")
    List<LegalAnalytics> findDecliningTrends();

    /**
     * Find analytics exceeding target
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.targetValue IS NOT NULL " +
           "AND a.numericValue > a.targetValue")
    List<LegalAnalytics> findExceedingTarget();

    /**
     * Find analytics below target
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.targetValue IS NOT NULL " +
           "AND a.numericValue < a.targetValue")
    List<LegalAnalytics> findBelowTarget();

    /**
     * Find analytics meeting target
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.targetValue IS NOT NULL " +
           "AND a.numericValue >= a.targetValue")
    List<LegalAnalytics> findMeetingTarget();

    /**
     * Find benchmarked analytics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.benchmarkValue IS NOT NULL")
    List<LegalAnalytics> findBenchmarkedAnalytics();

    /**
     * Find analytics above benchmark
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.benchmarkValue IS NOT NULL " +
           "AND a.numericValue > a.benchmarkValue")
    List<LegalAnalytics> findAboveBenchmark();

    /**
     * Find analytics below benchmark
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.benchmarkValue IS NOT NULL " +
           "AND a.numericValue < a.benchmarkValue")
    List<LegalAnalytics> findBelowBenchmark();

    /**
     * Find analytics by data source
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.dataSource = :dataSource")
    List<LegalAnalytics> findByDataSource(@Param("dataSource") String dataSource);

    /**
     * Find validated analytics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.validated = true")
    List<LegalAnalytics> findValidatedAnalytics();

    /**
     * Find unvalidated analytics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.validated = false OR a.validated IS NULL")
    List<LegalAnalytics> findUnvalidatedAnalytics();

    /**
     * Find analytics requiring review
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.requiresReview = true " +
           "AND a.reviewCompleted = false")
    List<LegalAnalytics> findRequiringReview();

    /**
     * Find reviewed analytics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.reviewCompleted = true")
    List<LegalAnalytics> findReviewedAnalytics();

    /**
     * Find published analytics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.published = true")
    List<LegalAnalytics> findPublishedAnalytics();

    /**
     * Find unpublished analytics
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.published = false OR a.published IS NULL")
    List<LegalAnalytics> findUnpublishedAnalytics();

    /**
     * Find latest analytics by metric type
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.metricType = :metricType " +
           "ORDER BY a.recordDate DESC")
    Optional<LegalAnalytics> findLatestByMetricType(@Param("metricType") String metricType);

    /**
     * Get year-over-year comparison for metric
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.metricType = :metricType " +
           "AND a.periodType = 'ANNUAL' " +
           "ORDER BY a.periodStartDate DESC")
    List<LegalAnalytics> findYearOverYearMetrics(@Param("metricType") String metricType);

    /**
     * Get month-over-month comparison for metric
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.metricType = :metricType " +
           "AND a.periodType = 'MONTHLY' " +
           "AND a.periodStartDate >= :startDate " +
           "ORDER BY a.periodStartDate ASC")
    List<LegalAnalytics> findMonthOverMonthMetrics(
        @Param("metricType") String metricType,
        @Param("startDate") LocalDate startDate
    );

    /**
     * Calculate average value for metric over period
     */
    @Query("SELECT AVG(a.numericValue) FROM LegalAnalytics a " +
           "WHERE a.metricType = :metricType " +
           "AND a.periodStartDate >= :startDate " +
           "AND a.periodEndDate <= :endDate")
    BigDecimal calculateAverageMetricValue(
        @Param("metricType") String metricType,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Calculate total value for metric over period
     */
    @Query("SELECT SUM(a.numericValue) FROM LegalAnalytics a " +
           "WHERE a.metricType = :metricType " +
           "AND a.periodStartDate >= :startDate " +
           "AND a.periodEndDate <= :endDate")
    BigDecimal calculateTotalMetricValue(
        @Param("metricType") String metricType,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find analytics by recorded by user
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.recordedBy = :recordedBy")
    List<LegalAnalytics> findByRecordedBy(@Param("recordedBy") String recordedBy);

    /**
     * Find analytics created within date range
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE a.createdAt BETWEEN :startDateTime AND :endDateTime " +
           "ORDER BY a.createdAt DESC")
    List<LegalAnalytics> findByCreatedAtBetween(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Count analytics by category
     */
    @Query("SELECT COUNT(a) FROM LegalAnalytics a WHERE a.category = :category")
    long countByCategory(@Param("category") String category);

    /**
     * Count analytics by metric type
     */
    @Query("SELECT COUNT(a) FROM LegalAnalytics a WHERE a.metricType = :metricType")
    long countByMetricType(@Param("metricType") String metricType);

    /**
     * Check if analytics exists for metric and period
     */
    boolean existsByMetricTypeAndPeriodStartDateAndPeriodEndDate(
        String metricType,
        LocalDate periodStartDate,
        LocalDate periodEndDate
    );

    /**
     * Get dashboard summary metrics
     */
    @Query("SELECT a.category, COUNT(*), AVG(a.numericValue), " +
           "COUNT(CASE WHEN a.trendDirection = 'IMPROVING' THEN 1 END) " +
           "FROM LegalAnalytics a " +
           "WHERE a.periodStartDate >= :startDate " +
           "GROUP BY a.category")
    List<Object[]> getDashboardSummary(@Param("startDate") LocalDate startDate);

    /**
     * Get KPI summary for period
     */
    @Query("SELECT a.metricType, a.numericValue, a.targetValue, a.benchmarkValue, a.trendDirection " +
           "FROM LegalAnalytics a " +
           "WHERE a.category = 'KPI' " +
           "AND a.periodStartDate = :periodStart " +
           "AND a.periodEndDate = :periodEnd")
    List<Object[]> getKpiSummary(
        @Param("periodStart") LocalDate periodStart,
        @Param("periodEnd") LocalDate periodEnd
    );

    /**
     * Get cost analytics summary
     */
    @Query("SELECT a.metricType, SUM(a.numericValue), AVG(a.numericValue), " +
           "MIN(a.numericValue), MAX(a.numericValue) " +
           "FROM LegalAnalytics a " +
           "WHERE a.category = 'COST' " +
           "AND a.periodStartDate >= :startDate " +
           "AND a.periodEndDate <= :endDate " +
           "GROUP BY a.metricType")
    List<Object[]> getCostAnalyticsSummary(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find anomalies (values significantly different from average)
     */
    @Query("SELECT a FROM LegalAnalytics a " +
           "WHERE a.metricType = :metricType " +
           "AND ABS(a.numericValue - :averageValue) > :threshold")
    List<LegalAnalytics> findAnomalies(
        @Param("metricType") String metricType,
        @Param("averageValue") BigDecimal averageValue,
        @Param("threshold") BigDecimal threshold
    );

    /**
     * Get trend analysis for metric
     */
    @Query("SELECT a.periodStartDate, a.numericValue, a.trendDirection " +
           "FROM LegalAnalytics a " +
           "WHERE a.metricType = :metricType " +
           "AND a.periodStartDate >= :startDate " +
           "ORDER BY a.periodStartDate ASC")
    List<Object[]> getTrendAnalysis(
        @Param("metricType") String metricType,
        @Param("startDate") LocalDate startDate
    );

    /**
     * Search analytics by description
     */
    @Query("SELECT a FROM LegalAnalytics a WHERE LOWER(a.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<LegalAnalytics> searchByDescription(@Param("searchTerm") String searchTerm);
}

/**
 * Placeholder class for LegalAnalytics entity
 * This should be created as a proper domain entity in com.waqiti.legal.domain package
 */
class LegalAnalytics {
    private UUID id;
    private String analyticsId;
    private String metricType;
    private String category;
    private String periodType;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private LocalDate recordDate;
    private String department;
    private String businessUnit;
    private String geographicRegion;
    private BigDecimal numericValue;
    private BigDecimal percentageValue;
    private String trendDirection;
    private BigDecimal targetValue;
    private BigDecimal benchmarkValue;
    private String dataSource;
    private Boolean validated;
    private Boolean requiresReview;
    private Boolean reviewCompleted;
    private Boolean published;
    private String recordedBy;
    private LocalDateTime createdAt;
    private String description;
}
