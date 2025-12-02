package com.waqiti.transaction.repository;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    /**
     * PRODUCTION FIX: Added Pageable to prevent loading millions of records
     */
    org.springframework.data.domain.Page<Transaction> findByBatchId(String batchId, org.springframework.data.domain.Pageable pageable);

    /**
     * PRODUCTION FIX: Added Pageable to prevent OOM errors
     */
    org.springframework.data.domain.Page<Transaction> findByStatus(TransactionStatus status, org.springframework.data.domain.Pageable pageable);
    
    /**
     * PRODUCTION FIX: Find transactions by source account with pagination
     * Prevents N+1 with EntityGraph and prevents OOM with Pageable
     */
    @EntityGraph(attributePaths = {"sourceAccount", "targetAccount"})
    org.springframework.data.domain.Page<Transaction> findBySourceAccountId(String sourceAccountId, org.springframework.data.domain.Pageable pageable);

    /**
     * PRODUCTION FIX: Find transactions by target account with pagination
     * Prevents N+1 with EntityGraph and prevents OOM with Pageable
     */
    @EntityGraph(attributePaths = {"sourceAccount", "targetAccount"})
    org.springframework.data.domain.Page<Transaction> findByTargetAccountId(String targetAccountId, org.springframework.data.domain.Pageable pageable);
    
    /**
     * Find transactions by status and date range - PAGINATED
     *
     * PRODUCTION FIX: Added Pageable parameter
     *
     * @param status Transaction status
     * @param startDate Start date
     * @param endDate End date
     * @param pageable Pagination parameters
     * @return Page of transactions
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.status = :status " +
           "AND t.createdAt BETWEEN :startDate AND :endDate " +
           "AND t.deletedAt IS NULL")
    org.springframework.data.domain.Page<Transaction> findByStatusAndDateRange(
        @Param("status") TransactionStatus status,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        org.springframework.data.domain.Pageable pageable
    );

    /**
     * Find transactions by batch ID and status - PAGINATED
     *
     * PRODUCTION FIX: Added Pageable parameter for large batches
     *
     * @param batchId Batch ID
     * @param status Transaction status
     * @param pageable Pagination parameters
     * @return Page of transactions
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.batchId = :batchId " +
           "AND t.status = :status " +
           "AND t.deletedAt IS NULL")
    org.springframework.data.domain.Page<Transaction> findByBatchIdAndStatus(
        @Param("batchId") String batchId,
        @Param("status") TransactionStatus status,
        org.springframework.data.domain.Pageable pageable
    );
    
    Optional<Transaction> findByReference(String reference);
    
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.batchId = :batchId AND t.status = 'COMPLETED'")
    long countCompletedByBatchId(@Param("batchId") String batchId);
    
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.batchId = :batchId AND t.status = 'FAILED'")
    long countFailedByBatchId(@Param("batchId") String batchId);
    
    @Modifying
    @Transactional
    @Query("UPDATE Transaction t SET t.status = :newStatus WHERE t.customerId = :customerId AND t.status = :currentStatus")
    int updateTransactionStatusByCustomer(
        @Param("customerId") String customerId, 
        @Param("currentStatus") TransactionStatus currentStatus, 
        @Param("newStatus") TransactionStatus newStatus
    );
    
    @Modifying
    @Transactional
    @Query("UPDATE Transaction t SET t.status = :newStatus WHERE t.merchantId = :merchantId AND t.status = :currentStatus")
    int updateTransactionStatusByMerchant(
        @Param("merchantId") String merchantId, 
        @Param("currentStatus") TransactionStatus currentStatus, 
        @Param("newStatus") TransactionStatus newStatus
    );
    
    /**
     * Find pending transactions by customer - LIMITED
     *
     * PRODUCTION FIX: Added LIMIT for performance
     * Use this for quick dashboard displays (shows most recent 50)
     * For full list, use findByCustomerIdAndStatus with Pageable
     *
     * @param customerId Customer ID
     * @return Up to 50 most recent pending transactions
     */
    @Query(value = "SELECT * FROM transactions " +
           "WHERE customer_id = :customerId " +
           "AND status = 'PENDING' " +
           "AND deleted_at IS NULL " +
           "ORDER BY created_at DESC " +
           "LIMIT 50",
           nativeQuery = true)
    List<Transaction> findPendingTransactionsByCustomer(@Param("customerId") String customerId);

    /**
     * Find pending transactions by merchant - LIMITED
     *
     * PRODUCTION FIX: Added LIMIT for performance
     * Use for quick dashboard displays (shows most recent 50)
     * For full list, use findByMerchantIdAndStatus with Pageable
     *
     * @param merchantId Merchant ID
     * @return Up to 50 most recent pending transactions
     */
    @Query(value = "SELECT * FROM transactions " +
           "WHERE merchant_id = :merchantId " +
           "AND status = 'PENDING' " +
           "AND deleted_at IS NULL " +
           "ORDER BY created_at DESC " +
           "LIMIT 50",
           nativeQuery = true)
    List<Transaction> findPendingTransactionsByMerchant(@Param("merchantId") String merchantId);
    
    /**
     * PRODUCTION FIX: Additional methods with pagination to prevent OOM
     */
    org.springframework.data.domain.Page<Transaction> findByFromWalletId(String fromWalletId, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Transaction> findByToWalletId(String toWalletId, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Transaction> findByFromUserIdAndStatus(String fromUserId, TransactionStatus status, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Transaction> findByToUserIdAndStatus(String toUserId, TransactionStatus status, org.springframework.data.domain.Pageable pageable);
    
    /**
     * Find transactions by user with optimized joins - PAGINATED
     *
     * PRODUCTION FIX: Added Pageable parameter to prevent loading millions of records
     * For users with years of transaction history, this prevents OOM errors
     *
     * @param userId The user ID
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of transactions
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.sourceAccount " +
           "LEFT JOIN FETCH t.targetAccount " +
           "WHERE (t.fromUserId = :userId OR t.toUserId = :userId) " +
           "AND t.deletedAt IS NULL")
    org.springframework.data.domain.Page<Transaction> findByUserId(@Param("userId") String userId, org.springframework.data.domain.Pageable pageable);

    /**
     * Find transactions by user and status with optimized joins - PAGINATED
     *
     * PRODUCTION FIX: Added Pageable parameter
     *
     * @param userId The user ID
     * @param status Transaction status
     * @param pageable Pagination parameters
     * @return Page of transactions
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.sourceAccount " +
           "LEFT JOIN FETCH t.targetAccount " +
           "WHERE (t.fromUserId = :userId OR t.toUserId = :userId) " +
           "AND t.status = :status " +
           "AND t.deletedAt IS NULL")
    org.springframework.data.domain.Page<Transaction> findByUserIdAndStatus(@Param("userId") String userId, @Param("status") TransactionStatus status, org.springframework.data.domain.Pageable pageable);
    
    /**
     * Find transactions by date range - PAGINATED
     *
     * PRODUCTION FIX: Added Pageable to handle large date ranges
     *
     * @param startDate Start of date range
     * @param endDate End of date range
     * @param pageable Pagination parameters
     * @return Page of transactions
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.createdAt BETWEEN :startDate AND :endDate " +
           "AND t.deletedAt IS NULL " +
           "ORDER BY t.createdAt DESC")
    org.springframework.data.domain.Page<Transaction> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        org.springframework.data.domain.Pageable pageable);

    /**
     * Find transactions eligible for retry - LIMITED
     *
     * PRODUCTION FIX: Added LIMIT to prevent loading too many failed transactions at once
     * Retry mechanism should process in batches, not all at once
     *
     * @return Limited list of transactions eligible for retry (max 100)
     */
    @Query(value = "SELECT * FROM transactions " +
           "WHERE retry_count > 0 " +
           "AND status IN ('FAILED', 'PROCESSING_ERROR') " +
           "AND deleted_at IS NULL " +
           "ORDER BY next_retry_at ASC NULLS LAST " +
           "LIMIT 100",
           nativeQuery = true)
    List<Transaction> findTransactionsEligibleForRetry();

    /**
     * Find stale processing transactions - LIMITED
     *
     * PRODUCTION FIX: Added LIMIT to prevent OOM when many transactions are stuck
     * Monitoring/alerting should process in batches
     *
     * @param cutoffTime Transactions older than this are considered stale
     * @return Limited list of stale transactions (max 1000 for monitoring)
     */
    @Query(value = "SELECT * FROM transactions " +
           "WHERE status = 'PROCESSING' " +
           "AND created_at < :cutoffTime " +
           "AND deleted_at IS NULL " +
           "ORDER BY created_at ASC " +
           "LIMIT 1000",
           nativeQuery = true)
    List<Transaction> findStaleProcessingTransactions(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.batchId = :batchId")
    long countByBatchId(@Param("batchId") String batchId);
    
    @Query("SELECT t FROM Transaction t WHERE t.externalReference = :externalReference")
    Optional<Transaction> findByExternalReference(@Param("externalReference") String externalReference);
    
    @Query("SELECT t FROM Transaction t WHERE t.processorReference = :processorReference")
    Optional<Transaction> findByProcessorReference(@Param("processorReference") String processorReference);
    
    /**
     * Find transactions by wallet and date range - PAGINATED
     *
     * PRODUCTION FIX: Added Pageable parameter for large date ranges
     *
     * @param walletId Wallet ID
     * @param startDate Start date
     * @param endDate End date
     * @param pageable Pagination parameters
     * @return Page of transactions
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE (t.fromWalletId = :walletId OR t.toWalletId = :walletId) " +
           "AND t.createdAt BETWEEN :startDate AND :endDate " +
           "AND t.deletedAt IS NULL " +
           "ORDER BY t.createdAt DESC")
    org.springframework.data.domain.Page<Transaction> findByWalletIdAndDateRange(
        @Param("walletId") String walletId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        org.springframework.data.domain.Pageable pageable);
    
    // Suspension-related methods
    @Modifying
    @Transactional
    @Query("UPDATE Transaction t SET t.suspensionReason = :reason, t.suspendedAt = :suspendedAt, t.emergencySuspension = :emergency WHERE t.id = :transactionId")
    int suspendTransaction(
        @Param("transactionId") UUID transactionId,
        @Param("reason") String reason,
        @Param("suspendedAt") LocalDateTime suspendedAt,
        @Param("emergency") Boolean emergency
    );
    
    @Query("SELECT t FROM Transaction t WHERE t.suspendedAt IS NOT NULL")
    List<Transaction> findSuspendedTransactions();
    
    @Query("SELECT t FROM Transaction t WHERE t.emergencySuspension = true")
    List<Transaction> findEmergencySuspendedTransactions();
    
    // ===============================================
    // N+1 QUERY OPTIMIZATION METHODS
    // ===============================================
    
    /**
     * Find transactions by user ID with pagination - optimized
     * Uses EntityGraph hint to avoid N+1 queries for related entities
     */
    @EntityGraph(attributePaths = {"sourceAccount", "targetAccount", "items"})
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "WHERE t.fromUserId = :userId OR t.toUserId = :userId " +
           "ORDER BY t.createdAt DESC")
    org.springframework.data.domain.Page<Transaction> findByUserId(
        @Param("userId") UUID userId, 
        org.springframework.data.domain.Pageable pageable
    );
    
    /**
     * Find transactions by merchant ID with customer details - optimized with JOIN FETCH
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.sourceAccount sa " +
           "LEFT JOIN FETCH t.targetAccount ta " +
           "LEFT JOIN FETCH t.customer c " +
           "WHERE t.merchantId = :merchantId " +
           "ORDER BY t.createdAt DESC")
    org.springframework.data.domain.Page<Transaction> findByMerchantIdWithCustomerDetails(
        @Param("merchantId") UUID merchantId,
        org.springframework.data.domain.Pageable pageable
    );
    
    /**
     * Find transactions by user ID with merchant details - optimized with JOIN FETCH
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.sourceAccount sa " +
           "LEFT JOIN FETCH t.targetAccount ta " +
           "LEFT JOIN FETCH t.merchant m " +
           "WHERE t.fromUserId = :userId OR t.toUserId = :userId " +
           "ORDER BY t.createdAt DESC")
    org.springframework.data.domain.Page<Transaction> findByUserIdWithMerchantDetails(
        @Param("userId") UUID userId,
        org.springframework.data.domain.Pageable pageable
    );
    
    /**
     * Find transactions by status with all related entities - optimized with multiple JOIN FETCH
     * NOTE: This uses multiple LEFT JOIN FETCH to eagerly load all related entities in a single query
     */
    @Query(value = "SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.sourceAccount sa " +
           "LEFT JOIN FETCH t.targetAccount ta " +
           "LEFT JOIN FETCH t.customer c " +
           "LEFT JOIN FETCH t.merchant m " +
           "WHERE t.status = :status " +
           "ORDER BY t.createdAt DESC")
    List<Transaction> findByStatusWithAllRelatedEntities(
        @Param("status") String status,
        @Param("limit") int limit
    );
    
    /**
     * Bulk update transaction statuses - single query update
     * Optimized to update multiple transactions in one database roundtrip
     */
    @Modifying
    @Transactional
    @Query("UPDATE Transaction t SET t.status = :newStatus, t.statusReason = :reason, " +
           "t.updatedAt = CURRENT_TIMESTAMP WHERE t.id IN :transactionIds")
    int bulkUpdateStatus(
        @Param("transactionIds") List<UUID> transactionIds,
        @Param("newStatus") String newStatus,
        @Param("reason") String reason
    );
    
    /**
     * Get transaction summaries for multiple users - optimized aggregation query
     * Returns summary statistics without loading full entities
     */
    @Query("SELECT t.fromUserId AS userId, " +
           "COUNT(t) AS totalTransactions, " +
           "SUM(t.amount) AS totalAmount, " +
           "AVG(t.amount) AS averageAmount, " +
           "SUM(CASE WHEN t.status = 'COMPLETED' THEN 1 ELSE 0 END) AS successfulTransactions, " +
           "SUM(CASE WHEN t.status = 'FAILED' THEN 1 ELSE 0 END) AS failedTransactions " +
           "FROM Transaction t " +
           "WHERE t.fromUserId IN :userIds " +
           "AND t.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY t.fromUserId")
    List<com.waqiti.transaction.service.OptimizedTransactionService.TransactionSummaryProjection> 
        getTransactionSummariesByUserIds(
            @Param("userIds") List<UUID> userIds,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
        );
    
    /**
     * Get daily transaction counts for multiple merchants - optimized aggregation
     * Groups transactions by merchant and date without loading full entities
     */
    @Query("SELECT t.merchantId AS merchantId, " +
           "CAST(t.createdAt AS LocalDate) AS date, " +
           "COUNT(t) AS count " +
           "FROM Transaction t " +
           "WHERE t.merchantId IN :merchantIds " +
           "AND CAST(t.createdAt AS LocalDate) BETWEEN :startDate AND :endDate " +
           "GROUP BY t.merchantId, CAST(t.createdAt AS LocalDate)")
    List<com.waqiti.transaction.service.OptimizedTransactionService.DailyTransactionCount> 
        getDailyTransactionCounts(
            @Param("merchantIds") List<UUID> merchantIds,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
        );

    // ===============================================
    // P0-016: PESSIMISTIC LOCKING FOR FINANCIAL OPERATIONS
    // ===============================================

    /**
     * CRITICAL FIX: Find transaction by ID with pessimistic write lock
     *
     * Prevents race conditions in concurrent transaction updates.
     * MUST be used when updating transaction balance or status.
     *
     * Use Case: Balance updates, status changes, fund transfers
     *
     * Example:
     * <pre>
     * Transaction tx = repo.findByIdForUpdate(id).orElseThrow();
     * tx.setAmount(newAmount);  // Safe - no other thread can modify
     * repo.save(tx);
     * </pre>
     *
     * @param id Transaction ID
     * @return Transaction with PESSIMISTIC_WRITE lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") UUID id);

    /**
     * CRITICAL FIX: Find transaction by reference with pessimistic write lock
     *
     * Prevents duplicate processing of external references.
     *
     * @param reference External reference
     * @return Transaction with PESSIMISTIC_WRITE lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.reference = :reference")
    Optional<Transaction> findByReferenceForUpdate(@Param("reference") String reference);

    /**
     * CRITICAL FIX: Find pending transactions for wallet with pessimistic lock
     *
     * Used for balance calculations to prevent race conditions.
     *
     * @param walletId Wallet ID
     * @return List of pending transactions with locks
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE " +
           "(t.fromWalletId = :walletId OR t.toWalletId = :walletId) " +
           "AND t.status IN ('PENDING', 'PROCESSING') " +
           "ORDER BY t.createdAt ASC")
    List<Transaction> findPendingTransactionsForWalletWithLock(@Param("walletId") String walletId);

    /**
     * CRITICAL FIX: Find transaction by external reference with pessimistic lock
     *
     * Prevents duplicate processing of external payment gateway transactions.
     *
     * @param externalReference External payment reference
     * @return Transaction with PESSIMISTIC_WRITE lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.externalReference = :externalReference")
    Optional<Transaction> findByExternalReferenceForUpdate(@Param("externalReference") String externalReference);

    /**
     * CRITICAL FIX: Find stale processing transactions with pessimistic lock
     *
     * Used for timeout handling and cleanup jobs.
     * Prevents multiple cleanup processes from handling the same transaction.
     *
     * @param cutoffTime Cutoff time for stale transactions
     * @return List of stale transactions with locks
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE " +
           "t.status = 'PROCESSING' " +
           "AND t.createdAt < :cutoffTime " +
           "ORDER BY t.createdAt ASC")
    List<Transaction> findStaleProcessingTransactionsWithLock(@Param("cutoffTime") LocalDateTime cutoffTime);
}