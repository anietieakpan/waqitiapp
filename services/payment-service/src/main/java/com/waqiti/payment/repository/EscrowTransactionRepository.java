package com.waqiti.payment.repository;

import com.waqiti.payment.entity.EscrowTransaction;
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
 * Repository for Escrow Transaction management
 * 
 * AUDIT: Immutable transaction records for regulatory compliance
 * PERFORMANCE: Optimized queries with proper indexing
 */
@Repository
public interface EscrowTransactionRepository extends JpaRepository<EscrowTransaction, UUID> {
    
    /**
     * Find transaction by transaction ID
     */
    Optional<EscrowTransaction> findByTransactionId(String transactionId);
    
    /**
     * Find transactions by escrow account ID
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.escrowId = :escrowId " +
           "ORDER BY et.createdAt DESC")
    List<EscrowTransaction> findByEscrowId(@Param("escrowId") String escrowId);
    
    /**
     * Find transactions by contract ID
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.contractId = :contractId " +
           "ORDER BY et.createdAt DESC")
    List<EscrowTransaction> findByContractId(@Param("contractId") String contractId);
    
    /**
     * Find transactions by type
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.transactionType = :type " +
           "ORDER BY et.createdAt DESC")
    List<EscrowTransaction> findByTransactionType(@Param("type") EscrowTransaction.TransactionType type);
    
    /**
     * Find transactions by status
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.status = :status " +
           "ORDER BY et.createdAt DESC")
    List<EscrowTransaction> findByStatus(@Param("status") EscrowTransaction.TransactionStatus status);
    
    /**
     * Find transactions initiated by a party
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.initiatedBy = :partyId " +
           "ORDER BY et.createdAt DESC")
    List<EscrowTransaction> findByInitiatedBy(@Param("partyId") String partyId);
    
    /**
     * Find transactions involving a party (from or to)
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.fromPartyId = :partyId " +
           "OR et.toPartyId = :partyId ORDER BY et.createdAt DESC")
    List<EscrowTransaction> findByPartyId(@Param("partyId") String partyId);
    
    /**
     * Find pending approval transactions
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.status = 'PENDING_APPROVAL' " +
           "AND et.approvalCount < et.requiredApprovals ORDER BY et.createdAt ASC")
    List<EscrowTransaction> findPendingApprovalTransactions();
    
    /**
     * Find transactions by idempotency key
     */
    Optional<EscrowTransaction> findByIdempotencyKey(String idempotencyKey);
    
    /**
     * Check if idempotency key exists
     */
    boolean existsByIdempotencyKey(String idempotencyKey);
    
    /**
     * Find completed transactions for an escrow account
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.escrowId = :escrowId " +
           "AND et.status = 'COMPLETED' ORDER BY et.completedAt DESC")
    List<EscrowTransaction> findCompletedTransactionsByEscrowId(@Param("escrowId") String escrowId);
    
    /**
     * Find failed transactions for an escrow account
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.escrowId = :escrowId " +
           "AND et.status = 'FAILED' ORDER BY et.failedAt DESC")
    List<EscrowTransaction> findFailedTransactionsByEscrowId(@Param("escrowId") String escrowId);
    
    /**
     * Find reversed transactions
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.reversed = true " +
           "ORDER BY et.reversedAt DESC")
    List<EscrowTransaction> findReversedTransactions();
    
    /**
     * Find transaction by reversal transaction ID
     */
    Optional<EscrowTransaction> findByReversalTransactionId(String reversalTransactionId);
    
    /**
     * Calculate total transaction amount by escrow and type
     */
    @Query("SELECT SUM(et.amount) FROM EscrowTransaction et WHERE et.escrowId = :escrowId " +
           "AND et.transactionType = :type AND et.status = 'COMPLETED'")
    BigDecimal sumAmountByEscrowIdAndType(@Param("escrowId") String escrowId,
                                          @Param("type") EscrowTransaction.TransactionType type);
    
    /**
     * Calculate total fees collected for an escrow account
     */
    @Query("SELECT SUM(et.feeAmount) FROM EscrowTransaction et WHERE et.escrowId = :escrowId " +
           "AND et.status = 'COMPLETED'")
    BigDecimal sumFeesByEscrowId(@Param("escrowId") String escrowId);
    
    /**
     * Find high-value transactions
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.amount >= :threshold " +
           "ORDER BY et.amount DESC, et.createdAt DESC")
    List<EscrowTransaction> findHighValueTransactions(@Param("threshold") BigDecimal threshold);
    
    /**
     * Find transactions created within date range
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY et.createdAt DESC")
    List<EscrowTransaction> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate,
                                                   @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find transactions by escrow and type
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.escrowId = :escrowId " +
           "AND et.transactionType = :type ORDER BY et.createdAt DESC")
    List<EscrowTransaction> findByEscrowIdAndType(@Param("escrowId") String escrowId,
                                                  @Param("type") EscrowTransaction.TransactionType type);
    
    /**
     * Count transactions by status
     */
    @Query("SELECT et.status, COUNT(et) FROM EscrowTransaction et GROUP BY et.status")
    List<Object[]> countByStatus();
    
    /**
     * Count transactions by type
     */
    @Query("SELECT et.transactionType, COUNT(et) FROM EscrowTransaction et GROUP BY et.transactionType")
    List<Object[]> countByType();
    
    /**
     * Find stuck transactions (processing for too long)
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.status = 'PROCESSING' " +
           "AND et.updatedAt < :stuckThreshold ORDER BY et.updatedAt ASC")
    List<EscrowTransaction> findStuckTransactions(@Param("stuckThreshold") LocalDateTime stuckThreshold);
    
    /**
     * Find authorized but not completed transactions
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.status = 'AUTHORIZED' " +
           "AND et.authorizedAt < :expiryThreshold")
    List<EscrowTransaction> findExpiredAuthorizations(@Param("expiryThreshold") LocalDateTime expiryThreshold);
    
    /**
     * Find recent transactions for an escrow account
     */
    @Query("SELECT et FROM EscrowTransaction et WHERE et.escrowId = :escrowId " +
           "AND et.createdAt >= :since ORDER BY et.createdAt DESC")
    List<EscrowTransaction> findRecentTransactionsByEscrowId(@Param("escrowId") String escrowId,
                                                             @Param("since") LocalDateTime since);
}