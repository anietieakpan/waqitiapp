package com.waqiti.payment.repository;

import com.waqiti.payment.entity.ACHTransfer;
import com.waqiti.payment.entity.ACHTransferStatus;
import com.waqiti.payment.entity.TransferDirection;
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
import java.util.UUID;

/**
 * Repository for ACH transfers
 */
@Repository
public interface ACHTransferRepository extends JpaRepository<ACHTransfer, UUID> {
    
    /**
     * Find transfers by user ID and date range
     */
    List<ACHTransfer> findByUserIdAndCreatedAtAfter(UUID userId, LocalDateTime after);
    
    /**
     * Find transfers by user ID with pagination
     */
    Page<ACHTransfer> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find transfers by wallet ID with pagination
     */
    Page<ACHTransfer> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);
    
    /**
     * Find by idempotency key
     */
    Optional<ACHTransfer> findByIdempotencyKey(String idempotencyKey);
    
    /**
     * Find by external reference ID
     */
    Optional<ACHTransfer> findByExternalReferenceId(String externalReferenceId);
    
    /**
     * Find transfers by status
     */
    List<ACHTransfer> findByStatus(ACHTransferStatus status);
    
    /**
     * Find processing transfers that are stuck
     */
    @Query("SELECT t FROM ACHTransfer t WHERE t.status = 'PROCESSING' " +
           "AND t.processingStartedAt < :cutoffTime")
    List<ACHTransfer> findStuckProcessingTransfers(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    /**
     * Calculate daily total for a user
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM ACHTransfer t " +
           "WHERE t.userId = :userId " +
           "AND t.createdAt >= :startOfDay " +
           "AND t.status NOT IN ('FAILED', 'CANCELLED')")
    BigDecimal calculateDailyTotal(@Param("userId") UUID userId, 
                                  @Param("startOfDay") LocalDateTime startOfDay);
    
    /**
     * Find transfers by status and direction
     */
    List<ACHTransfer> findByStatusAndDirection(ACHTransferStatus status, 
                                               TransferDirection direction);
    
    /**
     * Count transfers by status for a user
     */
    @Query("SELECT COUNT(t) FROM ACHTransfer t WHERE t.userId = :userId AND t.status = :status")
    long countByUserIdAndStatus(@Param("userId") UUID userId, 
                               @Param("status") ACHTransferStatus status);
    
    /**
     * Find recent completed transfers for a user
     */
    @Query("SELECT t FROM ACHTransfer t WHERE t.userId = :userId " +
           "AND t.status = 'COMPLETED' " +
           "ORDER BY t.completedAt DESC")
    Page<ACHTransfer> findRecentCompletedTransfers(@Param("userId") UUID userId, 
                                                   Pageable pageable);
    
    /**
     * Find transfers pending webhook confirmation
     */
    @Query("SELECT t FROM ACHTransfer t WHERE t.status = 'PROCESSING' " +
           "AND t.externalReferenceId IS NOT NULL " +
           "AND t.processingStartedAt < :cutoffTime")
    List<ACHTransfer> findTransfersPendingWebhook(@Param("cutoffTime") LocalDateTime cutoffTime);
}