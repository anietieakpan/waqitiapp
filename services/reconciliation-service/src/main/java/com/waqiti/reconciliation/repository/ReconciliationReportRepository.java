package com.waqiti.reconciliation.repository;

import com.waqiti.reconciliation.model.ReconciliationReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing ReconciliationReport entities
 * Handles storage and retrieval of reconciliation reports and summaries
 */
@Repository
public interface ReconciliationReportRepository extends JpaRepository<ReconciliationReport, String> {

    /**
     * Find reports by date range
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.createdAt BETWEEN :startDate AND :endDate ORDER BY rr.createdAt DESC")
    List<ReconciliationReport> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find reports by status
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.status = :status ORDER BY rr.createdAt DESC")
    List<ReconciliationReport> findByStatus(@Param("status") ReconciliationReport.ReportStatus status);

    /**
     * Find latest report
     */
    @Query("SELECT rr FROM ReconciliationReport rr ORDER BY rr.createdAt DESC")
    Optional<ReconciliationReport> findLatest();

    /**
     * Find reports with discrepancies
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.discrepancyCount > 0 ORDER BY rr.discrepancyCount DESC")
    List<ReconciliationReport> findReportsWithDiscrepancies();

    /**
     * Find reports by reconciliation type
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.reconciliationType = :type ORDER BY rr.createdAt DESC")
    List<ReconciliationReport> findByReconciliationType(@Param("type") String type);

    /**
     * Find reports with errors
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.status = 'COMPLETED_WITH_ERRORS' ORDER BY rr.createdAt DESC")
    List<ReconciliationReport> findReportsWithErrors();

    /**
     * Find reports by time range and status
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.startTime BETWEEN :startRange AND :endRange " +
           "AND rr.status = :status ORDER BY rr.createdAt DESC")
    List<ReconciliationReport> findByTimeRangeAndStatus(
            @Param("startRange") LocalDateTime startRange,
            @Param("endRange") LocalDateTime endRange,
            @Param("status") ReconciliationReport.ReportStatus status
    );

    /**
     * Find daily reconciliation reports
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE DATE(rr.createdAt) = DATE(:date) ORDER BY rr.createdAt DESC")
    List<ReconciliationReport> findDailyReports(@Param("date") LocalDateTime date);

    /**
     * Count reports by status
     */
    @Query("SELECT COUNT(rr) FROM ReconciliationReport rr WHERE rr.status = :status")
    long countByStatus(@Param("status") ReconciliationReport.ReportStatus status);

    /**
     * Find reports with high discrepancy amounts
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.totalDiscrepancyAmount > :threshold ORDER BY rr.totalDiscrepancyAmount DESC")
    List<ReconciliationReport> findHighDiscrepancyReports(@Param("threshold") java.math.BigDecimal threshold);

    /**
     * Find reports by execution duration
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.endTime - rr.startTime > :durationThreshold ORDER BY (rr.endTime - rr.startTime) DESC")
    List<ReconciliationReport> findLongRunningReports(@Param("durationThreshold") long durationThreshold);

    /**
     * Find reports with pagination
     */
    @Query("SELECT rr FROM ReconciliationReport rr ORDER BY rr.createdAt DESC")
    Page<ReconciliationReport> findAllWithPaging(Pageable pageable);

    /**
     * Find reports by created user
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.createdBy = :createdBy ORDER BY rr.createdAt DESC")
    List<ReconciliationReport> findByCreatedBy(@Param("createdBy") String createdBy);

    /**
     * Find successful reports in date range
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.status = 'COMPLETED' " +
           "AND rr.createdAt BETWEEN :startDate AND :endDate ORDER BY rr.createdAt DESC")
    List<ReconciliationReport> findSuccessfulReportsByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find reports by matched transaction count range
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.matchedTransactions BETWEEN :minMatched AND :maxMatched")
    List<ReconciliationReport> findByMatchedTransactionRange(
            @Param("minMatched") int minMatched,
            @Param("maxMatched") int maxMatched
    );

    /**
     * Find reports requiring attention
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.status IN ('COMPLETED_WITH_ERRORS', 'FAILED') " +
           "OR rr.discrepancyCount > :discrepancyThreshold ORDER BY rr.createdAt DESC")
    List<ReconciliationReport> findReportsRequiringAttention(@Param("discrepancyThreshold") int discrepancyThreshold);

    /**
     * Find summary statistics by date range
     */
    @Query("SELECT " +
           "COUNT(rr) as totalReports, " +
           "SUM(rr.matchedTransactions) as totalMatched, " +
           "SUM(rr.discrepancyCount) as totalDiscrepancies, " +
           "AVG(rr.totalTransactionsProcessed) as avgTransactionsProcessed " +
           "FROM ReconciliationReport rr WHERE rr.createdAt BETWEEN :startDate AND :endDate")
    ReconciliationSummaryProjection getSummaryByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find recent failed reports
     */
    @Query("SELECT rr FROM ReconciliationReport rr WHERE rr.status = 'FAILED' " +
           "AND rr.createdAt > :since ORDER BY rr.createdAt DESC")
    List<ReconciliationReport> findRecentFailedReports(@Param("since") LocalDateTime since);

    /**
     * Find reports by correlation ID
     */
    Optional<ReconciliationReport> findByCorrelationId(String correlationId);

    /**
     * Delete old reports
     */
    @Query("DELETE FROM ReconciliationReport rr WHERE rr.createdAt < :cutoffDate")
    void deleteOldReports(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Projection for summary statistics
     */
    interface ReconciliationSummaryProjection {
        Long getTotalReports();
        Long getTotalMatched();
        Long getTotalDiscrepancies();
        Double getAvgTransactionsProcessed();
    }
}