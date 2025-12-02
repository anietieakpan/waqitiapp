package com.waqiti.payroll.repository;

import com.waqiti.payroll.domain.BatchStatus;
import com.waqiti.payroll.domain.PayrollBatch;
import com.waqiti.payroll.domain.PayrollType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Payroll Batch Repository
 *
 * Data access layer for payroll batch operations.
 * Supports complex queries for reporting, analytics, and compliance.
 */
@Repository
public interface PayrollBatchRepository extends JpaRepository<PayrollBatch, UUID> {

    // Uniqueness checks
    boolean existsByPayrollBatchIdAndCompanyId(String payrollBatchId, String companyId);

    Optional<PayrollBatch> findByPayrollBatchIdAndCompanyId(String payrollBatchId, String companyId);

    // By company queries
    List<PayrollBatch> findByCompanyIdOrderByProcessedAtDesc(String companyId);

    Page<PayrollBatch> findByCompanyId(String companyId, Pageable pageable);

    List<PayrollBatch> findByCompanyIdAndPayPeriodBetweenOrderByPayPeriodDesc(
        String companyId, LocalDate startDate, LocalDate endDate);

    // By status queries
    List<PayrollBatch> findByStatusOrderByCreatedAtAsc(BatchStatus status);

    Page<PayrollBatch> findByStatus(BatchStatus status, Pageable pageable);

    List<PayrollBatch> findByCompanyIdAndStatus(String companyId, BatchStatus status);

    // By payroll type
    List<PayrollBatch> findByPayrollTypeAndProcessedAtBetween(
        PayrollType payrollType, LocalDateTime startDate, LocalDateTime endDate);

    // Pending/processing batches
    @Query("SELECT pb FROM PayrollBatch pb WHERE pb.status IN ('PENDING', 'PROCESSING', 'RETRYING') ORDER BY pb.createdAt ASC")
    List<PayrollBatch> findActiveBatches();

    @Query("SELECT pb FROM PayrollBatch pb WHERE pb.status IN ('PENDING', 'PROCESSING', 'RETRYING') AND pb.createdAt < :cutoffTime")
    List<PayrollBatch> findStaleBatches(@Param("cutoffTime") LocalDateTime cutoffTime);

    // Failed batches
    @Query("SELECT pb FROM PayrollBatch pb WHERE pb.status = 'FAILED' AND pb.retryCount < :maxRetries ORDER BY pb.processedAt DESC")
    List<PayrollBatch> findRetryableBatches(@Param("maxRetries") int maxRetries);

    List<PayrollBatch> findByStatusAndProcessedAtBetween(
        BatchStatus status, LocalDateTime startDate, LocalDateTime endDate);

    // Compliance queries
    @Query("SELECT pb FROM PayrollBatch pb WHERE pb.complianceViolations > 0 ORDER BY pb.processedAt DESC")
    List<PayrollBatch> findBatchesWithComplianceViolations();

    @Query("SELECT pb FROM PayrollBatch pb WHERE pb.status = 'COMPLETED_WITH_REVIEW' AND pb.approvedBy IS NULL")
    List<PayrollBatch> findBatchesPendingReview();

    // Statistics and analytics
    @Query("SELECT pb.companyId, COUNT(pb), SUM(pb.netAmount), SUM(pb.totalTaxWithheld) " +
           "FROM PayrollBatch pb " +
           "WHERE pb.status IN ('COMPLETED', 'COMPLETED_WITH_REVIEW') " +
           "AND pb.processedAt BETWEEN :startDate AND :endDate " +
           "GROUP BY pb.companyId")
    List<Object[]> getPayrollStatisticsByCompany(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT pb.payrollType, COUNT(pb), SUM(pb.netAmount) " +
           "FROM PayrollBatch pb " +
           "WHERE pb.companyId = :companyId " +
           "AND pb.processedAt BETWEEN :startDate AND :endDate " +
           "GROUP BY pb.payrollType")
    List<Object[]> getPayrollStatisticsByType(
        @Param("companyId") String companyId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    // Total amounts
    @Query("SELECT SUM(pb.netAmount) FROM PayrollBatch pb " +
           "WHERE pb.companyId = :companyId " +
           "AND pb.status IN ('COMPLETED', 'COMPLETED_WITH_REVIEW') " +
           "AND pb.payPeriod BETWEEN :startDate AND :endDate")
    BigDecimal getTotalPayrollAmountForPeriod(
        @Param("companyId") String companyId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(pb.totalTaxWithheld) FROM PayrollBatch pb " +
           "WHERE pb.companyId = :companyId " +
           "AND pb.status IN ('COMPLETED', 'COMPLETED_WITH_REVIEW') " +
           "AND pb.payPeriod BETWEEN :startDate AND :endDate")
    BigDecimal getTotalTaxWithheldForPeriod(
        @Param("companyId") String companyId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    // Success rate queries
    @Query("SELECT COUNT(pb) FROM PayrollBatch pb WHERE pb.companyId = :companyId AND pb.status = 'COMPLETED'")
    Long countSuccessfulBatches(@Param("companyId") String companyId);

    @Query("SELECT COUNT(pb) FROM PayrollBatch pb WHERE pb.companyId = :companyId AND pb.status = 'FAILED'")
    Long countFailedBatches(@Param("companyId") String companyId);

    // Processing time analytics
    @Query("SELECT AVG(pb.processingTimeMs) FROM PayrollBatch pb " +
           "WHERE pb.status IN ('COMPLETED', 'COMPLETED_WITH_REVIEW') " +
           "AND pb.processedAt BETWEEN :startDate AND :endDate")
    Double getAverageProcessingTime(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    // Recent batches
    List<PayrollBatch> findTop10ByCompanyIdOrderByProcessedAtDesc(String companyId);

    List<PayrollBatch> findTop100ByOrderByProcessedAtDesc();

    // Correlation ID lookup
    Optional<PayrollBatch> findByCorrelationId(String correlationId);

    // Cleanup queries
    @Query("DELETE FROM PayrollBatch pb WHERE pb.status IN ('COMPLETED', 'CANCELLED') AND pb.updatedAt < :cutoffDate")
    int deleteOldCompletedBatches(@Param("cutoffDate") LocalDateTime cutoffDate);
}
