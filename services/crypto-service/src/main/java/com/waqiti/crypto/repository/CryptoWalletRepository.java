/**
 * Crypto Wallet Repository
 * JPA repository for cryptocurrency wallet operations
 */
package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.CryptoWallet;
import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.entity.WalletStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CryptoWalletRepository extends JpaRepository<CryptoWallet, UUID> {

    /**
     * Find wallet by user ID and currency
     */
    Optional<CryptoWallet> findByUserIdAndCurrency(UUID userId, CryptoCurrency currency);

    /**
     * Find all wallets for a user
     */
    List<CryptoWallet> findByUserId(UUID userId);

    /**
     * Find all active wallets for a user
     */
    List<CryptoWallet> findByUserIdAndStatus(UUID userId, WalletStatus status);

    /**
     * Find wallet by ID and user ID (security check)
     */
    Optional<CryptoWallet> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Check if wallet exists for user and currency
     */
    boolean existsByUserIdAndCurrency(UUID userId, CryptoCurrency currency);

    /**
     * Find wallet by multi-sig address
     */
    Optional<CryptoWallet> findByMultiSigAddress(String multiSigAddress);

    /**
     * Find all wallets by currency
     */
    List<CryptoWallet> findByCurrency(CryptoCurrency currency);

    /**
     * Find all wallets with specific status
     */
    List<CryptoWallet> findByStatus(WalletStatus status);

    /**
     * Find wallets created after specific date
     */
    List<CryptoWallet> findByCreatedAtAfter(LocalDateTime createdAt);

    /**
     * Count total wallets for user
     */
    @Query("SELECT COUNT(w) FROM CryptoWallet w WHERE w.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);

    /**
     * Count active wallets for user
     */
    @Query("SELECT COUNT(w) FROM CryptoWallet w WHERE w.userId = :userId AND w.status = :status")
    long countByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") WalletStatus status);

    /**
     * Find wallets that need KMS key rotation
     */
    @Query("SELECT w FROM CryptoWallet w WHERE w.updatedAt < :rotationDate")
    List<CryptoWallet> findWalletsRequiringKeyRotation(@Param("rotationDate") LocalDateTime rotationDate);

    /**
     * Find wallets by multiple currencies
     */
    @Query("SELECT w FROM CryptoWallet w WHERE w.userId = :userId AND w.currency IN :currencies")
    List<CryptoWallet> findByUserIdAndCurrencyIn(@Param("userId") UUID userId, @Param("currencies") List<CryptoCurrency> currencies);

    /**
     * Get wallet statistics for user
     */
    @Query("SELECT w.currency, COUNT(w), SUM(CASE WHEN w.status = 'ACTIVE' THEN 1 ELSE 0 END) " +
           "FROM CryptoWallet w WHERE w.userId = :userId GROUP BY w.currency")
    List<Object[]> getWalletStatisticsByUserId(@Param("userId") UUID userId);

    /**
     * Find inactive wallets older than specified date
     */
    @Query("SELECT w FROM CryptoWallet w WHERE w.status = 'INACTIVE' AND w.updatedAt < :cutoffDate")
    List<CryptoWallet> findInactiveWalletsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Update wallet status
     */
    @Modifying
    @Query("UPDATE CryptoWallet w SET w.status = :status, w.updatedAt = CURRENT_TIMESTAMP WHERE w.id = :walletId")
    int updateWalletStatus(@Param("walletId") UUID walletId, @Param("status") WalletStatus status);

    /**
     * Update multiple wallet statuses for user
     */
    @Modifying
    @Query("UPDATE CryptoWallet w SET w.status = :newStatus, w.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE w.userId = :userId AND w.status = :currentStatus")
    int updateWalletStatusForUser(@Param("userId") UUID userId, 
                                  @Param("currentStatus") WalletStatus currentStatus,
                                  @Param("newStatus") WalletStatus newStatus);
}