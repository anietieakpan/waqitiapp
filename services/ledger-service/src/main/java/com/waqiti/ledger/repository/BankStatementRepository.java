package com.waqiti.ledger.repository;

import com.waqiti.ledger.domain.BankStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BankStatement entities
 */
@Repository
public interface BankStatementRepository extends JpaRepository<BankStatement, UUID> {

    /**
     * Find bank statement by statement number
     */
    Optional<BankStatement> findByStatementNumber(String statementNumber);

    /**
     * Check if statement number exists
     */
    boolean existsByStatementNumber(String statementNumber);

    /**
     * Find statements by bank account
     */
    Page<BankStatement> findByBankAccountIdOrderByStatementDateDesc(UUID bankAccountId, Pageable pageable);

    /**
     * Find statements by status
     */
    Page<BankStatement> findByStatusOrderByStatementDateDesc(BankStatement.StatementStatus status, Pageable pageable);

    /**
     * Find statements by date range
     */
    Page<BankStatement> findByStatementDateBetweenOrderByStatementDateDesc(
            LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Find statements by account and date range
     */
    Page<BankStatement> findByBankAccountIdAndStatementDateBetweenOrderByStatementDateDesc(
            UUID bankAccountId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Find unreconciled statements
     */
    @Query("SELECT bs FROM BankStatement bs WHERE bs.status != 'RECONCILED' ORDER BY bs.statementDate ASC")
    Page<BankStatement> findUnreconciledStatements(Pageable pageable);

    /**
     * Find statements by file hash (to prevent duplicates)
     */
    Optional<BankStatement> findByFileHash(String fileHash);

    /**
     * Find statements by bank code
     */
    Page<BankStatement> findByBankCodeOrderByStatementDateDesc(String bankCode, Pageable pageable);

    /**
     * Find statements by account number
     */
    Page<BankStatement> findByAccountNumberOrderByStatementDateDesc(String accountNumber, Pageable pageable);

    /**
     * Find statements imported by user
     */
    Page<BankStatement> findByImportedByOrderByImportedAtDesc(String importedBy, Pageable pageable);

    /**
     * Find statements imported in date range
     */
    @Query("SELECT bs FROM BankStatement bs WHERE bs.importedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY bs.importedAt DESC")
    Page<BankStatement> findStatementsImportedBetween(@Param("startDate") LocalDateTime startDate,
                                                    @Param("endDate") LocalDateTime endDate,
                                                    Pageable pageable);

    /**
     * Find statements reconciled by user
     */
    Page<BankStatement> findByReconciledByOrderByReconciledAtDesc(String reconciledBy, Pageable pageable);

    /**
     * Find statements reconciled in date range
     */
    @Query("SELECT bs FROM BankStatement bs WHERE bs.reconciledAt BETWEEN :startDate AND :endDate " +
           "ORDER BY bs.reconciledAt DESC")
    Page<BankStatement> findStatementsReconciledBetween(@Param("startDate") LocalDateTime startDate,
                                                      @Param("endDate") LocalDateTime endDate,
                                                      Pageable pageable);

    /**
     * Find latest statement for account
     */
    @Query("SELECT bs FROM BankStatement bs WHERE bs.bankAccountId = :accountId " +
           "ORDER BY bs.statementDate DESC LIMIT 1")
    Optional<BankStatement> findLatestStatementForAccount(@Param("accountId") UUID accountId);

    /**
     * Find statements with balance variances
     */
    @Query("SELECT bs FROM BankStatement bs WHERE " +
           "bs.openingBalance + bs.totalCredits - bs.totalDebits != bs.closingBalance")
    List<BankStatement> findStatementsWithBalanceVariances();

    /**
     * Find statements overlapping with date range
     */
    @Query("SELECT bs FROM BankStatement bs WHERE " +
           "bs.bankAccountId = :accountId AND " +
           "bs.startDate <= :endDate AND bs.endDate >= :startDate " +
           "ORDER BY bs.startDate ASC")
    List<BankStatement> findOverlappingStatements(@Param("accountId") UUID accountId,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    /**
     * Get statement statistics by account
     */
    @Query("SELECT bs.bankAccountId, COUNT(bs), " +
           "SUM(CASE WHEN bs.status = 'RECONCILED' THEN 1 ELSE 0 END), " +
           "SUM(bs.totalCredits), SUM(bs.totalDebits) " +
           "FROM BankStatement bs " +
           "GROUP BY bs.bankAccountId")
    List<Object[]> getStatementStatisticsByAccount();

    /**
     * Get statement statistics by status
     */
    @Query("SELECT bs.status, COUNT(bs), SUM(bs.totalCredits), SUM(bs.totalDebits) " +
           "FROM BankStatement bs GROUP BY bs.status")
    List<Object[]> getStatementStatisticsByStatus();

    /**
     * Find statements requiring attention
     */
    @Query("SELECT bs FROM BankStatement bs WHERE " +
           "bs.status = 'PENDING' AND bs.importedAt < :cutoffDate " +
           "ORDER BY bs.importedAt ASC")
    List<BankStatement> findStatementsRequiringAttention(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find statements with high transaction counts
     */
    @Query("SELECT bs FROM BankStatement bs WHERE bs.transactionCount > :threshold " +
           "ORDER BY bs.transactionCount DESC")
    Page<BankStatement> findHighVolumeStatements(@Param("threshold") Integer threshold, Pageable pageable);

    /**
     * Find statements with large amounts
     */
    @Query("SELECT bs FROM BankStatement bs WHERE " +
           "ABS(bs.closingBalance) > :threshold OR " +
           "bs.totalCredits > :threshold OR " +
           "bs.totalDebits > :threshold " +
           "ORDER BY GREATEST(ABS(bs.closingBalance), bs.totalCredits, bs.totalDebits) DESC")
    Page<BankStatement> findLargeAmountStatements(@Param("threshold") java.math.BigDecimal threshold,
                                                Pageable pageable);

    /**
     * Find statements by currency
     */
    Page<BankStatement> findByCurrencyOrderByStatementDateDesc(String currency, Pageable pageable);

    /**
     * Complex search with multiple criteria
     */
    @Query("SELECT bs FROM BankStatement bs WHERE " +
           "(:accountId IS NULL OR bs.bankAccountId = :accountId) AND " +
           "(:status IS NULL OR bs.status = :status) AND " +
           "(:startDate IS NULL OR bs.statementDate >= :startDate) AND " +
           "(:endDate IS NULL OR bs.statementDate <= :endDate) AND " +
           "(:bankCode IS NULL OR bs.bankCode = :bankCode) AND " +
           "(:accountNumber IS NULL OR bs.accountNumber = :accountNumber) " +
           "ORDER BY bs.statementDate DESC")
    Page<BankStatement> searchStatements(
            @Param("accountId") UUID accountId,
            @Param("status") BankStatement.StatementStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("bankCode") String bankCode,
            @Param("accountNumber") String accountNumber,
            Pageable pageable);

    /**
     * Find statements for period reconciliation
     */
    @Query("SELECT bs FROM BankStatement bs WHERE " +
           "bs.bankAccountId = :accountId AND " +
           "bs.endDate <= :periodEndDate AND " +
           "bs.startDate >= :periodStartDate " +
           "ORDER BY bs.statementDate ASC")
    List<BankStatement> findStatementsForPeriodReconciliation(@Param("accountId") UUID accountId,
                                                            @Param("periodStartDate") LocalDate periodStartDate,
                                                            @Param("periodEndDate") LocalDate periodEndDate);

    /**
     * Get account balance at date
     */
    @Query("SELECT bs.closingBalance FROM BankStatement bs WHERE " +
           "bs.bankAccountId = :accountId AND " +
           "bs.statementDate = (SELECT MAX(bs2.statementDate) FROM BankStatement bs2 " +
           "WHERE bs2.bankAccountId = :accountId AND bs2.statementDate <= :asOfDate)")
    Optional<java.math.BigDecimal> getAccountBalanceAtDate(@Param("accountId") UUID accountId,
                                                         @Param("asOfDate") LocalDate asOfDate);

    /**
     * Find consecutive statements with balance continuity issues
     */
    @Query("SELECT bs1, bs2 FROM BankStatement bs1, BankStatement bs2 WHERE " +
           "bs1.bankAccountId = bs2.bankAccountId AND " +
           "bs1.statementDate < bs2.statementDate AND " +
           "bs1.closingBalance != bs2.openingBalance AND " +
           "NOT EXISTS (SELECT bs3 FROM BankStatement bs3 WHERE " +
           "bs3.bankAccountId = bs1.bankAccountId AND " +
           "bs3.statementDate > bs1.statementDate AND " +
           "bs3.statementDate < bs2.statementDate)")
    List<Object[]> findBalanceContinuityIssues();

    /**
     * Get monthly statement summary
     */
    @Query("SELECT YEAR(bs.statementDate), MONTH(bs.statementDate), " +
           "COUNT(bs), SUM(bs.totalCredits), SUM(bs.totalDebits) " +
           "FROM BankStatement bs WHERE bs.bankAccountId = :accountId " +
           "AND bs.statementDate BETWEEN :startDate AND :endDate " +
           "GROUP BY YEAR(bs.statementDate), MONTH(bs.statementDate) " +
           "ORDER BY YEAR(bs.statementDate), MONTH(bs.statementDate)")
    List<Object[]> getMonthlyStatementSummary(@Param("accountId") UUID accountId,
                                            @Param("startDate") LocalDate startDate,
                                            @Param("endDate") LocalDate endDate);
}