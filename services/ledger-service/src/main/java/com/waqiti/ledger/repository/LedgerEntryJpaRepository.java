package com.waqiti.ledger.repository;

import com.waqiti.ledger.entity.LedgerEntryEntity;
import com.waqiti.ledger.entity.LedgerEntryEntity.EntryType;
import com.waqiti.ledger.entity.LedgerEntryEntity.EntryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JPA Repository for Ledger Entry Entity
 * 
 * Provides data access operations for ledger entries with
 * specialized queries for financial calculations and reporting.
 */
@Repository
public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    // Basic queries
    List<LedgerEntryEntity> findByAccountAccountId(UUID accountId);
    
    List<LedgerEntryEntity> findByTransactionTransactionId(UUID transactionId);
    
    List<LedgerEntryEntity> findByAccountAccountIdOrderByTransactionDateAsc(UUID accountId);
    
    List<LedgerEntryEntity> findByAccountAccountIdOrderByTransactionDateDesc(UUID accountId);
    
    // Date range queries
    List<LedgerEntryEntity> findByTransactionDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    List<LedgerEntryEntity> findByAccountAccountIdAndTransactionDateBetween(
        UUID accountId, LocalDateTime startDate, LocalDateTime endDate);
    
    List<LedgerEntryEntity> findByAccountAccountIdAndTransactionDateBetweenOrderByTransactionDateAsc(
        UUID accountId, LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT le FROM LedgerEntryEntity le WHERE le.account.accountId = :accountId " +
           "AND le.transactionDate <= :asOfDate ORDER BY le.transactionDate ASC")
    List<LedgerEntryEntity> findByAccountIdUpToDate(
        @Param("accountId") UUID accountId,
        @Param("asOfDate") LocalDateTime asOfDate);
    
    // Status and type queries
    List<LedgerEntryEntity> findByStatus(EntryStatus status);
    
    List<LedgerEntryEntity> findByEntryType(EntryType entryType);
    
    List<LedgerEntryEntity> findByStatusAndTransactionDateBetween(
        EntryStatus status, LocalDateTime startDate, LocalDateTime endDate);
    
    // Period queries
    List<LedgerEntryEntity> findByAccountingPeriod(String accountingPeriod);
    
    @Query("SELECT le FROM LedgerEntryEntity le WHERE le.accountingPeriod = :period " +
           "AND le.account.companyId = :companyId")
    List<LedgerEntryEntity> findByPeriodAndCompany(
        @Param("period") String period,
        @Param("companyId") UUID companyId);
    
    // Balance calculation queries
    @Query("SELECT SUM(CASE WHEN le.entryType = 'DEBIT' THEN le.amount ELSE 0 END) - " +
           "SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount ELSE 0 END) " +
           "FROM LedgerEntryEntity le WHERE le.account.accountId = :accountId " +
           "AND le.transactionDate <= :asOfDate AND le.status = 'POSTED'")
    BigDecimal calculateAccountBalance(
        @Param("accountId") UUID accountId,
        @Param("asOfDate") LocalDateTime asOfDate);
    
    @Query("SELECT SUM(CASE WHEN le.entryType = 'DEBIT' THEN le.amount ELSE 0 END) - " +
           "SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount ELSE 0 END) " +
           "FROM LedgerEntryEntity le WHERE le.account.accountId = :accountId " +
           "AND le.transactionDate BETWEEN :startDate AND :endDate AND le.status = 'POSTED'")
    BigDecimal calculatePeriodMovement(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // Aggregation queries
    @Query("SELECT le.entryType, SUM(le.amount) FROM LedgerEntryEntity le " +
           "WHERE le.account.accountId = :accountId " +
           "AND le.transactionDate BETWEEN :startDate AND :endDate " +
           "GROUP BY le.entryType")
    List<Object[]> sumByEntryType(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT DATE(le.transactionDate), SUM(le.amount) FROM LedgerEntryEntity le " +
           "WHERE le.account.accountId = :accountId AND le.entryType = :entryType " +
           "AND le.transactionDate BETWEEN :startDate AND :endDate " +
           "GROUP BY DATE(le.transactionDate)")
    List<Object[]> dailySum(
        @Param("accountId") UUID accountId,
        @Param("entryType") EntryType entryType,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // Reconciliation queries
    List<LedgerEntryEntity> findByReconciledFalse();
    
    List<LedgerEntryEntity> findByAccountAccountIdAndReconciledFalse(UUID accountId);
    
    List<LedgerEntryEntity> findByReconciliationId(UUID reconciliationId);
    
    @Modifying
    @Query("UPDATE LedgerEntryEntity le SET le.reconciled = true, " +
           "le.reconciliationId = :reconciliationId, le.reconciledDate = :reconciledDate, " +
           "le.reconciledBy = :reconciledBy WHERE le.ledgerEntryId IN :entryIds")
    void markAsReconciled(
        @Param("entryIds") List<UUID> entryIds,
        @Param("reconciliationId") UUID reconciliationId,
        @Param("reconciledDate") LocalDateTime reconciledDate,
        @Param("reconciledBy") String reconciledBy);
    
    // Trial balance queries
    @Query("SELECT a.accountCode, a.accountName, " +
           "SUM(CASE WHEN le.entryType = 'DEBIT' THEN le.amount ELSE 0 END) as debits, " +
           "SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount ELSE 0 END) as credits " +
           "FROM LedgerEntryEntity le JOIN le.account a " +
           "WHERE le.transactionDate <= :asOfDate AND le.status = 'POSTED' " +
           "AND a.companyId = :companyId " +
           "GROUP BY a.accountCode, a.accountName " +
           "ORDER BY a.accountCode")
    List<Object[]> getTrialBalance(
        @Param("companyId") UUID companyId,
        @Param("asOfDate") LocalDateTime asOfDate);
    
    // Closing entries queries
    List<LedgerEntryEntity> findByIsClosingEntryTrue();
    
    List<LedgerEntryEntity> findByIsClosingEntryTrueAndAccountingPeriod(String accountingPeriod);
    
    // Reversal queries
    List<LedgerEntryEntity> findByReversalOfEntryId(UUID originalEntryId);
    
    List<LedgerEntryEntity> findByIsReversingEntryTrue();
    
    // Search and filter
    @Query("SELECT le FROM LedgerEntryEntity le WHERE " +
           "(LOWER(le.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(le.referenceNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "AND le.account.companyId = :companyId")
    Page<LedgerEntryEntity> searchEntries(
        @Param("searchTerm") String searchTerm,
        @Param("companyId") UUID companyId,
        Pageable pageable);
    
    // Unposted entries
    @Query("SELECT COUNT(le) FROM LedgerEntryEntity le " +
           "WHERE le.status != 'POSTED' AND le.transactionDate <= :asOfDate")
    long countUnpostedEntries(@Param("asOfDate") LocalDateTime asOfDate);
    
    // Running balance update
    @Modifying
    @Query("UPDATE LedgerEntryEntity le SET le.runningBalance = :runningBalance " +
           "WHERE le.ledgerEntryId = :entryId")
    void updateRunningBalance(
        @Param("entryId") UUID entryId,
        @Param("runningBalance") BigDecimal runningBalance);
    
    // Pagination queries
    Page<LedgerEntryEntity> findByAccountAccountId(UUID accountId, Pageable pageable);
    
    Page<LedgerEntryEntity> findByAccountAccountIdAndTransactionDateBetween(
        UUID accountId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    // Count queries
    long countByAccountAccountId(UUID accountId);
    
    long countByTransactionTransactionId(UUID transactionId);
    
    long countByAccountingPeriod(String accountingPeriod);
}