package com.waqiti.ledger.repository;

import com.waqiti.ledger.domain.AccountBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {

    /**
     * Find account balance by account ID and currency with pessimistic lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.accountId = :accountId AND ab.currency = :currency")
    Optional<AccountBalance> findByAccountIdAndCurrencyWithLock(
            @Param("accountId") String accountId,
            @Param("currency") String currency);

    /**
     * Find account balance by account ID and currency
     */
    Optional<AccountBalance> findByAccountIdAndCurrency(String accountId, String currency);

    /**
     * Find all balances for an account
     */
    List<AccountBalance> findByAccountIdOrderByCurrency(String accountId);

    /**
     * Find accounts with low balances
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.availableBalance <= :threshold " +
           "AND ab.isFrozen = false")
    List<AccountBalance> findAccountsWithLowBalance(@Param("threshold") BigDecimal threshold);

    /**
     * Find frozen accounts
     */
    List<AccountBalance> findByIsFrozenTrueOrderByUpdatedAt();

    /**
     * Find accounts with credit balances (money owed to account holder)
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.currentBalance > 0 " +
           "ORDER BY ab.currentBalance DESC")
    List<AccountBalance> findAccountsWithCreditBalances();

    /**
     * Find accounts with debit balances (money owed by account holder)
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.currentBalance < 0 " +
           "ORDER BY ab.currentBalance ASC")
    List<AccountBalance> findAccountsWithDebitBalances();

    /**
     * Get total balances by currency
     */
    @Query("SELECT ab.currency, SUM(ab.currentBalance), SUM(ab.availableBalance), SUM(ab.reservedBalance) " +
           "FROM AccountBalance ab GROUP BY ab.currency")
    List<Object[]> getTotalBalancesByCurrency();

    /**
     * Find accounts that haven't been updated recently
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.lastUpdated < :cutoffDate")
    List<AccountBalance> findStaleBalances(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Count accounts by currency
     */
    @Query("SELECT ab.currency, COUNT(ab) FROM AccountBalance ab GROUP BY ab.currency")
    List<Object[]> countAccountsByCurrency();

    /**
     * Find accounts with reserved balances
     */
    @Query("SELECT ab FROM AccountBalance ab WHERE ab.reservedBalance > 0 " +
           "ORDER BY ab.reservedBalance DESC")
    List<AccountBalance> findAccountsWithReservedBalances();

    /**
     * Get account balance summary for reporting
     */
    @Query("SELECT COUNT(ab), SUM(ab.currentBalance), AVG(ab.currentBalance), " +
           "MIN(ab.currentBalance), MAX(ab.currentBalance) " +
           "FROM AccountBalance ab WHERE ab.currency = :currency")
    Object[] getBalanceSummaryByCurrency(@Param("currency") String currency);

    /**
     * Check if account has sufficient available balance
     */
    @Query("SELECT CASE WHEN ab.availableBalance >= :amount THEN true ELSE false END " +
           "FROM AccountBalance ab WHERE ab.accountId = :accountId AND ab.currency = :currency")
    Boolean hasSufficientBalance(
            @Param("accountId") String accountId,
            @Param("currency") String currency,
            @Param("amount") BigDecimal amount);

    /**
     * Update last transaction ID for account balance
     */
    @Modifying
    @Query("UPDATE AccountBalance ab SET ab.lastTransactionId = :transactionId, " +
           "ab.lastUpdated = CURRENT_TIMESTAMP WHERE ab.accountId = :accountId AND ab.currency = :currency")
    void updateLastTransactionId(
            @Param("accountId") String accountId,
            @Param("currency") String currency,
            @Param("transactionId") String transactionId);

    /**
     * Find account balance by account ID (UUID version)
     */
    Optional<AccountBalance> findByAccountId(UUID accountId);
}