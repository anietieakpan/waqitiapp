package com.waqiti.payment.repository;

import com.waqiti.payment.domain.InstantTransfer;
import com.waqiti.payment.domain.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

/**
 * Repository for InstantTransfer entities with comprehensive querying capabilities
 */
@Repository
public interface InstantTransferRepository extends JpaRepository<InstantTransfer, UUID> {

    /**
     * Find transfer by transaction reference
     */
    Optional<InstantTransfer> findByTransactionReference(String transactionReference);

    /**
     * Find transfers by sender ID
     */
    List<InstantTransfer> findBySenderIdOrderByCreatedAtDesc(String senderId);

    /**
     * Find transfers by recipient ID
     */
    List<InstantTransfer> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    /**
     * Find transfers by user ID (sender or recipient)
     */
    @Query("SELECT it FROM InstantTransfer it WHERE it.senderId = :userId OR it.recipientId = :userId ORDER BY it.createdAt DESC")
    List<InstantTransfer> findByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    /**
     * Find transfers by status
     */
    List<InstantTransfer> findByStatusOrderByCreatedAtDesc(TransferStatus status);

    /**
     * Find transfers by network type
     */
    List<InstantTransfer> findByNetworkTypeOrderByCreatedAtDesc(String networkType);

    /**
     * Find transfers within date range
     */
    List<InstantTransfer> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find pending transfers that need processing
     */
    @Query("SELECT it FROM InstantTransfer it WHERE it.status IN ('PENDING', 'PROCESSING') AND it.createdAt < :timeout ORDER BY it.createdAt ASC")
    List<InstantTransfer> findPendingTransfersOlderThan(@Param("timeout") LocalDateTime timeout);

    /**
     * Find transfers by amount range
     */
    @Query("SELECT it FROM InstantTransfer it WHERE it.amount.amount BETWEEN :minAmount AND :maxAmount ORDER BY it.createdAt DESC")
    List<InstantTransfer> findByAmountRange(@Param("minAmount") BigDecimal minAmount, @Param("maxAmount") BigDecimal maxAmount);

    /**
     * Sum transfer amounts for user within period
     */
    @Query("SELECT COALESCE(SUM(it.amount.amount), 0) FROM InstantTransfer it WHERE " +
           "(it.senderId = :userId OR it.recipientId = :userId) AND " +
           "it.createdAt >= :since AND " +
           "it.status IN ('COMPLETED', 'SETTLED')")
    BigDecimal sumAmountByUserIdAndCreatedAtAfter(@Param("userId") String userId, @Param("since") LocalDateTime since);

    /**
     * Count transfers for user within period
     */
    @Query("SELECT COUNT(it) FROM InstantTransfer it WHERE " +
           "(it.senderId = :userId OR it.recipientId = :userId) AND " +
           "it.createdAt >= :since")
    int countByUserIdAndCreatedAtAfter(@Param("userId") String userId, @Param("since") LocalDateTime since);

    /**
     * Find transfers with settlement delays
     */
    @Query("SELECT it FROM InstantTransfer it WHERE " +
           "it.status = 'PROCESSING' AND " +
           "it.createdAt < :threshold AND " +
           "it.expectedSettlementTime IS NOT NULL AND " +
           "it.expectedSettlementTime < CURRENT_TIMESTAMP")
    List<InstantTransfer> findDelayedSettlements(@Param("threshold") LocalDateTime threshold);

    /**
     * Find transfers by currency
     */
    @Query("SELECT it FROM InstantTransfer it WHERE it.amount.currency = :currency ORDER BY it.createdAt DESC")
    List<InstantTransfer> findByCurrency(@Param("currency") String currency);

