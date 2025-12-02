package com.waqiti.account.repository;

import com.waqiti.account.domain.Transaction;
import com.waqiti.account.domain.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transaction Repository - PRODUCTION READY
 *
 * Provides optimized queries for transaction management
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Check if account has any pending transactions
     */
    boolean existsByAccountIdAndStatus(UUID accountId, TransactionStatus status);

    /**
     * Find all transactions for account with specific status
     */
    List<Transaction> findByAccountIdAndStatus(UUID accountId, TransactionStatus status);

    /**
     * Calculate total pending transaction amount for account
     * Optimized query that sums without loading entities
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.accountId = :accountId " +
           "AND t.status = 'PENDING'")
    BigDecimal calculatePendingAmount(@Param("accountId") UUID accountId);

    /**
     * Count transactions in date range
     */
    long countByAccountIdAndCreatedAtBetween(UUID accountId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find all transactions for account in date range
     */
    List<Transaction> findByAccountIdAndCreatedAtBetween(UUID accountId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find recent transactions for account (limited)
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE t.accountId = :accountId " +
           "ORDER BY t.createdAt DESC")
    List<Transaction> findRecentByAccountId(@Param("accountId") UUID accountId, org.springframework.data.domain.Pageable pageable);

    /**
     * Calculate total transaction volume for account in date range
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.accountId = :accountId " +
           "AND t.createdAt BETWEEN :startDate AND :endDate " +
           "AND t.status = 'COMPLETED'")
    BigDecimal calculateTotalVolumeInRange(
            @Param("accountId") UUID accountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
