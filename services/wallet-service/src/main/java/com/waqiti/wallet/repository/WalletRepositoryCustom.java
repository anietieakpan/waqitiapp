package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.Wallet;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

/**
 * Custom repository interface for wallet operations requiring special locking mechanisms.
 */
public interface WalletRepositoryCustom {
    
    /**
     * Find wallet by ID with pessimistic write lock for updates.
     * This prevents concurrent modifications during critical operations.
     * 
     * @param walletId The wallet ID
     * @return Optional wallet with exclusive lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :walletId")
    Optional<Wallet> findByIdWithLock(@Param("walletId") UUID walletId);
    
    /**
     * Find wallet by ID with pessimistic read lock for read operations
     * that need consistency guarantees.
     * 
     * @param walletId The wallet ID
     * @return Optional wallet with shared lock
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT w FROM Wallet w WHERE w.id = :walletId")
    Optional<Wallet> findByIdWithReadLock(@Param("walletId") UUID walletId);
    
    /**
     * Find wallet by user ID and currency with pessimistic write lock.
     * 
     * @param userId The user ID
     * @param currency The currency code
     * @return Optional wallet with exclusive lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId AND w.currency = :currency")
    Optional<Wallet> findByUserIdAndCurrencyWithLock(@Param("userId") UUID userId, 
                                                      @Param("currency") String currency);
    
    /**
     * Update wallet balance atomically with optimistic locking check.
     * 
     * @param walletId The wallet ID
     * @param newBalance The new balance
     * @param version The expected version for optimistic locking
     * @return Number of rows updated (1 if successful, 0 if version mismatch)
     */
    @Modifying
    @Query("UPDATE Wallet w SET w.availableBalance = :newBalance, w.version = w.version + 1, " +
           "w.lastTransactionAt = CURRENT_TIMESTAMP WHERE w.id = :walletId AND w.version = :version")
    int updateBalanceWithOptimisticLock(@Param("walletId") UUID walletId, 
                                        @Param("newBalance") java.math.BigDecimal newBalance,
                                        @Param("version") Long version);
}