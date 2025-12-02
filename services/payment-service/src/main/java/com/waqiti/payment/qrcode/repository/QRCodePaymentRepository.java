package com.waqiti.payment.qrcode.repository;

import com.waqiti.payment.qrcode.domain.QRCodePayment;
import com.waqiti.payment.qrcode.domain.QRCodeType;
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

/**
 * Repository for QR Code Payment entities
 * Provides comprehensive data access methods for QR code operations
 */
@Repository
public interface QRCodePaymentRepository extends JpaRepository<QRCodePayment, Long> {
    
    /**
     * Find QR code payment by QR code ID
     */
    Optional<QRCodePayment> findByQrCodeId(String qrCodeId);
    
    /**
     * Find active QR codes for a user
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.userId = :userId AND q.status = 'ACTIVE' AND (q.expiresAt IS NULL OR q.expiresAt > :now)")
    List<QRCodePayment> findActiveQRCodesByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);
    
    /**
     * Find QR codes by merchant ID
     */
    Page<QRCodePayment> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);
    
    /**
     * Find QR codes by user ID with pagination
     */
    Page<QRCodePayment> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    /**
     * Find QR codes by status
     */
    List<QRCodePayment> findByStatusAndCreatedAtBetween(QRCodePayment.Status status, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find expired QR codes for cleanup
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.status = 'ACTIVE' AND q.expiresAt <= :now")
    List<QRCodePayment> findExpiredQRCodes(@Param("now") LocalDateTime now);
    
    /**
     * Mark expired QR codes as expired
     */
    @Modifying
    @Query("UPDATE QRCodePayment q SET q.status = 'EXPIRED' WHERE q.status = 'ACTIVE' AND q.expiresAt <= :now")
    int markExpiredQRCodes(@Param("now") LocalDateTime now);
    
    /**
     * Find QR codes by type and date range
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.type = :type AND q.createdAt BETWEEN :startDate AND :endDate ORDER BY q.createdAt DESC")
    List<QRCodePayment> findByTypeAndDateRange(@Param("type") QRCodeType type, 
                                               @Param("startDate") LocalDateTime startDate, 
                                               @Param("endDate") LocalDateTime endDate);
    
    /**
     * Count QR codes by status for a user
     */
    @Query("SELECT q.status, COUNT(q) FROM QRCodePayment q WHERE q.userId = :userId GROUP BY q.status")
    List<Object[]> countQRCodesByStatusForUser(@Param("userId") String userId);
    
    /**
     * Count QR codes by status for a merchant
     */
    @Query("SELECT q.status, COUNT(q) FROM QRCodePayment q WHERE q.merchantId = :merchantId GROUP BY q.status")
    List<Object[]> countQRCodesByStatusForMerchant(@Param("merchantId") String merchantId);
    
    /**
     * Get payment analytics for merchant
     */
    @Query("SELECT DATE(q.completedAt) as paymentDate, COUNT(q) as count, SUM(q.finalAmount) as totalAmount " +
           "FROM QRCodePayment q WHERE q.merchantId = :merchantId AND q.status = 'COMPLETED' " +
           "AND q.completedAt BETWEEN :startDate AND :endDate GROUP BY DATE(q.completedAt) ORDER BY paymentDate")
    List<Object[]> getMerchantPaymentAnalytics(@Param("merchantId") String merchantId,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get user payment analytics
     */
    @Query("SELECT DATE(q.completedAt) as paymentDate, COUNT(q) as count, SUM(q.finalAmount) as totalAmount " +
           "FROM QRCodePayment q WHERE q.userId = :userId AND q.status = 'COMPLETED' " +
           "AND q.completedAt BETWEEN :startDate AND :endDate GROUP BY DATE(q.completedAt) ORDER BY paymentDate")
    List<Object[]> getUserPaymentAnalytics(@Param("userId") String userId,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find high-value transactions for monitoring
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.finalAmount >= :threshold AND q.status = 'COMPLETED' " +
           "AND q.completedAt >= :since ORDER BY q.finalAmount DESC")
    List<QRCodePayment> findHighValueTransactions(@Param("threshold") BigDecimal threshold,
                                                   @Param("since") LocalDateTime since);
    
    /**
     * Find frequent scanners (potential fraud detection)
     */
    @Query("SELECT q.payerUserId, COUNT(q) FROM QRCodePayment q WHERE q.processedAt >= :since " +
           "GROUP BY q.payerUserId HAVING COUNT(q) >= :threshold ORDER BY COUNT(q) DESC")
    List<Object[]> findFrequentScanners(@Param("since") LocalDateTime since, @Param("threshold") Long threshold);
    
    /**
     * Find QR codes with failed payments
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.status = 'FAILED' AND q.processedAt >= :since ORDER BY q.processedAt DESC")
    List<QRCodePayment> findFailedPayments(@Param("since") LocalDateTime since);
    
    /**
     * Get conversion rate analytics
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN q.status = 'COMPLETED' THEN 1 END) as completed, " +
           "COUNT(CASE WHEN q.processedAt IS NOT NULL THEN 1 END) as attempted, " +
           "COUNT(q) as generated " +
           "FROM QRCodePayment q WHERE q.createdAt BETWEEN :startDate AND :endDate")
    Object[] getConversionAnalytics(@Param("startDate") LocalDateTime startDate, 
                                    @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find QR codes by reference
     */
    Optional<QRCodePayment> findByReference(String reference);
    
    /**
     * Find merchant static QR codes
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.merchantId = :merchantId AND q.type = 'MERCHANT_STATIC' AND q.status = 'ACTIVE'")
    List<QRCodePayment> findMerchantStaticQRCodes(@Param("merchantId") String merchantId);
    
    /**
     * Check if user has active QR codes
     */
    @Query("SELECT COUNT(q) > 0 FROM QRCodePayment q WHERE q.userId = :userId AND q.status = 'ACTIVE' " +
           "AND (q.expiresAt IS NULL OR q.expiresAt > :now)")
    boolean hasActiveQRCodes(@Param("userId") String userId, @Param("now") LocalDateTime now);
    
    /**
     * Get payment volume by currency
     */
    @Query("SELECT q.currency, COUNT(q), SUM(q.finalAmount) FROM QRCodePayment q " +
           "WHERE q.status = 'COMPLETED' AND q.completedAt BETWEEN :startDate AND :endDate " +
           "GROUP BY q.currency ORDER BY SUM(q.finalAmount) DESC")
    List<Object[]> getPaymentVolumeByCurrency(@Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find recently processed payments for notifications
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.status = 'COMPLETED' AND q.completedAt >= :since " +
           "ORDER BY q.completedAt DESC")
    List<QRCodePayment> findRecentlyCompletedPayments(@Param("since") LocalDateTime since, Pageable pageable);
    
    /**
     * Get average payment amount by merchant category
     */
    @Query("SELECT m.category, AVG(q.finalAmount) FROM QRCodePayment q " +
           "JOIN q.metadata m WHERE m.key = 'category' AND q.status = 'COMPLETED' " +
           "AND q.completedAt BETWEEN :startDate AND :endDate GROUP BY m.category")
    List<Object[]> getAveragePaymentByCategory(@Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);
    
    /**
     * Clean up old expired QR codes
     */
    @Modifying
    @Query("DELETE FROM QRCodePayment q WHERE q.status IN ('EXPIRED', 'CANCELLED') AND q.createdAt < :cutoffDate")
    int cleanupOldQRCodes(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    /**
     * Find suspicious payment patterns
     */
    @Query("SELECT q.payerUserId, q.userId, COUNT(q) FROM QRCodePayment q " +
           "WHERE q.status = 'COMPLETED' AND q.completedAt >= :since " +
           "GROUP BY q.payerUserId, q.userId HAVING COUNT(q) >= :threshold")
    List<Object[]> findSuspiciousPaymentPatterns(@Param("since") LocalDateTime since, @Param("threshold") Long threshold);
}