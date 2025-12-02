package com.waqiti.transaction.repository;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Optimized repository for Transaction with N+1 query prevention
 */
@Repository
public interface OptimizedTransactionRepository extends JpaRepository<Transaction, UUID> {
    
    /**
     * Find transaction with events eagerly loaded
     */
    @EntityGraph(attributePaths = {"events"})
    Optional<Transaction> findWithEventsById(UUID id);
    
    /**
     * Find transactions by user with optimized query
     */
    @Query("SELECT t FROM Transaction t " +
           "WHERE (t.senderId = :userId OR t.receiverId = :userId) " +
           "ORDER BY t.createdAt DESC")
    Page<Transaction> findByUserIdOptimized(@Param("userId") UUID userId, Pageable pageable);
    
    /**
     * Find transactions with events for a date range
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
           "LEFT JOIN FETCH t.events " +
           "WHERE t.userId = :userId " +
           "AND t.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY t.createdAt DESC")
    List<Transaction> findByUserIdAndDateRangeWithEvents(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Get transaction statistics without loading full entities
     */
    @Query("SELECT new com.waqiti.transaction.dto.TransactionStatistics(" +
           "t.type, t.status, COUNT(t), SUM(t.amount), AVG(t.amount), MIN(t.amount), MAX(t.amount)) " +
           "FROM Transaction t " +
           "WHERE t.userId = :userId " +
           "AND t.createdAt BETWEEN :startDate AND :endDate " +
           "GROUP BY t.type, t.status")
    List<Object> getTransactionStatistics(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    /**
     * Find pending transactions with limited fields
     */
    @Query("SELECT new com.waqiti.transaction.dto.PendingTransactionDto(" +
           "t.id, t.type, t.amount, t.currency, t.description, t.createdAt) " +
           "FROM Transaction t " +
           "WHERE t.userId = :userId " +
           "AND t.status = 'PENDING' " +
           "ORDER BY t.createdAt DESC")
    List<Object> findPendingTransactionSummaries(@Param("userId") UUID userId);
    
    /**
     * Batch update transaction status (avoiding N+1 on updates)
     */
    @Query("UPDATE Transaction t SET t.status = :newStatus " +
           "WHERE t.id IN :ids AND t.status = :currentStatus")
    int batchUpdateStatus(
        @Param("ids") List<UUID> ids,
        @Param("currentStatus") TransactionStatus currentStatus,
        @Param("newStatus") TransactionStatus newStatus
    );
    
    /**
     * Find transactions by reference with events
     */
    @EntityGraph(attributePaths = {"events"})
    Optional<Transaction> findByReferenceNumber(String referenceNumber);
    
    /**
     * Get daily transaction summary
     */
    @Query("SELECT DATE(t.createdAt) as date, " +
           "COUNT(t) as count, " +
           "SUM(t.amount) as total, " +
           "SUM(CASE WHEN t.type = 'PAYMENT' THEN t.amount ELSE 0 END) as payments, " +
           "SUM(CASE WHEN t.type = 'TRANSFER' THEN t.amount ELSE 0 END) as transfers " +
           "FROM Transaction t " +
           "WHERE t.userId = :userId " +
           "AND t.createdAt >= :startDate " +
           "GROUP BY DATE(t.createdAt) " +
           "ORDER BY DATE(t.createdAt) DESC")
    List<Object[]> getDailyTransactionSummary(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate
    );
    
    /**
     * Find recent transactions with user details (projection)
     */
    @Query("SELECT new com.waqiti.transaction.dto.RecentTransactionDto(" +
           "t.id, t.type, t.amount, t.currency, t.status, " +
           "t.senderId, t.receiverId, t.description, t.createdAt, " +
           "CASE WHEN t.senderId = :userId THEN r.fullName ELSE s.fullName END) " +
           "FROM Transaction t " +
           "LEFT JOIN User s ON t.senderId = s.id " +
           "LEFT JOIN User r ON t.receiverId = r.id " +
           "WHERE t.senderId = :userId OR t.receiverId = :userId " +
           "ORDER BY t.createdAt DESC")
    Page<Object> findRecentTransactionsWithUserDetails(@Param("userId") UUID userId, Pageable pageable);
    
    /**
     * Count transactions by type for dashboard
     */
    @Query("SELECT t.type, COUNT(t), SUM(t.amount) " +
           "FROM Transaction t " +
           "WHERE t.userId = :userId " +
           "AND t.createdAt >= :since " +
           "AND t.status = 'COMPLETED' " +
           "GROUP BY t.type")
    List<Object[]> countTransactionsByType(
        @Param("userId") UUID userId,
        @Param("since") LocalDateTime since
    );
}