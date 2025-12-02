package com.waqiti.transaction.repository;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Optimized transaction repository with performance improvements
 */
@Repository
public interface TransactionRepositoryOptimized extends JpaRepository<Transaction, UUID> {

    /**
     * Find user transactions with efficient pagination and index usage
     */
    @Query(value = "SELECT * FROM transactions " +
           "WHERE user_id = ?1 " +
           "AND (?2 IS NULL OR status = ?2) " +
           "ORDER BY created_at DESC " +
           "LIMIT ?3 OFFSET ?4",
           nativeQuery = true)
    List<Transaction> findUserTransactionsOptimized(UUID userId, String status, int limit, int offset);

    /**
     * Find transactions by date range with efficient filtering
     */
    @Query(value = "SELECT * FROM transactions " +
           "WHERE user_id = ?1 " +
           "AND created_at BETWEEN ?2 AND ?3 " +
           "AND (?4 IS NULL OR transaction_type = ?4) " +
           "ORDER BY created_at DESC " +
           "LIMIT ?5",
           nativeQuery = true)
    List<Transaction> findTransactionsByDateRange(UUID userId, LocalDateTime startDate, 
                                                LocalDateTime endDate, String type, int limit);

    /**
     * Get transaction statistics efficiently using aggregation with indexes
     */
    @Query(value = "SELECT " +
           "COUNT(*) as total_count, " +
           "COALESCE(SUM(amount), 0) as total_amount, " +
           "COALESCE(AVG(amount), 0) as average_amount, " +
           "COUNT(*) FILTER (WHERE status = 'COMPLETED') as completed_count, " +
           "COUNT(*) FILTER (WHERE status = 'FAILED') as failed_count " +
           "FROM transactions " +
           "WHERE user_id = ?1 " +
           "AND created_at >= ?2",
           nativeQuery = true)
    Object[] getTransactionStatsByUser(UUID userId, LocalDateTime since);

    /**
     * Find high-value transactions for fraud monitoring
     */
    @Query(value = "SELECT * FROM transactions " +
           "WHERE amount > ?1 " +
           "AND created_at >= ?2 " +
           "AND (?3 IS NULL OR status = ?3) " +
           "ORDER BY amount DESC, created_at DESC " +
           "LIMIT ?4",
           nativeQuery = true)
    List<Transaction> findHighValueTransactions(BigDecimal threshold, LocalDateTime since, 
                                              String status, int limit);

    /**
     * Batch update transaction status for processing
     */
    @Modifying
    @Query("UPDATE Transaction t SET t.status = :newStatus, t.updatedAt = :updateTime " +
           "WHERE t.id IN :ids AND t.status = :currentStatus")
    int batchUpdateStatus(@Param("ids") List<UUID> ids, 
                         @Param("currentStatus") TransactionStatus currentStatus,
                         @Param("newStatus") TransactionStatus newStatus,
                         @Param("updateTime") LocalDateTime updateTime);

    /**
     * Find pending transactions for processing with timeout
     */
    @Query(value = "SELECT * FROM transactions " +
           "WHERE status = 'PENDING' " +
           "AND created_at < ?1 " +
           "ORDER BY created_at ASC " +
           "LIMIT ?2",
           nativeQuery = true)
    List<Transaction> findTimeoutPendingTransactions(LocalDateTime timeoutThreshold, int batchSize);

    /**
     * Get daily transaction volume aggregation
     */
    @Query(value = "SELECT " +
           "DATE(created_at) as transaction_date, " +
           "COUNT(*) as transaction_count, " +
           "SUM(amount) as total_volume, " +
           "AVG(amount) as average_amount, " +
           "COUNT(*) FILTER (WHERE status = 'COMPLETED') as successful_count " +
           "FROM transactions " +
           "WHERE created_at >= ?1 " +
           "AND created_at < ?2 " +
           "GROUP BY DATE(created_at) " +
           "ORDER BY transaction_date DESC",
           nativeQuery = true)
    List<Object[]> getDailyTransactionMetrics(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find transactions by payment ID efficiently
     */
    @Query(value = "SELECT * FROM transactions " +
           "WHERE payment_id = ?1 " +
           "ORDER BY created_at DESC",
           nativeQuery = true)
    List<Transaction> findByPaymentIdOptimized(String paymentId);

    /**
     * Get transaction type distribution for analytics
     */
    @Query(value = "SELECT " +
           "transaction_type, " +
           "COUNT(*) as count, " +
           "SUM(amount) as total_amount, " +
           "AVG(amount) as average_amount " +
           "FROM transactions " +
           "WHERE user_id = ?1 " +
           "AND created_at >= ?2 " +
           "GROUP BY transaction_type " +
           "ORDER BY count DESC",
           nativeQuery = true)
    List<Object[]> getTransactionTypeDistribution(UUID userId, LocalDateTime since);

    /**
     * Batch mark transactions as reconciled
     */
    @Modifying
    @Query("UPDATE Transaction t SET t.reconciled = true, t.reconciledAt = :reconcileTime " +
           "WHERE t.id IN :ids")
    int markAsReconciled(@Param("ids") List<UUID> ids, @Param("reconcileTime") LocalDateTime reconcileTime);
}