    /**
     * Search transfers with complex criteria
     */
    @Query("SELECT it FROM InstantTransfer it WHERE " +
           "(:senderId IS NULL OR it.senderId = :senderId) AND " +
           "(:recipientId IS NULL OR it.recipientId = :recipientId) AND " +
           "(:status IS NULL OR it.status = :status) AND " +
           "(:networkType IS NULL OR it.networkType = :networkType) AND " +
           "(:minAmount IS NULL OR it.amount.amount >= :minAmount) AND " +
           "(:maxAmount IS NULL OR it.amount.amount <= :maxAmount) AND " +
           "it.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY it.createdAt DESC")
    Page<InstantTransfer> searchTransfers(
        @Param("senderId") String senderId,
        @Param("recipientId") String recipientId,
        @Param("status") TransferStatus status,
        @Param("networkType") String networkType,
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        Pageable pageable
    );

    /**
     * Find transfers requiring reconciliation
     */
    @Query("SELECT it FROM InstantTransfer it WHERE " +
           "it.status = 'COMPLETED' AND " +
           "it.reconciledAt IS NULL AND " +
           "it.completedAt < :reconciliationThreshold")
    List<InstantTransfer> findTransfersRequiringReconciliation(@Param("reconciliationThreshold") LocalDateTime reconciliationThreshold);

    /**
     * Get transfer statistics for period
     */
    @Query("SELECT " +
           "COUNT(it) as totalTransfers, " +
           "SUM(it.amount.amount) as totalAmount, " +
           "AVG(it.amount.amount) as avgAmount, " +
           "COUNT(CASE WHEN it.status = 'COMPLETED' THEN 1 END) as completedTransfers, " +
           "COUNT(CASE WHEN it.status = 'FAILED' THEN 1 END) as failedTransfers " +
           "FROM InstantTransfer it WHERE it.createdAt >= :since")
    Object[] getTransferStatistics(@Param("since") LocalDateTime since);

    /**
     * Find transfers by processing time threshold
     */
    @Query("SELECT it FROM InstantTransfer it WHERE " +
           "it.status = 'COMPLETED' AND " +
           "it.processingTimeMs > :threshold " +
           "ORDER BY it.processingTimeMs DESC")
    List<InstantTransfer> findSlowTransfers(@Param("threshold") long threshold);

    /**
     * Count transfers by status and date
     */
    @Query("SELECT it.status, COUNT(it) FROM InstantTransfer it WHERE it.createdAt >= :since GROUP BY it.status")
    List<Object[]> countTransfersByStatus(@Param("since") LocalDateTime since);

    /**
     * Count transfers by network type
     */
    @Query("SELECT it.networkType, COUNT(it) FROM InstantTransfer it WHERE it.createdAt >= :since GROUP BY it.networkType")
    List<Object[]> countTransfersByNetwork(@Param("since") LocalDateTime since);

    /**
     * Find duplicate transfers by idempotency key
     */
    Optional<InstantTransfer> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find transfers with processing errors
     */
    @Query("SELECT it FROM InstantTransfer it WHERE " +
           "it.errorCode IS NOT NULL OR " +
           "it.errorMessage IS NOT NULL " +
           "ORDER BY it.createdAt DESC")
    List<InstantTransfer> findTransfersWithErrors();

    /**
     * Update transfer status in batch
     */
    @Modifying
    @Query("UPDATE InstantTransfer it SET it.status = :newStatus, it.updatedAt = :timestamp WHERE it.id IN :transferIds")
    void updateStatusBatch(@Param("transferIds") List<UUID> transferIds, 
                          @Param("newStatus") TransferStatus newStatus, 
                          @Param("timestamp") LocalDateTime timestamp);

    /**
     * Find transfers needing status updates from external networks
     */
    @Query("SELECT it FROM InstantTransfer it WHERE " +
           "it.status IN ('PROCESSING', 'PENDING') AND " +
           "it.externalTransactionId IS NOT NULL AND " +
           "it.lastStatusCheck IS NULL OR it.lastStatusCheck < :staleThreshold")
    List<InstantTransfer> findTransfersNeedingStatusUpdate(@Param("staleThreshold") LocalDateTime staleThreshold);

