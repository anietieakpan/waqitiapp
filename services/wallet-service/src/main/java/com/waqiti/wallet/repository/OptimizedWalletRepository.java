package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Optimized wallet repository with batch operations and efficient queries
 */
@Repository
public interface OptimizedWalletRepository extends JpaRepository<Wallet, UUID> {
    
    /**
     * Batch update wallet balances - eliminates N+1 queries
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE wallets w SET " +
           "balance = v.new_balance, " +
           "updated_at = CURRENT_TIMESTAMP, " +
           "version = version + 1 " +
           "FROM (VALUES :updates) AS v(wallet_id, new_balance) " +
           "WHERE w.id = v.wallet_id::uuid", nativeQuery = true)
    void batchUpdateBalances(@Param("updates") List<Object[]> updates);
    
    /**
     * Fetch wallets with user information using JOIN instead of N+1
     */
    @Query("SELECT w FROM Wallet w " +
           "JOIN FETCH w.user u " +
           "WHERE w.userId = :userId " +
           "AND w.status = 'ACTIVE'")
    List<Wallet> findActiveWalletsByUserIdWithUser(@Param("userId") UUID userId);
    
    /**
     * Optimistic locking with retry for balance updates
     */
    @Modifying
    @Query("UPDATE Wallet w SET " +
           "w.balance = w.balance + :amount, " +
           "w.updatedAt = :timestamp, " +
           "w.version = w.version + 1 " +
           "WHERE w.id = :walletId " +
           "AND w.version = :expectedVersion " +
           "AND w.status = 'ACTIVE'")
    int updateBalanceOptimistic(@Param("walletId") UUID walletId,
                               @Param("amount") BigDecimal amount,
                               @Param("timestamp") LocalDateTime timestamp,
                               @Param("expectedVersion") Long expectedVersion);
    
    /**
     * Stream large result sets to avoid memory issues
     */
    @Query("SELECT w FROM Wallet w WHERE w.updatedAt < :cutoffDate")
    Stream<Wallet> streamStaleWallets(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Bulk fetch wallets with their transaction counts
     */
    @Query("SELECT w.id as walletId, w.balance as balance, " +
           "COUNT(t.id) as transactionCount " +
           "FROM Wallet w " +
           "LEFT JOIN Transaction t ON (t.sourceWalletId = w.id OR t.targetWalletId = w.id) " +
           "WHERE w.userId IN :userIds " +
           "GROUP BY w.id, w.balance")
    List<WalletSummaryProjection> getWalletSummariesForUsers(@Param("userIds") Set<UUID> userIds);
    
    /**
     * Efficient balance check without loading full entity
     */
    @Query("SELECT w.balance FROM Wallet w WHERE w.id = :walletId")
    Optional<BigDecimal> getBalanceById(@Param("walletId") UUID walletId);
    
    /**
     * Batch create wallets with single query
     */
    @Modifying
    @Query(value = "INSERT INTO wallets (id, wallet_id, user_id, currency, balance, status, " +
           "wallet_type, created_at, updated_at, version) " +
           "VALUES :values", nativeQuery = true)
    void batchInsertWallets(@Param("values") List<Object[]> walletData);
    
    /**
     * Update multiple wallet statuses in single query
     */
    @Modifying
    @Query("UPDATE Wallet w SET w.status = :newStatus, w.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE w.id IN :walletIds")
    void batchUpdateStatus(@Param("walletIds") List<UUID> walletIds, 
                          @Param("newStatus") String newStatus);
    
    /**
     * Projection interface for efficient data fetching
     */
    interface WalletSummaryProjection {
        UUID getWalletId();
        BigDecimal getBalance();
        Long getTransactionCount();
    }
    
    /**
     * Find wallets that need balance reconciliation
     */
    @Query(value = "SELECT w.* FROM wallets w " +
           "WHERE w.last_reconciled_at < :cutoffTime " +
           "OR w.last_reconciled_at IS NULL " +
           "ORDER BY w.last_reconciled_at ASC NULLS FIRST " +
           "LIMIT :batchSize", nativeQuery = true)
    List<Wallet> findWalletsNeedingReconciliation(@Param("cutoffTime") LocalDateTime cutoffTime,
                                                  @Param("batchSize") int batchSize);
}