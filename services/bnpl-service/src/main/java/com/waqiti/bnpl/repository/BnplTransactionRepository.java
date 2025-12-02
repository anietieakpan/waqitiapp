package com.waqiti.bnpl.repository;

import com.waqiti.bnpl.domain.BnplTransaction;
import com.waqiti.bnpl.domain.enums.TransactionStatus;
import com.waqiti.bnpl.domain.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for BNPL transactions
 */
@Repository
public interface BnplTransactionRepository extends JpaRepository<BnplTransaction, Long> {

    /**
     * Find transaction by transaction ID
     */
    Optional<BnplTransaction> findByTransactionId(String transactionId);

    /**
     * Find transactions by plan ID
     */
    List<BnplTransaction> findByBnplPlanIdOrderByCreatedAtDesc(Long planId);

    /**
     * Find transactions by type and status
     */
    List<BnplTransaction> findByTypeAndStatus(TransactionType type, TransactionStatus status);

    /**
     * Find transactions for a plan within date range
     */
    @Query("SELECT t FROM BnplTransaction t " +
           "WHERE t.bnplPlan.id = :planId " +
           "AND t.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY t.createdAt DESC")
    List<BnplTransaction> findPlanTransactionsInDateRange(
            @Param("planId") Long planId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Calculate total payments for a plan
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM BnplTransaction t " +
           "WHERE t.bnplPlan.id = :planId " +
           "AND t.type = 'PAYMENT' " +
           "AND t.status = 'COMPLETED'")
    BigDecimal getTotalPaymentsByPlanId(@Param("planId") Long planId);

    /**
     * Find pending transactions older than specified time
     */
    @Query("SELECT t FROM BnplTransaction t " +
           "WHERE t.status = 'PENDING' " +
           "AND t.createdAt < :cutoffTime")
    List<BnplTransaction> findPendingTransactionsOlderThan(
            @Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Find failed transactions for retry
     */
    @Query("SELECT t FROM BnplTransaction t " +
           "WHERE t.status = 'FAILED' " +
           "AND t.retryCount < :maxRetries " +
           "AND t.type = 'PAYMENT'")
    List<BnplTransaction> findFailedTransactionsForRetry(
            @Param("maxRetries") int maxRetries);

    /**
     * Find transactions by gateway reference
     */
    Optional<BnplTransaction> findByGatewayReference(String gatewayReference);

    /**
     * Get transaction statistics for a user
     */
    @Query("SELECT t.type, t.status, COUNT(t), SUM(t.amount) " +
           "FROM BnplTransaction t " +
           "JOIN t.bnplPlan p " +
           "WHERE p.userId = :userId " +
           "GROUP BY t.type, t.status")
    List<Object[]> getTransactionStatsByUserId(@Param("userId") String userId);

    /**
     * Find refunds for an original transaction
     */
    List<BnplTransaction> findByOriginalTransactionIdAndType(
            String originalTransactionId, TransactionType type);
}