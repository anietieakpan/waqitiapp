package com.waqiti.payroll.repository;

import com.waqiti.payroll.domain.PaymentStatus;
import com.waqiti.payroll.domain.PayrollPayment;
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
 * Payroll Payment Repository
 *
 * Data access layer for individual payroll payment operations.
 * Supports employee payment history, tax reporting, and audit queries.
 */
@Repository
public interface PayrollPaymentRepository extends JpaRepository<PayrollPayment, UUID> {

    // By batch
    List<PayrollPayment> findByPayrollBatchIdOrderByEmployeeIdAsc(String payrollBatchId);

    Page<PayrollPayment> findByPayrollBatchId(String payrollBatchId, Pageable pageable);

    Long countByPayrollBatchId(String payrollBatchId);

    Long countByPayrollBatchIdAndStatus(String payrollBatchId, PaymentStatus status);

    // By employee
    List<PayrollPayment> findByEmployeeIdOrderByProcessedAtDesc(String employeeId);

    Page<PayrollPayment> findByEmployeeId(String employeeId, Pageable pageable);

    List<PayrollPayment> findByEmployeeIdAndProcessedAtBetween(
        String employeeId, LocalDateTime startDate, LocalDateTime endDate);

    // By status
    List<PayrollPayment> findByStatusOrderByCreatedAtAsc(PaymentStatus status);

    List<PayrollPayment> findByPayrollBatchIdAndStatus(String payrollBatchId, PaymentStatus status);

    // Failed payments
    @Query("SELECT pp FROM PayrollPayment pp WHERE pp.status = 'FAILED' AND pp.retryCount < :maxRetries ORDER BY pp.processedAt DESC")
    List<PayrollPayment> findRetryablePayments(@Param("maxRetries") int maxRetries);

    List<PayrollPayment> findByStatusAndRetryCountLessThan(PaymentStatus status, int maxRetries);

    // Transaction lookup
    Optional<PayrollPayment> findByTransactionId(String transactionId);

    boolean existsByTransactionId(String transactionId);

    // Payment statistics
    @Query("SELECT SUM(pp.netAmount) FROM PayrollPayment pp WHERE pp.payrollBatchId = :payrollBatchId AND pp.status IN ('COMPLETED', 'SETTLED')")
    BigDecimal getTotalPaidAmount(@Param("payrollBatchId") String payrollBatchId);

    @Query("SELECT SUM(pp.taxWithheld) FROM PayrollPayment pp WHERE pp.payrollBatchId = :payrollBatchId AND pp.status IN ('COMPLETED', 'SETTLED')")
    BigDecimal getTotalTaxWithheld(@Param("payrollBatchId") String payrollBatchId);

    // Employee statistics
    @Query("SELECT SUM(pp.netAmount) FROM PayrollPayment pp " +
           "WHERE pp.employeeId = :employeeId " +
           "AND pp.status IN ('COMPLETED', 'SETTLED') " +
           "AND pp.processedAt BETWEEN :startDate AND :endDate")
    BigDecimal getEmployeeTotalEarnings(
        @Param("employeeId") String employeeId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT SUM(pp.taxWithheld) FROM PayrollPayment pp " +
           "WHERE pp.employeeId = :employeeId " +
           "AND pp.status IN ('COMPLETED', 'SETTLED') " +
           "AND pp.processedAt BETWEEN :startDate AND :endDate")
    BigDecimal getEmployeeTotalTaxWithheld(
        @Param("employeeId") String employeeId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    // Tax reporting queries (for W-2, 1099, etc.)
    @Query("SELECT pp FROM PayrollPayment pp " +
           "WHERE pp.employeeId = :employeeId " +
           "AND pp.status IN ('COMPLETED', 'SETTLED') " +
           "AND YEAR(pp.processedAt) = :year " +
           "ORDER BY pp.processedAt ASC")
    List<PayrollPayment> getEmployeeAnnualPayments(
        @Param("employeeId") String employeeId,
        @Param("year") int year);

    @Query("SELECT pp.employeeId, SUM(pp.grossAmount), SUM(pp.taxWithheld), SUM(pp.netAmount) " +
           "FROM PayrollPayment pp " +
           "WHERE pp.status IN ('COMPLETED', 'SETTLED') " +
           "AND YEAR(pp.processedAt) = :year " +
           "GROUP BY pp.employeeId")
    List<Object[]> getAnnualPayrollSummaryByEmployee(@Param("year") int year);

    // Quarter-end reporting
    @Query("SELECT pp FROM PayrollPayment pp " +
           "WHERE pp.status IN ('COMPLETED', 'SETTLED') " +
           "AND pp.processedAt BETWEEN :quarterStart AND :quarterEnd " +
           "ORDER BY pp.employeeId, pp.processedAt")
    List<PayrollPayment> getQuarterlyPayments(
        @Param("quarterStart") LocalDateTime quarterStart,
        @Param("quarterEnd") LocalDateTime quarterEnd);

    // Payment method breakdown
    @Query("SELECT pp.paymentMethod, COUNT(pp), SUM(pp.netAmount) " +
           "FROM PayrollPayment pp " +
           "WHERE pp.payrollBatchId = :payrollBatchId " +
           "GROUP BY pp.paymentMethod")
    List<Object[]> getPaymentMethodBreakdown(@Param("payrollBatchId") String payrollBatchId);

    // Recent payments
    List<PayrollPayment> findTop100ByOrderByProcessedAtDesc();

    List<PayrollPayment> findTop10ByEmployeeIdOrderByProcessedAtDesc(String employeeId);

    // Large payments (for AML monitoring)
    @Query("SELECT pp FROM PayrollPayment pp WHERE pp.netAmount > :threshold AND pp.processedAt > :since ORDER BY pp.netAmount DESC")
    List<PayrollPayment> findLargePayments(
        @Param("threshold") BigDecimal threshold,
        @Param("since") LocalDateTime since);

    // Cleanup queries
    @Query("DELETE FROM PayrollPayment pp WHERE pp.status IN ('COMPLETED', 'SETTLED', 'CANCELLED') AND pp.updatedAt < :cutoffDate")
    int deleteOldPayments(@Param("cutoffDate") LocalDateTime cutoffDate);
}
