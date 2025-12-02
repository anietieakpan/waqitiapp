package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.Transaction;
import com.waqiti.wallet.domain.TransactionStatus;
import com.waqiti.wallet.domain.TransactionType;
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
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Find a transaction by its external ID
     */
    Optional<Transaction> findByExternalId(String externalId);

    /**
     * Find transactions for a user (either as source or target)
     */
    @Query("SELECT t FROM Transaction t WHERE t.sourceWalletId IN " +
            "(SELECT w.id FROM Wallet w WHERE w.userId = :userId) OR " +
            "t.targetWalletId IN (SELECT w.id FROM Wallet w WHERE w.userId = :userId)")
    Page<Transaction> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Find transactions for a specific wallet
     */
    @Query("SELECT t FROM Transaction t WHERE t.sourceWalletId = :walletId OR t.targetWalletId = :walletId")
    Page<Transaction> findByWalletId(@Param("walletId") UUID walletId, Pageable pageable);

    /**
     * Find transactions by status
     */
    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    /**
     * Find transactions by type
     */
    Page<Transaction> findByType(TransactionType type, Pageable pageable);

    /**
     * Find transactions by date range
     */
    Page<Transaction> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Find pending transactions older than a specific time
     */
    List<Transaction> findByStatusAndCreatedAtBefore(TransactionStatus status, LocalDateTime beforeTime);
    
    /**
     * Get total debit amount for a wallet within a date range
     */
    @Query("SELECT COALESCE(SUM(ABS(t.amount)), 0) FROM Transaction t " +
           "WHERE t.walletId = :walletId AND t.type IN ('DEBIT', 'WITHDRAWAL', 'TRANSFER_OUT') " +
           "AND t.status = 'COMPLETED' AND t.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalDebitAmountByWalletAndDateRange(
        @Param("walletId") UUID walletId, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Get total credit amount for a wallet within a date range
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.walletId = :walletId AND t.type IN ('CREDIT', 'DEPOSIT', 'TRANSFER_IN') " +
           "AND t.status = 'COMPLETED' AND t.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalCreditAmountByWalletAndDateRange(
        @Param("walletId") UUID walletId, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Count transactions for a wallet within a date range
     */
    @Query("SELECT COUNT(t) FROM Transaction t " +
           "WHERE t.walletId = :walletId AND t.status = 'COMPLETED' " +
           "AND t.createdAt BETWEEN :startDate AND :endDate")
    Long countCompletedTransactionsByWalletAndDateRange(
        @Param("walletId") UUID walletId, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Find all pending transactions for a user
     */
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.status IN ('PENDING', 'PROCESSING', 'SCHEDULED')")
    List<Transaction> findPendingTransactionsByUserId(@Param("userId") UUID userId);
    
    /**
     * PERFORMANCE FIX: Batch block pending transactions for a user
     * Prevents N+1 query pattern by updating all pending transactions in single query
     */
    @Modifying
    @Query("""
        UPDATE Transaction t SET
        t.status = :newStatus,
        t.description = CONCAT(COALESCE(t.description, ''), ' [BLOCKED: ', :blockReason, ']'),
        t.updatedAt = :updatedAt
        WHERE t.userId = :userId
        AND t.status = :oldStatus
        """)
    int batchBlockPendingTransactions(@Param("userId") UUID userId,
                                      @Param("oldStatus") TransactionStatus oldStatus,
                                      @Param("newStatus") TransactionStatus newStatus,
                                      @Param("blockReason") String blockReason,
                                      @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Find transactions by wallet ID and status list
     */
    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.status IN :statuses")
    List<Transaction> findByWalletIdAndStatusIn(@Param("walletId") UUID walletId,
                                                 @Param("statuses") List<TransactionStatus> statuses);

    /**
     * Find transactions by wallet ID and status list with pagination
     */
    @Query("SELECT t FROM Transaction t WHERE t.walletId = :walletId AND t.status IN :statuses")
    Page<Transaction> findByWalletIdAndStatusIn(@Param("walletId") UUID walletId,
                                                 @Param("statuses") List<TransactionStatus> statuses,
                                                 Pageable pageable);

    /**
     * Count transactions by wallet ID and status list
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.walletId = :walletId AND t.status IN :statuses")
    long countByWalletIdAndStatusIn(@Param("walletId") UUID walletId,
                                     @Param("statuses") List<TransactionStatus> statuses);
}