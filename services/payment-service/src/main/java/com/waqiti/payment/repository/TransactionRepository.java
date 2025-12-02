package com.waqiti.payment.repository;

import com.waqiti.payment.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Transaction entities with optimized queries to prevent N+1 problems
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    
    @EntityGraph(attributePaths = {"payment", "fees"})
    Optional<Transaction> findByProviderTransactionId(String providerTransactionId);
    
    @EntityGraph(attributePaths = {"payment", "fees"})
    List<Transaction> findByPaymentId(UUID paymentId);
    
    @EntityGraph(attributePaths = {"payment", "fees", "user"})
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId ORDER BY t.createdAt DESC")
    Page<Transaction> findByUserId(@Param("userId") UUID userId, Pageable pageable);
    
    @EntityGraph(attributePaths = {"payment", "fees", "user"})
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId")
    List<Transaction> findAllByUserId(@Param("userId") UUID userId);
    
    @EntityGraph(attributePaths = {"payment", "fees", "user"})
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.status = :status ORDER BY t.createdAt DESC")
    Page<Transaction> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") Transaction.Status status, Pageable pageable);
    
    @EntityGraph(attributePaths = {"payment", "fees", "user"})
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.status = :status")
    List<Transaction> findAllByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") Transaction.Status status);
    
    Page<Transaction> findByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    List<Transaction> findAllByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.status = :status AND t.createdAt >= :since ORDER BY t.createdAt DESC")
    Page<Transaction> findRecentTransactionsByUserAndStatus(
            @Param("userId") UUID userId,
            @Param("status") Transaction.Status status,
            @Param("since") LocalDateTime since,
            Pageable pageable
    );
    
    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.status = :status AND t.createdAt >= :since")
    List<Transaction> findAllRecentTransactionsByUserAndStatus(
            @Param("userId") UUID userId,
            @Param("status") Transaction.Status status,
            @Param("since") LocalDateTime since
    );
    
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.userId = :userId AND t.status = 'COMPLETED' AND t.createdAt >= :since")
    long countCompletedTransactionsSince(
            @Param("userId") UUID userId,
            @Param("since") LocalDateTime since
    );
    
    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.userId = :userId AND t.status = 'COMPLETED' AND t.createdAt >= :since")
    Optional<java.math.BigDecimal> sumCompletedTransactionsSince(
            @Param("userId") UUID userId,
            @Param("since") LocalDateTime since
    );
    
    @EntityGraph(attributePaths = {"payment", "fees"})
    @Query("SELECT t FROM Transaction t WHERE t.paymentId = :paymentId ORDER BY t.createdAt DESC")
    Page<Transaction> findByPaymentIdPaginated(@Param("paymentId") UUID paymentId, Pageable pageable);
    
    @Query("SELECT t FROM Transaction t WHERE t.status IN :statuses AND t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    Page<Transaction> findByStatusInAndCreatedAtBetween(
            @Param("statuses") List<Transaction.Status> statuses,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
}