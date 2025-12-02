package com.waqiti.analytics.repository;

import com.waqiti.analytics.domain.TransactionMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TransactionMetrics entity
 * Provides data access methods for analytics and reporting
 */
@Repository
public interface TransactionMetricsRepository extends JpaRepository<TransactionMetrics, UUID> {

    /**
     * Find metrics for a specific date
     */
    Optional<TransactionMetrics> findByDate(LocalDate date);

    /**
     * Find metrics within a date range
     */
    List<TransactionMetrics> findByDateBetweenOrderByDateAsc(LocalDate startDate, LocalDate endDate);

    /**
     * Find metrics for the last N days
     */
    @Query("SELECT tm FROM TransactionMetrics tm WHERE tm.date >= :startDate ORDER BY tm.date DESC")
    List<TransactionMetrics> findLastNDays(@Param("startDate") LocalDate startDate);

    /**
     * Get total volume for a date range
     */
    @Query("SELECT COALESCE(SUM(tm.totalVolume), 0) FROM TransactionMetrics tm WHERE tm.date BETWEEN :startDate AND :endDate")
    java.math.BigDecimal getTotalVolumeForPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get total transactions for a date range
     */
    @Query("SELECT COALESCE(SUM(tm.totalTransactions), 0) FROM TransactionMetrics tm WHERE tm.date BETWEEN :startDate AND :endDate")
    Long getTotalTransactionsForPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get average daily volume for a period
     */
    @Query("SELECT AVG(tm.totalVolume) FROM TransactionMetrics tm WHERE tm.date BETWEEN :startDate AND :endDate")
    java.math.BigDecimal getAverageDailyVolumeForPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get success rate for a period
     */
    @Query("SELECT (CAST(SUM(tm.successfulTransactions) AS double) / CAST(SUM(tm.totalTransactions) AS double)) * 100 " +
           "FROM TransactionMetrics tm WHERE tm.date BETWEEN :startDate AND :endDate AND tm.totalTransactions > 0")
    Double getSuccessRateForPeriod(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find top volume days
     */
    @Query("SELECT tm FROM TransactionMetrics tm WHERE tm.date BETWEEN :startDate AND :endDate ORDER BY tm.totalVolume DESC")
    List<TransactionMetrics> findTopVolumeDays(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find metrics with most new users
     */
    @Query("SELECT tm FROM TransactionMetrics tm WHERE tm.date BETWEEN :startDate AND :endDate ORDER BY tm.newUsers DESC")
    List<TransactionMetrics> findTopNewUserDays(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Get monthly summary (aggregated by year-month) - Optimized with native query
     */
    @Query(value = "/*+ USE_INDEX(transaction_metrics, idx_transaction_metrics_date_year_month) */ " +
           "SELECT EXTRACT(YEAR FROM date) as year, " +
           "EXTRACT(MONTH FROM date) as month, " +
           "SUM(total_transactions) as totalTransactions, " +
           "SUM(total_volume) as totalVolume, " +
           "AVG(average_transaction_amount) as avgTransactionAmount " +
           "FROM transaction_metrics " +
           "WHERE date BETWEEN ?1 AND ?2 " +
           "GROUP BY EXTRACT(YEAR FROM date), EXTRACT(MONTH FROM date) " +
           "ORDER BY EXTRACT(YEAR FROM date), EXTRACT(MONTH FROM date)", 
           nativeQuery = true)
    List<java.util.Map<String, Object>> getMonthlySummary(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Check if metrics exist for a specific date
     */
    boolean existsByDate(LocalDate date);

    /**
     * Delete metrics older than specified date
     */
    void deleteByDateBefore(LocalDate cutoffDate);

    /**
     * Get the latest metrics entry
     */
    Optional<TransactionMetrics> findTopByOrderByDateDesc();

    /**
     * Find all dates with missing metrics in a range - Optimized PostgreSQL version
     */
    @Query(value = "SELECT d.date::date " +
           "FROM generate_series(?1::date, ?2::date, '1 day'::interval) d(date) " +
           "LEFT JOIN transaction_metrics tm ON d.date::date = tm.date " +
           "WHERE tm.date IS NULL " +
           "ORDER BY d.date", 
           nativeQuery = true)
    List<LocalDate> findMissingMetricsDates(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}