    /**
     * Get hourly transfer volume
     */
    @Query("SELECT EXTRACT(HOUR FROM it.createdAt) as hour, COUNT(it) as count, SUM(it.amount.amount) as volume " +
           "FROM InstantTransfer it WHERE it.createdAt >= :since " +
           "GROUP BY EXTRACT(HOUR FROM it.createdAt) " +
           "ORDER BY hour")
    List<Object[]> getHourlyTransferVolume(@Param("since") LocalDateTime since);

    /**
     * Find transfers for compliance reporting
     */
    @Query("SELECT it FROM InstantTransfer it WHERE " +
           "it.amount.amount >= :reportingThreshold AND " +
           "it.createdAt BETWEEN :startDate AND :endDate AND " +
           "it.status = 'COMPLETED' " +
           "ORDER BY it.amount.amount DESC")
    List<InstantTransfer> findTransfersForComplianceReporting(@Param("reportingThreshold") BigDecimal reportingThreshold,
                                                             @Param("startDate") LocalDateTime startDate,
                                                             @Param("endDate") LocalDateTime endDate);

    /**
     * Find transfers by external transaction ID
     */
    Optional<InstantTransfer> findByExternalTransactionId(String externalTransactionId);

    /**
     * Find transfers with specific metadata
     */
    @Query("SELECT it FROM InstantTransfer it WHERE FUNCTION('JSON_EXTRACT', it.metadata, :jsonPath) = :value")
    List<InstantTransfer> findByMetadata(@Param("jsonPath") String jsonPath, @Param("value") String value);

    /**
     * Archive old completed transfers
     */
    @Query("UPDATE InstantTransfer it SET it.archived = true WHERE " +
           "it.status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND " +
           "it.createdAt < :archiveThreshold")
    void archiveOldTransfers(@Param("archiveThreshold") LocalDateTime archiveThreshold);

    /**
     * Delete archived transfers beyond retention period
     */
    @Query("DELETE FROM InstantTransfer it WHERE it.archived = true AND it.createdAt < :deleteThreshold")
    void deleteArchivedTransfers(@Param("deleteThreshold") LocalDateTime deleteThreshold);

    /**
     * Find cross-border transfers
     */
    @Query("SELECT it FROM InstantTransfer it WHERE " +
           "FUNCTION('JSON_EXTRACT', it.metadata, '$.senderCountry') != FUNCTION('JSON_EXTRACT', it.metadata, '$.recipientCountry') " +
           "ORDER BY it.createdAt DESC")
    List<InstantTransfer> findCrossBorderTransfers();

    /**
     * Get success rate by network
     */
    @Query("SELECT it.networkType, " +
           "COUNT(CASE WHEN it.status = 'COMPLETED' THEN 1 END) * 100.0 / COUNT(it) as successRate " +
           "FROM InstantTransfer it WHERE it.createdAt >= :since " +
           "GROUP BY it.networkType")
    List<Object[]> getSuccessRateByNetwork(@Param("since") LocalDateTime since);

    /**
     * Find transfers with unusual patterns (for fraud detection)
     */
    @Query("SELECT it FROM InstantTransfer it WHERE " +
           "it.amount.amount = :suspiciousAmount OR " +
           "(it.amount.amount BETWEEN :minRoundAmount AND :maxRoundAmount AND it.amount.amount % 100 = 0) " +
           "ORDER BY it.createdAt DESC")
    List<InstantTransfer> findSuspiciousPatterns(@Param("suspiciousAmount") BigDecimal suspiciousAmount,
                                               @Param("minRoundAmount") BigDecimal minRoundAmount,
                                               @Param("maxRoundAmount") BigDecimal maxRoundAmount);

    /**
     * Find transfers between specific user pairs
     */
    @Query("SELECT it FROM InstantTransfer it WHERE " +
           "(it.senderId = :userId1 AND it.recipientId = :userId2) OR " +
           "(it.senderId = :userId2 AND it.recipientId = :userId1) " +
           "ORDER BY it.createdAt DESC")
    List<InstantTransfer> findTransfersBetweenUsers(@Param("userId1") String userId1, @Param("userId2") String userId2);
}