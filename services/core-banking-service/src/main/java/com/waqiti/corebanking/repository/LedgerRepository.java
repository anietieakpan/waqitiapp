package com.waqiti.corebanking.repository;

import com.waqiti.corebanking.domain.Ledger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for Ledger entities
 */
@Repository
public interface LedgerRepository extends JpaRepository<Ledger, UUID> {

    /**
     * Finds ledger entries by transaction ID
     */
    List<Ledger> findByTransactionIdOrderByCreatedAt(UUID transactionId);

    /**
     * Finds ledger entries by account ID
     */
    List<Ledger> findByAccountIdOrderByTransactionDateDesc(UUID accountId);

    /**
     * Finds ledger entries by account ID with pagination
     */
    Page<Ledger> findByAccountIdOrderByTransactionDateDesc(UUID accountId, Pageable pageable);

    /**
     * Finds ledger entries by reference number
     */
    List<Ledger> findByReferenceNumber(String referenceNumber);

    /**
     * Finds ledger entries by entry type
     */
    List<Ledger> findByEntryType(Ledger.EntryType entryType);

    /**
     * Finds ledger entries by status
     */
    List<Ledger> findByStatus(Ledger.LedgerStatus status);

    /**
     * Finds ledger entries by date range
     */
    List<Ledger> findByTransactionDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Gets account balance at specific date
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN l.entryType = 'DEBIT' THEN -l.amount ELSE l.amount END), 0) " +
           "FROM Ledger l WHERE l.accountId = :accountId " +
           "AND l.valueDate <= :asOfDate AND l.status = 'POSTED'")
    BigDecimal getAccountBalanceAsOf(@Param("accountId") UUID accountId, 
                                   @Param("asOfDate") LocalDateTime asOfDate);

    /**
     * Gets current account balance
     */
    @Query("SELECT COALESCE(SUM(CASE WHEN l.entryType = 'DEBIT' THEN -l.amount ELSE l.amount END), 0) " +
           "FROM Ledger l WHERE l.accountId = :accountId AND l.status = 'POSTED'")
    BigDecimal getCurrentAccountBalance(@Param("accountId") UUID accountId);

    /**
     * Validates double-entry for transaction
     */
    @Query("SELECT " +
           "SUM(CASE WHEN l.entryType = 'DEBIT' THEN l.amount ELSE 0 END) as totalDebits, " +
           "SUM(CASE WHEN l.entryType = 'CREDIT' THEN l.amount ELSE 0 END) as totalCredits " +
           "FROM Ledger l WHERE l.transactionId = :transactionId")
    Object[] validateDoubleEntry(@Param("transactionId") UUID transactionId);

    /**
     * Gets trial balance
     */
    @Query("SELECT l.accountId, " +
           "SUM(CASE WHEN l.entryType = 'DEBIT' THEN l.amount ELSE 0 END) as totalDebits, " +
           "SUM(CASE WHEN l.entryType = 'CREDIT' THEN l.amount ELSE 0 END) as totalCredits " +
           "FROM Ledger l WHERE l.status = 'POSTED' GROUP BY l.accountId")
    List<Object[]> getTrialBalance();
}