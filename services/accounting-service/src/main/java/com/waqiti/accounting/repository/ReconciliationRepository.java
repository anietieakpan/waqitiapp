package com.waqiti.accounting.repository;

import com.waqiti.accounting.domain.ReconciliationRecord;
import com.waqiti.accounting.domain.ReconciliationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reconciliation Repository
 * Repository for account reconciliation records
 */
@Repository
public interface ReconciliationRepository extends JpaRepository<ReconciliationRecord, UUID> {

    /**
     * Find reconciliation by unique ID
     */
    Optional<ReconciliationRecord> findByReconciliationId(String reconciliationId);

    /**
     * Find reconciliations for an account
     */
    List<ReconciliationRecord> findByAccountCodeOrderByReconciliationDateDesc(String accountCode);

    /**
     * Find reconciliations by status
     */
    List<ReconciliationRecord> findByStatus(ReconciliationStatus status);

    /**
     * Find reconciliations for account and period
     */
    List<ReconciliationRecord> findByAccountCodeAndFiscalYearAndFiscalPeriod(
        String accountCode, Integer fiscalYear, String fiscalPeriod);

    /**
     * Find reconciliations within date range
     */
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.reconciliationDate BETWEEN :startDate AND :endDate " +
           "ORDER BY r.reconciliationDate DESC")
    List<ReconciliationRecord> findByDateRange(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    /**
     * Find unreviewed reconciliations
     */
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.reviewedAt IS NULL AND r.status = :status")
    List<ReconciliationRecord> findUnreviewed(@Param("status") ReconciliationStatus status);

    /**
     * Find discrepancies (unmatched reconciliations)
     */
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.status = 'DISCREPANCY' " +
           "ORDER BY r.reconciliationDate DESC")
    List<ReconciliationRecord> findDiscrepancies();

    /**
     * Get latest reconciliation for account
     */
    @Query("SELECT r FROM ReconciliationRecord r WHERE r.accountCode = :accountCode " +
           "ORDER BY r.reconciliationDate DESC")
    Optional<ReconciliationRecord> findLatestForAccount(@Param("accountCode") String accountCode);
}
