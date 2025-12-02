package com.waqiti.reporting.repository;

import com.waqiti.reporting.domain.FinancialData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface FinancialDataRepository extends JpaRepository<FinancialData, UUID> {

    /**
     * Find financial data by date range
     */
    @Query("SELECT fd FROM FinancialData fd WHERE fd.reportDate BETWEEN :startDate AND :endDate ORDER BY fd.reportDate DESC")
    List<FinancialData> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find financial data by type and date range
     */
    @Query("SELECT fd FROM FinancialData fd WHERE fd.dataType = :dataType AND fd.reportDate BETWEEN :startDate AND :endDate ORDER BY fd.reportDate DESC")
    List<FinancialData> findByTypeAndDateRange(@Param("dataType") FinancialData.DataType dataType,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);

    /**
     * Find financial data by category and date range
     */
    @Query("SELECT fd FROM FinancialData fd WHERE fd.category = :category AND fd.reportDate BETWEEN :startDate AND :endDate ORDER BY fd.reportDate DESC")
    List<FinancialData> findByCategoryAndDateRange(@Param("category") FinancialData.MetricCategory category,
                                                  @Param("startDate") LocalDate startDate,
                                                  @Param("endDate") LocalDate endDate);

    /**
     * Get monthly revenue for a specific date
     */
    @Query("SELECT COALESCE(SUM(fd.metricValue), 0) FROM FinancialData fd WHERE " +
           "fd.dataType = 'REVENUE' AND YEAR(fd.reportDate) = YEAR(:date) AND MONTH(fd.reportDate) = MONTH(:date)")
    BigDecimal getMonthlyRevenue(@Param("date") LocalDate date);

    /**
     * Get total revenue for date range
     */
    @Query("SELECT COALESCE(SUM(fd.metricValue), 0) FROM FinancialData fd WHERE " +
           "fd.dataType = 'REVENUE' AND fd.reportDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalRevenue(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get total expenses for date range
     */
    @Query("SELECT COALESCE(SUM(fd.metricValue), 0) FROM FinancialData fd WHERE " +
           "fd.dataType = 'EXPENSE' AND fd.reportDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalExpenses(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get revenue by category for date range
     */
    @Query("SELECT fd.category, SUM(fd.metricValue) FROM FinancialData fd WHERE " +
           "fd.dataType = 'REVENUE' AND fd.reportDate BETWEEN :startDate AND :endDate GROUP BY fd.category")
    List<Object[]> getRevenueByCategoryAndDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get expense breakdown by category
     */
    @Query("SELECT fd.category, SUM(fd.metricValue) FROM FinancialData fd WHERE " +
           "fd.dataType = 'EXPENSE' AND fd.reportDate BETWEEN :startDate AND :endDate GROUP BY fd.category")
    List<Object[]> getExpenseBreakdown(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get daily transaction volumes
     */
    @Query("SELECT fd.reportDate, SUM(fd.metricValue) FROM FinancialData fd WHERE " +
           "fd.category = 'PAYMENT_VOLUME' AND fd.reportDate BETWEEN :startDate AND :endDate " +
           "GROUP BY fd.reportDate ORDER BY fd.reportDate")
    List<Object[]> getDailyTransactionVolumes(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get customer metrics for date
     */
    @Query("SELECT fd FROM FinancialData fd WHERE fd.dataType = 'CUSTOMER_METRIC' AND fd.reportDate = :date")
    List<FinancialData> getCustomerMetrics(@Param("date") LocalDate date);

    /**
     * Get operational metrics for date
     */
    @Query("SELECT fd FROM FinancialData fd WHERE fd.dataType = 'OPERATIONAL_METRIC' AND fd.reportDate = :date")
    List<FinancialData> getOperationalMetrics(@Param("date") LocalDate date);

    /**
     * Get risk metrics for date
     */
    @Query("SELECT fd FROM FinancialData fd WHERE fd.dataType = 'RISK_METRIC' AND fd.reportDate = :date")
    List<FinancialData> getRiskMetrics(@Param("date") LocalDate date);

    /**
     * Get compliance metrics for date
     */
    @Query("SELECT fd FROM FinancialData fd WHERE fd.dataType = 'COMPLIANCE_METRIC' AND fd.reportDate = :date")
    List<FinancialData> getComplianceMetrics(@Param("date") LocalDate date);

    /**
     * Get metric value by name and date
     */
    @Query("SELECT fd.metricValue FROM FinancialData fd WHERE fd.metricName = :metricName AND fd.reportDate = :date")
    BigDecimal getMetricValue(@Param("metricName") String metricName, @Param("date") LocalDate date);

    /**
     * Get average metric value over date range
     */
    @Query("SELECT AVG(fd.metricValue) FROM FinancialData fd WHERE fd.metricName = :metricName AND " +
           "fd.reportDate BETWEEN :startDate AND :endDate")
    BigDecimal getAverageMetricValue(@Param("metricName") String metricName,
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);

    /**
     * Get metric trend (daily values) over date range
     */
    @Query("SELECT fd.reportDate, fd.metricValue FROM FinancialData fd WHERE fd.metricName = :metricName AND " +
           "fd.reportDate BETWEEN :startDate AND :endDate ORDER BY fd.reportDate")
    List<Object[]> getMetricTrend(@Param("metricName") String metricName,
                                 @Param("startDate") LocalDate startDate,
                                 @Param("endDate") LocalDate endDate);

    /**
     * Find audited data only
     */
    @Query("SELECT fd FROM FinancialData fd WHERE fd.isAudited = true AND fd.reportDate BETWEEN :startDate AND :endDate")
    List<FinancialData> findAuditedDataInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find estimated data for review
     */
    @Query("SELECT fd FROM FinancialData fd WHERE fd.isEstimated = true AND fd.reportDate BETWEEN :startDate AND :endDate")
    List<FinancialData> findEstimatedDataInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get data by source and date range
     */
    @Query("SELECT fd FROM FinancialData fd WHERE fd.dataSource = :source AND fd.reportDate BETWEEN :startDate AND :endDate")
    List<FinancialData> findBySourceAndDateRange(@Param("source") String source,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    /**
     * Get unique metric names
     */
    @Query("SELECT DISTINCT fd.metricName FROM FinancialData fd ORDER BY fd.metricName")
    List<String> findUniqueMetricNames();

    /**
     * Get unique data sources
     */
    @Query("SELECT DISTINCT fd.dataSource FROM FinancialData fd ORDER BY fd.dataSource")
    List<String> findUniqueDataSources();

    /**
     * Check if data exists for metric and date
     */
    boolean existsByMetricNameAndReportDate(String metricName, LocalDate reportDate);

    /**
     * Delete data older than specified date (for cleanup)
     */
    int deleteByReportDateBefore(LocalDate cutoffDate);

    /**
     * Get data quality statistics
     */
    @Query("SELECT fd.isAudited, fd.isEstimated, COUNT(fd) FROM FinancialData fd WHERE " +
           "fd.reportDate BETWEEN :startDate AND :endDate GROUP BY fd.isAudited, fd.isEstimated")
    List<Object[]> getDataQualityStatistics(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}