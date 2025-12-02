package com.waqiti.ledger.repository;

import com.waqiti.ledger.domain.BankReconciliation;
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
 * Bank Reconciliation Repository
 * 
 * Provides data access for bank reconciliation records.
 * Critical for maintaining reconciliation history and audit trail.
 */
@Repository
public interface BankReconciliationRepository extends JpaRepository<BankReconciliation, UUID> {
    
    /**
     * Find reconciliations by bank account and date range
     */
    List<BankReconciliation> findByBankAccountIdAndReconciliationDateBetweenOrderByReconciliationDateDesc(
        UUID bankAccountId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Find the latest reconciliation for a bank account
     */
    Optional<BankReconciliation> findTopByBankAccountIdOrderByReconciliationDateDesc(UUID bankAccountId);
    
    /**
     * Find reconciliation by bank account and specific date
     */
    Optional<BankReconciliation> findByBankAccountIdAndReconciliationDate(
        UUID bankAccountId, LocalDate reconciliationDate);
    
    /**
     * Find all unreconciled or high-variance reconciliations
     */
    @Query("SELECT br FROM BankReconciliation br " +
           "WHERE br.reconciled = false " +
           "OR ABS(br.variance) > :varianceThreshold " +
           "ORDER BY br.reconciliationDate DESC")
    List<BankReconciliation> findProblematicReconciliations(@Param("varianceThreshold") BigDecimal varianceThreshold);
    
    /**
     * Find reconciliations pending approval
     */
    List<BankReconciliation> findByStatusOrderByReconciliationDateDesc(
        BankReconciliation.ReconciliationStatus status);
    
    /**
     * Get reconciliation statistics for a period
     */
    @Query("SELECT " +
           "COUNT(br) as totalReconciliations, " +
           "AVG(br.variance) as averageVariance, " +
           "MAX(ABS(br.variance)) as maxVariance, " +
           "AVG(br.matchingRate) as averageMatchingRate, " +
           "SUM(CASE WHEN br.reconciled = true THEN 1 ELSE 0 END) as successfulReconciliations " +
           "FROM BankReconciliation br " +
           "WHERE br.bankAccountId = :bankAccountId " +
           "AND br.reconciliationDate BETWEEN :startDate AND :endDate")
    Object[] getReconciliationStatistics(
        @Param("bankAccountId") UUID bankAccountId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
    
    /**
     * Find reconciliations with outstanding items above threshold
     */
    @Query("SELECT br FROM BankReconciliation br " +
           "WHERE br.outstandingChecksAmount > :threshold " +
           "OR br.depositsInTransitAmount > :threshold " +
           "ORDER BY br.reconciliationDate DESC")
    List<BankReconciliation> findReconciliationsWithHighOutstandingItems(
        @Param("threshold") BigDecimal threshold);
    
    /**
     * Check if reconciliation exists for a period
     */
    boolean existsByBankAccountIdAndReconciliationDate(UUID bankAccountId, LocalDate reconciliationDate);
    
    /**
     * Find reconciliations by user
     */
    List<BankReconciliation> findByReconciledByOrderByReconciledAtDesc(String reconciledBy);
    
    /**
     * Find reconciliations within date range for all accounts
     */
    List<BankReconciliation> findByReconciliationDateBetweenOrderByReconciliationDateDesc(
        LocalDate startDate, LocalDate endDate);
    
    /**
     * Delete old reconciliations (for archival)
     */
    void deleteByReconciliationDateBefore(LocalDate cutoffDate);
}