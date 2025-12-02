package com.waqiti.transaction.repository;

import com.waqiti.transaction.entity.Receipt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Receipt entity with comprehensive query methods
 */
@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    /**
     * Find receipt by transaction ID with optimized fetching
     */
    @EntityGraph(attributePaths = {"transaction", "transaction.payment"})
    Optional<Receipt> findByTransactionId(UUID transactionId);

    /**
     * Find all receipts for a transaction (in case of multiple formats)
     */
    @EntityGraph(attributePaths = {"transaction"})
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "50"))
    List<Receipt> findAllByTransactionId(UUID transactionId);

    /**
     * Find receipts that have expired
     */
    @Query("SELECT r FROM Receipt r LEFT JOIN FETCH r.transaction WHERE r.expiresAt < :now")
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "100"))
    List<Receipt> findExpiredReceipts(@Param("now") LocalDateTime now);

    /**
     * Find receipts by format
     */
    List<Receipt> findByFormat(com.waqiti.transaction.dto.ReceiptGenerationOptions.ReceiptFormat format);

    /**
     * Find receipts generated within date range
     */
    @Query("SELECT r FROM Receipt r LEFT JOIN FETCH r.transaction WHERE r.generatedAt BETWEEN :startDate AND :endDate")
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "100"))
    List<Receipt> findByGeneratedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                         @Param("endDate") LocalDateTime endDate);

    /**
     * Get receipt statistics
     */
    @Query("SELECT COUNT(r), SUM(r.accessCount), AVG(r.fileSize) FROM Receipt r")
    Object[] getReceiptStatistics();

    /**
     * Find most accessed receipts
     */
    @Query("SELECT r FROM Receipt r ORDER BY r.accessCount DESC")
    List<Receipt> findMostAccessedReceipts();

    /**
     * Update access count and last accessed time
     */
    @Modifying
    @Query("UPDATE Receipt r SET r.accessCount = r.accessCount + 1, r.lastAccessedAt = :accessTime WHERE r.id = :receiptId")
    void incrementAccessCount(@Param("receiptId") UUID receiptId, @Param("accessTime") LocalDateTime accessTime);

    /**
     * Mark receipt as emailed
     */
    @Modifying
    @Query("UPDATE Receipt r SET r.emailed = true, r.emailCount = r.emailCount + 1 WHERE r.id = :receiptId")
    void markAsEmailed(@Param("receiptId") UUID receiptId);

    /**
     * Find receipts that haven't been accessed recently (for cleanup)
     */
    @Query("SELECT r FROM Receipt r WHERE r.lastAccessedAt < :cutoffDate OR r.lastAccessedAt IS NULL")
    List<Receipt> findUnusedReceipts(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Get total storage used by receipts
     */
    @Query("SELECT SUM(r.fileSize) FROM Receipt r")
    Long getTotalStorageUsed();

    /**
     * Find receipts by transaction IDs (bulk operation) with batch fetching
     */
    @Query("SELECT DISTINCT r FROM Receipt r LEFT JOIN FETCH r.transaction WHERE r.transactionId IN :transactionIds")
    @QueryHints({
        @QueryHint(name = "org.hibernate.fetchSize", value = "100"),
        @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Receipt> findByTransactionIds(@Param("transactionIds") List<UUID> transactionIds);

    /**
     * Delete expired receipts
     */
    @Modifying
    @Query("DELETE FROM Receipt r WHERE r.expiresAt < :now")
    void deleteExpiredReceipts(@Param("now") LocalDateTime now);

    /**
     * Count receipts by format
     */
    @Query("SELECT r.format, COUNT(r) FROM Receipt r GROUP BY r.format")
    List<Object[]> countByFormat();
}