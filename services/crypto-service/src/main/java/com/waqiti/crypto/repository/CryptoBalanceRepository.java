/**
 * Crypto Balance Repository
 * JPA repository for cryptocurrency balance operations
 */
package com.waqiti.crypto.repository;

import com.waqiti.crypto.entity.CryptoBalance;
import com.waqiti.crypto.entity.CryptoCurrency;
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
public interface CryptoBalanceRepository extends JpaRepository<CryptoBalance, UUID> {

    /**
     * Find balance by wallet ID
     */
    Optional<CryptoBalance> findByWalletId(UUID walletId);

    /**
     * Find balances by currency
     */
    List<CryptoBalance> findByCurrency(CryptoCurrency currency);

    /**
     * Find balances with available balance greater than zero
     */
    @Query("SELECT b FROM CryptoBalance b WHERE b.availableBalance > 0")
    List<CryptoBalance> findBalancesWithAvailableAmount();

    /**
     * Find balances with pending balance greater than zero
     */
    @Query("SELECT b FROM CryptoBalance b WHERE b.pendingBalance > 0")
    List<CryptoBalance> findBalancesWithPendingAmount();

    /**
     * Find balances with staked balance greater than zero
     */
    @Query("SELECT b FROM CryptoBalance b WHERE b.stakedBalance > 0")
    List<CryptoBalance> findBalancesWithStakedAmount();

    /**
     * Get total balance across all wallets for currency
     */
    @Query("SELECT COALESCE(SUM(b.totalBalance), 0) FROM CryptoBalance b WHERE b.currency = :currency")
    BigDecimal getTotalBalanceByCurrency(@Param("currency") CryptoCurrency currency);

    /**
     * Get total available balance for currency
     */
    @Query("SELECT COALESCE(SUM(b.availableBalance), 0) FROM CryptoBalance b WHERE b.currency = :currency")
    BigDecimal getTotalAvailableBalanceByCurrency(@Param("currency") CryptoCurrency currency);

    /**
     * Update available balance
     */
    @Modifying
    @Query("UPDATE CryptoBalance b SET b.availableBalance = b.availableBalance + :amount, " +
           "b.lastUpdated = CURRENT_TIMESTAMP WHERE b.walletId = :walletId")
    int updateAvailableBalance(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount);

    /**
     * Update pending balance
     */
    @Modifying
    @Query("UPDATE CryptoBalance b SET b.pendingBalance = b.pendingBalance + :amount, " +
           "b.lastUpdated = CURRENT_TIMESTAMP WHERE b.walletId = :walletId")
    int updatePendingBalance(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount);

    /**
     * Update staked balance
     */
    @Modifying
    @Query("UPDATE CryptoBalance b SET b.stakedBalance = b.stakedBalance + :amount, " +
           "b.lastUpdated = CURRENT_TIMESTAMP WHERE b.walletId = :walletId")
    int updateStakedBalance(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount);

    /**
     * Move pending to available balance
     */
    @Modifying
    @Query("UPDATE CryptoBalance b SET b.pendingBalance = b.pendingBalance - :amount, " +
           "b.availableBalance = b.availableBalance + :amount, b.lastUpdated = CURRENT_TIMESTAMP " +
           "WHERE b.walletId = :walletId AND b.pendingBalance >= :amount")
    int movePendingToAvailable(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount);

    /**
     * Move available to pending balance
     */
    @Modifying
    @Query("UPDATE CryptoBalance b SET b.availableBalance = b.availableBalance - :amount, " +
           "b.pendingBalance = b.pendingBalance + :amount, b.lastUpdated = CURRENT_TIMESTAMP " +
           "WHERE b.walletId = :walletId AND b.availableBalance >= :amount")
    int moveAvailableToPending(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount);

    /**
     * Reserve balance for transaction
     */
    @Modifying
    @Query("UPDATE CryptoBalance b SET b.availableBalance = b.availableBalance - :amount, " +
           "b.reservedBalance = b.reservedBalance + :amount, b.lastUpdated = CURRENT_TIMESTAMP " +
           "WHERE b.walletId = :walletId AND b.availableBalance >= :amount")
    int reserveBalance(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount);

    /**
     * Release reserved balance
     */
    @Modifying
    @Query("UPDATE CryptoBalance b SET b.reservedBalance = b.reservedBalance - :amount, " +
           "b.availableBalance = b.availableBalance + :amount, b.lastUpdated = CURRENT_TIMESTAMP " +
           "WHERE b.walletId = :walletId AND b.reservedBalance >= :amount")
    int releaseReservedBalance(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount);

    /**
     * Confirm reserved balance (remove from reserved, don't add back to available)
     */
    @Modifying
    @Query("UPDATE CryptoBalance b SET b.reservedBalance = b.reservedBalance - :amount, " +
           "b.lastUpdated = CURRENT_TIMESTAMP " +
           "WHERE b.walletId = :walletId AND b.reservedBalance >= :amount")
    int confirmReservedBalance(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount);

    /**
     * Get balance summary by currency
     */
    @Query("SELECT b.currency, COUNT(b), COALESCE(SUM(b.totalBalance), 0), " +
           "COALESCE(SUM(b.availableBalance), 0), COALESCE(SUM(b.pendingBalance), 0), " +
           "COALESCE(SUM(b.stakedBalance), 0) " +
           "FROM CryptoBalance b GROUP BY b.currency")
    List<Object[]> getBalanceSummaryByCurrency();

    /**
     * Find balances updated before specific time
     */
    @Query("SELECT b FROM CryptoBalance b WHERE b.lastUpdated < :cutoffTime")
    List<CryptoBalance> findBalancesNotUpdatedSince(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find balances with insufficient available amount
     */
    @Query("SELECT b FROM CryptoBalance b WHERE b.availableBalance < :minAmount AND b.currency = :currency")
    List<CryptoBalance> findBalancesWithInsufficientAmount(
        @Param("currency") CryptoCurrency currency,
        @Param("minAmount") BigDecimal minAmount
    );

    /**
     * Get aggregated balance statistics
     */
    @Query("SELECT COUNT(b), COALESCE(SUM(b.totalBalance), 0), COALESCE(AVG(b.totalBalance), 0), " +
           "COALESCE(MAX(b.totalBalance), 0) FROM CryptoBalance b WHERE b.currency = :currency")
    Object[] getBalanceStatistics(@Param("currency") CryptoCurrency currency);

    /**
     * Refresh total balance calculation for all balances
     */
    @Modifying
    @Query("UPDATE CryptoBalance b SET b.totalBalance = b.availableBalance + b.pendingBalance + b.stakedBalance, " +
           "b.lastUpdated = CURRENT_TIMESTAMP")
    int refreshAllTotalBalances();

    /**
     * Refresh total balance for specific wallet
     */
    @Modifying
    @Query("UPDATE CryptoBalance b SET b.totalBalance = b.availableBalance + b.pendingBalance + b.stakedBalance, " +
           "b.lastUpdated = CURRENT_TIMESTAMP WHERE b.walletId = :walletId")
    int refreshTotalBalance(@Param("walletId") UUID walletId);

    /**
     * Check if wallet has sufficient available balance
     */
    @Query("SELECT CASE WHEN b.availableBalance >= :amount THEN true ELSE false END " +
           "FROM CryptoBalance b WHERE b.walletId = :walletId")
    Boolean hasSufficientBalance(@Param("walletId") UUID walletId, @Param("amount") BigDecimal amount);
}