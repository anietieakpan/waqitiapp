package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.WalletStatus;
import com.waqiti.wallet.domain.WalletType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    /**
     * Find all wallets for a user
     */
    List<Wallet> findByUserId(UUID userId);

    /**
     * Find all active wallets for a user
     */
    List<Wallet> findByUserIdAndStatus(UUID userId, WalletStatus status);

    /**
     * Find a wallet by its external ID
     */
    Optional<Wallet> findByExternalId(String externalId);

    /**
     * Find a wallet for a user by currency
     */
    Optional<Wallet> findByUserIdAndCurrency(UUID userId, String currency);

    /**
     * Find a wallet with pessimistic lock for write operations
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(@Param("id") UUID id);
    
    /**
     * CRITICAL SECURITY: Find wallet with pessimistic lock to prevent race conditions
     * Used in atomic transfer operations
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithPessimisticLock(@Param("id") UUID id);
    
    /**
     * CRITICAL SECURITY FIX: Find wallet with pessimistic write lock for balance updates
     * Prevents race conditions in getUserWallets balance synchronization
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithPessimisticWriteLock(@Param("id") UUID id);
    
    /**
     * Find wallets with batch pessimistic lock (for multi-wallet operations)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id IN :ids ORDER BY w.id")
    List<Wallet> findByIdsWithPessimisticLock(@Param("ids") List<UUID> ids);
    
    /**
     * CRITICAL SECURITY: Atomic check-and-reserve funds operation
     * Prevents race conditions by performing balance check and reservation in single database operation
     * 
     * @param walletId The wallet to reserve funds from
     * @param amount The amount to reserve
     * @param transactionId The transaction ID for the reservation
     * @return true if funds were successfully reserved, false if insufficient balance
     */
    @Modifying
    @Query(value = """
        UPDATE wallets w SET 
            reserved_amount = COALESCE(reserved_amount, 0) + :amount,
            updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE w.id = :walletId 
        AND (w.balance - COALESCE(w.reserved_amount, 0)) >= :amount
        AND w.status = 'ACTIVE'
        """, nativeQuery = true)
    int atomicCheckAndReserveFunds(@Param("walletId") UUID walletId, 
                                  @Param("amount") BigDecimal amount, 
                                  @Param("transactionId") UUID transactionId);
    
    /**
     * Get current available balance (balance minus reserved funds)
     */
    @Query(value = """
        SELECT (w.balance - COALESCE(w.reserved_amount, 0)) as available_balance
        FROM wallets w 
        WHERE w.id = :walletId
        """, nativeQuery = true)
    BigDecimal getCurrentAvailableBalance(@Param("walletId") UUID walletId);
    
    /**
     * CRITICAL SECURITY: Atomic fund release operation
     * Used when transactions are completed or rolled back
     */
    @Modifying
    @Query(value = """
        UPDATE wallets w SET 
            reserved_amount = GREATEST(COALESCE(reserved_amount, 0) - :amount, 0),
            updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE w.id = :walletId
        """, nativeQuery = true)
    int atomicReleaseFunds(@Param("walletId") UUID walletId, 
                          @Param("amount") BigDecimal amount);
    
    /**
     * CRITICAL SECURITY: Atomic balance transfer operation
     * Transfers balance from reserved funds to actual balance atomically
     */
    @Modifying
    @Query(value = """
        UPDATE wallets w SET 
            balance = balance - :amount,
            reserved_amount = GREATEST(COALESCE(reserved_amount, 0) - :amount, 0),
            updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE w.id = :sourceWalletId 
        AND COALESCE(reserved_amount, 0) >= :amount
        """, nativeQuery = true)
    int atomicTransferFromReserved(@Param("sourceWalletId") UUID sourceWalletId, 
                                  @Param("amount") BigDecimal amount);
    
    /**
     * CRITICAL SECURITY: Atomic credit operation for destination wallet
     */
    @Modifying
    @Query(value = """
        UPDATE wallets w SET 
            balance = balance + :amount,
            updated_at = CURRENT_TIMESTAMP,
            version = version + 1
        WHERE w.id = :destinationWalletId
        AND w.status = 'ACTIVE'
        """, nativeQuery = true)
    int atomicCreditWallet(@Param("destinationWalletId") UUID destinationWalletId, 
                          @Param("amount") BigDecimal amount);
    
    /**
     * PERFORMANCE OPTIMIZATION: Find user wallets with all necessary data in single query
     * Includes joined data to prevent additional queries during wallet processing
     */
    @Query("""
        SELECT w FROM Wallet w 
        LEFT JOIN FETCH w.reservations r
        WHERE w.userId = :userId 
        AND w.status = 'ACTIVE'
        ORDER BY w.createdAt ASC
        """)
    List<Wallet> findByUserIdWithReservations(@Param("userId") UUID userId);
    
    /**
     * PERFORMANCE OPTIMIZATION: Batch balance synchronization for external system updates
     * Reduces round trips when synchronizing multiple wallet balances
     */
    @Query("""
        SELECT w FROM Wallet w 
        WHERE w.userId = :userId 
        AND w.status = 'ACTIVE'
        AND w.lastSyncedAt < :syncThreshold
        ORDER BY w.lastSyncedAt ASC
        """)
    List<Wallet> findWalletsNeedingSync(@Param("userId") UUID userId, 
                                       @Param("syncThreshold") java.time.LocalDateTime syncThreshold);
    
    /**
     * PERFORMANCE FIX: Batch enable monitoring for all user wallets
     * Prevents N+1 query pattern by updating all wallets in single query
     */
    @Modifying
    @Query("""
        UPDATE Wallet w SET 
        w.monitoringEnabled = true,
        w.monitoringUntil = :reviewDate,
        w.updatedAt = :updatedAt
        WHERE w.userId = :userId
        """)
    int batchEnableMonitoring(@Param("userId") UUID userId, 
                             @Param("reviewDate") java.time.LocalDateTime reviewDate,
                             @Param("updatedAt") java.time.LocalDateTime updatedAt);
    
    /**
     * PERFORMANCE FIX: Batch apply temporary limits for all user wallets
     * Prevents N+1 query pattern by updating all wallets in single query
     */
    @Modifying
    @Query("""
        UPDATE Wallet w SET 
        w.temporaryLimit = :maxAmount,
        w.limitExpirationDate = :expirationDate,
        w.updatedAt = :updatedAt
        WHERE w.userId = :userId
        """)
    int batchApplyTemporaryLimits(@Param("userId") UUID userId, 
                                  @Param("maxAmount") BigDecimal maxAmount,
                                  @Param("expirationDate") java.time.LocalDateTime expirationDate,
                                  @Param("updatedAt") java.time.LocalDateTime updatedAt);
    
    /**
     * PERFORMANCE FIX: Batch enable transaction monitoring for all user wallets
     * Prevents N+1 query pattern by updating all wallets in single query
     */
    @Modifying
    @Query("""
        UPDATE Wallet w SET 
        w.transactionMonitoringEnabled = true,
        w.monitoringReason = :reason,
        w.updatedAt = :updatedAt
        WHERE w.userId = :userId
        """)
    int batchEnableTransactionMonitoring(@Param("userId") UUID userId, 
                                         @Param("reason") String reason,
                                         @Param("updatedAt") java.time.LocalDateTime updatedAt);
    
    /**
     * PERFORMANCE FIX: Find user wallets by user ID and wallet type
     * Used for batch operations on specific wallet types
     */
    List<Wallet> findByUserIdAndWalletType(UUID userId, WalletType walletType);
    
    /**
     * SECURITY FIX: Perform dummy database operation for timing attack protection
     * Simulates database access timing without actual wallet data retrieval
     */
    default void performDummyOperation() {
        try {
            // Simulate database query execution time
            UUID dummyId = UUID.randomUUID();
            
            // Perform a database query that will always return empty results
            // This simulates the same database access patterns as real operations
            findByExternalId("dummy-external-" + dummyId);
            
            // Simulate additional query processing that would occur in real operations
            findByUserIdAndCurrency(dummyId, "DUMMY");
            
        } catch (Exception e) {
            // Expected to fail for non-existent data - this is for timing consistency
        }
    }
    
    // ===============================================
    // N+1 QUERY OPTIMIZATION METHODS - CRITICAL FOR PERFORMANCE
    // ===============================================
    
    /**
     * N+1 QUERY FIX: Find wallets with transactions for user - single query
     * Critical performance fix for getUserWallets - loads all related data at once
     */
    @Query("""
        SELECT DISTINCT w FROM Wallet w 
        LEFT JOIN FETCH w.transactions t
        WHERE w.userId = :userId 
        AND w.status = 'ACTIVE'
        ORDER BY w.createdAt ASC
        """)
    List<Wallet> findByUserIdWithTransactions(@Param("userId") UUID userId);
    
    /**
     * N+1 QUERY FIX: Find wallets with all related entities for comprehensive view
     * Prevents multiple round trips when building wallet responses
     */
    @Query("""
        SELECT DISTINCT w FROM Wallet w 
        LEFT JOIN FETCH w.reservations r
        LEFT JOIN FETCH w.transactions t
        WHERE w.userId = :userId 
        AND w.status = 'ACTIVE'
        ORDER BY w.createdAt ASC
        """)
    List<Wallet> findByUserIdWithAllRelations(@Param("userId") UUID userId);
    
    /**
     * N+1 QUERY FIX: Batch load wallets with pessimistic lock for atomic operations
     * Critical for transfer operations requiring exclusive access
     */
    @Query("SELECT w FROM Wallet w WHERE w.id IN :walletIds ORDER BY w.id")
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<Wallet> findByIdsWithPessimisticLock(@Param("walletIds") List<UUID> walletIds);
    
    /**
     * N+1 QUERY FIX: Get wallet summaries without lazy loading - projection query
     * Returns only essential data for wallet lists, avoiding entity graph overhead
     */
    @Query("""
        SELECT w.id, w.userId, w.currency, w.balance, w.availableBalance, 
               w.status, w.walletType, w.createdAt, w.updatedAt
        FROM Wallet w 
        WHERE w.userId IN :userIds
        ORDER BY w.userId, w.createdAt
        """)
    List<Object[]> findWalletSummariesByUserIds(@Param("userIds") List<UUID> userIds);
    
    /**
     * N+1 QUERY FIX: Get user wallet statistics in single aggregation query
     * Avoids loading full wallet entities just for statistics
     */
    @Query("""
        SELECT w.userId,
               COUNT(w) as walletCount,
               SUM(w.balance) as totalBalance,
               SUM(w.availableBalance) as totalAvailableBalance,
               COUNT(CASE WHEN w.status = 'ACTIVE' THEN 1 END) as activeWalletCount,
               COUNT(CASE WHEN w.status = 'FROZEN' THEN 1 END) as frozenWalletCount
        FROM Wallet w
        WHERE w.userId IN :userIds
        GROUP BY w.userId
        """)
    List<Object[]> getUserWalletStatistics(@Param("userIds") List<UUID> userIds);

    /**
     * RECONCILIATION: Count all active wallets for daily reconciliation job
     */
    @Query("SELECT COUNT(w) FROM Wallet w WHERE w.status = 'ACTIVE'")
    long countActiveWallets();

    /**
     * RECONCILIATION: Find active wallets in batches for reconciliation processing
     */
    @Query(value = """
        SELECT * FROM wallets w
        WHERE w.status = 'ACTIVE'
        ORDER BY w.id
        LIMIT :limit OFFSET :offset
        """, nativeQuery = true)
    List<Wallet> findActiveWalletsBatch(@Param("offset") int offset, @Param("limit") int limit);
}