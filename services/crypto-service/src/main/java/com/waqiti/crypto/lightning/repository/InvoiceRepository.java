package com.waqiti.crypto.lightning.repository;

import com.waqiti.crypto.lightning.entity.InvoiceEntity;
import com.waqiti.crypto.lightning.entity.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Lightning invoice entities
 */
@Repository
public interface InvoiceRepository extends JpaRepository<InvoiceEntity, String>, JpaSpecificationExecutor<InvoiceEntity> {

    /**
     * Find invoice by payment hash
     */
    Optional<InvoiceEntity> findByPaymentHash(String paymentHash);

    /**
     * Find invoices by user ID
     */
    Page<InvoiceEntity> findByUserId(String userId, Pageable pageable);

    /**
     * Find invoices by user ID and status
     */
    Page<InvoiceEntity> findByUserIdAndStatus(String userId, InvoiceStatus status, Pageable pageable);

    /**
     * Find pending invoices that have expired
     */
    @Query("SELECT i FROM InvoiceEntity i WHERE i.status = 'PENDING' AND i.expiresAt < :now")
    List<InvoiceEntity> findExpiredInvoices(@Param("now") Instant now);

    /**
     * Find invoices created within a time range
     */
    @Query("SELECT i FROM InvoiceEntity i WHERE i.userId = :userId " +
           "AND i.createdAt >= :startTime AND i.createdAt <= :endTime")
    List<InvoiceEntity> findByUserIdAndTimeRange(
        @Param("userId") String userId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    /**
     * Get invoice statistics grouped by status
     */
    @Query("SELECT i.status, COUNT(i), SUM(i.amountSat) FROM InvoiceEntity i " +
           "WHERE i.userId = :userId AND i.createdAt >= :startTime AND i.createdAt <= :endTime " +
           "GROUP BY i.status")
    List<Object[]> getUserInvoiceStatistics(
        @Param("userId") String userId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    /**
     * Count invoices by status
     */
    long countByStatus(InvoiceStatus status);

    /**
     * Count user's invoices by status
     */
    long countByUserIdAndStatus(String userId, InvoiceStatus status);

    /**
     * Get total amount for paid invoices
     */
    @Query("SELECT SUM(i.amountPaidSat) FROM InvoiceEntity i " +
           "WHERE i.userId = :userId AND i.status = 'PAID'")
    Long getTotalPaidAmount(@Param("userId") String userId);

    /**
     * Get total amount for pending invoices
     */
    @Query("SELECT SUM(i.amountSat) FROM InvoiceEntity i " +
           "WHERE i.userId = :userId AND i.status = 'PENDING'")
    Long getTotalPendingAmount(@Param("userId") String userId);

    /**
     * Find invoices with pending webhooks
     */
    @Query("SELECT i FROM InvoiceEntity i WHERE i.webhookUrl IS NOT NULL " +
           "AND i.status = 'PAID' AND i.webhookAttempts < 3")
    List<InvoiceEntity> findInvoicesWithPendingWebhooks();

    /**
     * Delete old invoices
     */
    @Modifying
    @Query("DELETE FROM InvoiceEntity i WHERE i.status IN ('CANCELLED', 'EXPIRED') " +
           "AND i.createdAt < :cutoffDate")
    int deleteOldInvoices(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Find recurring invoices due for generation
     */
    @Query("SELECT i FROM InvoiceEntity i WHERE i.isRecurring = true " +
           "AND i.status = 'PAID' AND i.recurringSchedule IS NOT NULL")
    List<InvoiceEntity> findRecurringInvoicesDue();

    /**
     * Check if payment hash exists
     */
    boolean existsByPaymentHash(String paymentHash);

    /**
     * Find invoices by Lightning address
     */
    List<InvoiceEntity> findByLightningAddress(String lightningAddress);

    /**
     * Get recent invoices for a user
     */
    List<InvoiceEntity> findTop10ByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find invoices by LNURL code
     */
    Optional<InvoiceEntity> findByLnurlPayCode(String lnurlPayCode);

    /**
     * Update invoice status in bulk
     */
    @Modifying
    @Query("UPDATE InvoiceEntity i SET i.status = :newStatus, i.updatedAt = :now " +
           "WHERE i.paymentHash IN :paymentHashes AND i.status = :oldStatus")
    int bulkUpdateStatus(
        @Param("paymentHashes") List<String> paymentHashes,
        @Param("oldStatus") InvoiceStatus oldStatus,
        @Param("newStatus") InvoiceStatus newStatus,
        @Param("now") Instant now
    );
}