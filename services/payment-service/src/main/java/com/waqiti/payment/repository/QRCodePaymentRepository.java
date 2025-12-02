package com.waqiti.payment.repository;

import com.waqiti.payment.entity.QRCodePayment;
import com.waqiti.payment.entity.QRCodePaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for QR code payment operations
 */
@Repository
public interface QRCodePaymentRepository extends JpaRepository<QRCodePayment, String> {

    /**
     * Find QR payment by QR code ID
     */
    Optional<QRCodePayment> findByQrCodeId(String qrCodeId);

    /**
     * Find QR payments by merchant ID
     */
    Page<QRCodePayment> findByMerchantId(String merchantId, Pageable pageable);

    /**
     * Find QR payments by status
     */
    List<QRCodePayment> findByStatus(QRCodePaymentStatus status);

    /**
     * Find QR payments by merchant and status
     */
    Page<QRCodePayment> findByMerchantIdAndStatus(String merchantId, QRCodePaymentStatus status, Pageable pageable);

    /**
     * Find expired QR codes
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.status = 'PENDING' AND q.expiresAt < :now")
    List<QRCodePayment> findExpiredQRCodes(@Param("now") Instant now);

    /**
     * Find QR payments scanned by user
     */
    Page<QRCodePayment> findByScannedBy(String userId, Pageable pageable);

    /**
     * Find QR payments by transaction ID
     */
    Optional<QRCodePayment> findByTransactionId(String transactionId);

    /**
     * Find QR payments by terminal ID
     */
    List<QRCodePayment> findByTerminalId(String terminalId);

    /**
     * Count QR payments by merchant and date range
     */
    @Query("SELECT COUNT(q) FROM QRCodePayment q WHERE q.merchantId = :merchantId " +
           "AND q.createdAt BETWEEN :startDate AND :endDate")
    Long countByMerchantAndDateRange(@Param("merchantId") String merchantId,
                                     @Param("startDate") Instant startDate,
                                     @Param("endDate") Instant endDate);

    /**
     * Calculate total amount by merchant and status
     */
    @Query("SELECT COALESCE(SUM(q.amount), 0) FROM QRCodePayment q " +
           "WHERE q.merchantId = :merchantId AND q.status = :status")
    BigDecimal calculateTotalAmountByMerchantAndStatus(@Param("merchantId") String merchantId,
                                                       @Param("status") QRCodePaymentStatus status);

    /**
     * Find QR payments with high fraud score
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.fraudScore > :threshold")
    List<QRCodePayment> findHighRiskQRPayments(@Param("threshold") BigDecimal threshold);

    /**
     * Update expired QR codes status
     */
    @Modifying
    @Query("UPDATE QRCodePayment q SET q.status = 'EXPIRED' " +
           "WHERE q.status = 'PENDING' AND q.expiresAt < :now")
    int updateExpiredQRCodes(@Param("now") Instant now);

    /**
     * Find QR payments by date range
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.createdAt BETWEEN :startDate AND :endDate")
    Page<QRCodePayment> findByDateRange(@Param("startDate") Instant startDate,
                                        @Param("endDate") Instant endDate,
                                        Pageable pageable);

    /**
     * Find completed QR payments by merchant and date
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.merchantId = :merchantId " +
           "AND q.status = 'COMPLETED' AND q.completedAt BETWEEN :startDate AND :endDate")
    List<QRCodePayment> findCompletedPaymentsByMerchantAndDate(@Param("merchantId") String merchantId,
                                                               @Param("startDate") Instant startDate,
                                                               @Param("endDate") Instant endDate);

    /**
     * Count QR payments by status
     */
    Long countByStatus(QRCodePaymentStatus status);

    /**
     * Find QR payments with multiple scan attempts
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.scanAttempts > :threshold")
    List<QRCodePayment> findWithMultipleScanAttempts(@Param("threshold") Integer threshold);

    /**
     * Find recent QR payments by merchant
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.merchantId = :merchantId " +
           "ORDER BY q.createdAt DESC")
    Page<QRCodePayment> findRecentByMerchant(@Param("merchantId") String merchantId, Pageable pageable);

    /**
     * Check if QR code ID exists
     */
    boolean existsByQrCodeId(String qrCodeId);

    /**
     * Delete old QR payments
     */
    @Modifying
    @Query("DELETE FROM QRCodePayment q WHERE q.createdAt < :cutoffDate " +
           "AND q.status IN ('EXPIRED', 'CANCELLED', 'FAILED')")
    int deleteOldQRPayments(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Find QR payments by location
     */
    List<QRCodePayment> findByLocation(String location);

    /**
     * Get merchant daily statistics
     */
    @Query("SELECT q.status, COUNT(q), SUM(q.amount) FROM QRCodePayment q " +
           "WHERE q.merchantId = :merchantId AND DATE(q.createdAt) = CURRENT_DATE " +
           "GROUP BY q.status")
    List<Object[]> getMerchantDailyStatistics(@Param("merchantId") String merchantId);
}