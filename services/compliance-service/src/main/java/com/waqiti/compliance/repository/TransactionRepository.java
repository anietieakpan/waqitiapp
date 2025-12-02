package com.waqiti.compliance.repository;

import com.waqiti.compliance.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Transaction Repository for Compliance Service
 * Provides transaction data access for sanctions screening and AML monitoring
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("SELECT t.id FROM Transaction t WHERE t.createdAt >= :since AND t.amount >= :threshold AND t.status = 'COMPLETED'")
    List<UUID> findHighValueTransactionIds(
        @Param("since") LocalDateTime since,
        @Param("threshold") BigDecimal threshold
    );

    @Query("SELECT t FROM Transaction t WHERE t.senderId = :userId OR t.recipientId = :userId")
    List<Transaction> findByUserInvolved(@Param("userId") UUID userId);

    @Query("SELECT t FROM Transaction t WHERE t.status = 'PENDING_COMPLIANCE_REVIEW'")
    List<Transaction> findPendingComplianceReview();

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.createdAt >= :since AND t.amount >= :threshold")
    long countHighValueTransactionsSince(
        @Param("since") LocalDateTime since,
        @Param("threshold") BigDecimal threshold
    );

    @Query("SELECT t FROM Transaction t WHERE t.flaggedForReview = true AND t.reviewedAt IS NULL")
    List<Transaction> findUnreviewedFlaggedTransactions();
}