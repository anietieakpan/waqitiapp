package com.waqiti.payment.qrcode.repository;

import com.waqiti.payment.qrcode.model.QRCodePayment;
import com.waqiti.payment.qrcode.model.QRCodeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * QR Code Repository
 * 
 * Provides data access operations for QR code payments with
 * optimized queries for high-performance retrieval and analytics.
 * 
 * @version 2.0.0
 * @since 2025-01-15
 */
@Repository
public interface QRCodeRepository extends JpaRepository<QRCodePayment, String> {
    
    /**
     * Find QR code payment by QR code ID
     */
    Optional<QRCodePayment> findByQrCodeId(String qrCodeId);
    
    /**
     * Find QR codes by merchant ID
     */
    List<QRCodePayment> findByMerchantId(String merchantId);
    
    /**
     * Find QR codes by recipient ID
     */
    List<QRCodePayment> findByRecipientId(String recipientId);
    
    /**
     * Find QR codes created within date range
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.createdAt BETWEEN :startDate AND :endDate")
    List<QRCodePayment> findByDateRange(@Param("startDate") Instant startDate, 
                                        @Param("endDate") Instant endDate);
    
    /**
     * Find active QR codes (not expired, not cancelled, not used for single-use)
     */
    @Query("""
        SELECT q FROM QRCodePayment q 
        WHERE (q.expiresAt IS NULL OR q.expiresAt > :now)
        AND q.isCancelled = false
        AND (q.isStatic = true OR q.isUsed = false)
        """)
    List<QRCodePayment> findActiveQRCodes(@Param("now") Instant now);
    
    /**
     * Find expired QR codes that haven't been marked as expired
     */
    @Query("""
        SELECT q FROM QRCodePayment q 
        WHERE q.expiresAt IS NOT NULL 
        AND q.expiresAt < :now
        AND q.paymentStatus != 'EXPIRED'
        AND q.isCancelled = false
        """)
    List<QRCodePayment> findExpiredQRCodes(@Param("now") Instant now);
    
    /**
     * Find QR codes by type
     */
    List<QRCodePayment> findByType(QRCodeType type);
    
    /**
     * Find QR codes by payment status
     */
    List<QRCodePayment> findByPaymentStatus(String paymentStatus);
    
    /**
     * Find static merchant QR codes
     */
    @Query("SELECT q FROM QRCodePayment q WHERE q.isStatic = true AND q.merchantId = :merchantId")
    List<QRCodePayment> findStaticMerchantQRCodes(@Param("merchantId") String merchantId);
    
    /**
     * Count QR codes by type within date range
     */
    @Query("""
        SELECT q.type, COUNT(q) 
        FROM QRCodePayment q 
        WHERE q.createdAt BETWEEN :startDate AND :endDate
        GROUP BY q.type
        """)
    List<Object[]> countByTypeAndDateRange(@Param("startDate") Instant startDate,
                                           @Param("endDate") Instant endDate);
    
    /**
     * Get QR code scan statistics
     */
    @Query("""
        SELECT 
            COUNT(q) as total,
            SUM(CASE WHEN q.scannedAt IS NOT NULL THEN 1 ELSE 0 END) as scanned,
            SUM(CASE WHEN q.processedAt IS NOT NULL THEN 1 ELSE 0 END) as processed,
            AVG(q.processingTimeMillis) as avgProcessingTime
        FROM QRCodePayment q
        WHERE q.createdAt BETWEEN :startDate AND :endDate
        """)
    Object getStatistics(@Param("startDate") Instant startDate,
                        @Param("endDate") Instant endDate);
    
    /**
     * Get top merchants by QR code usage
     */
    @Query("""
        SELECT q.merchantId, q.merchantName, COUNT(q) as usageCount, 
               SUM(CASE WHEN q.processedAt IS NOT NULL THEN 1 ELSE 0 END) as processedCount
        FROM QRCodePayment q
        WHERE q.merchantId IS NOT NULL
        AND q.createdAt BETWEEN :startDate AND :endDate
        GROUP BY q.merchantId, q.merchantName
        ORDER BY usageCount DESC
        """)
    List<Object[]> getTopMerchants(@Param("startDate") Instant startDate,
                                   @Param("endDate") Instant endDate,
                                   org.springframework.data.domain.Pageable pageable);
    
    /**
     * Update QR code as expired
     */
    @Modifying
    @Query("UPDATE QRCodePayment q SET q.paymentStatus = 'EXPIRED' WHERE q.qrCodeId = :qrCodeId")
    void markAsExpired(@Param("qrCodeId") String qrCodeId);
    
    /**
     * Update scan information
     */
    @Modifying
    @Query("""
        UPDATE QRCodePayment q 
        SET q.scannedAt = :scannedAt, 
            q.scanCount = q.scanCount + 1,
            q.scannerDeviceId = :deviceId,
            q.scannerLocation = :location
        WHERE q.qrCodeId = :qrCodeId
        """)
    void updateScanInfo(@Param("qrCodeId") String qrCodeId,
                       @Param("scannedAt") Instant scannedAt,
                       @Param("deviceId") String deviceId,
                       @Param("location") String location);
    
    /**
     * Check if QR code ID exists
     */
    boolean existsByQrCodeId(String qrCodeId);
    
    /**
     * Delete expired QR codes older than specified date
     */
    @Modifying
    @Query("DELETE FROM QRCodePayment q WHERE q.expiresAt < :cutoffDate AND q.isUsed = true")
    int deleteExpiredQRCodes(@Param("cutoffDate") Instant cutoffDate);
    
    /**
     * Get hourly QR code generation statistics
     */
    @Query("""
        SELECT HOUR(q.createdAt) as hour, COUNT(q) as count
        FROM QRCodePayment q
        WHERE DATE(q.createdAt) = CURRENT_DATE
        GROUP BY HOUR(q.createdAt)
        ORDER BY hour
        """)
    List<Object[]> getHourlyStatistics();
    
    /**
     * Find QR codes pending payment
     */
    @Query("""
        SELECT q FROM QRCodePayment q
        WHERE q.scannedAt IS NOT NULL
        AND q.processedAt IS NULL
        AND q.isCancelled = false
        AND (q.expiresAt IS NULL OR q.expiresAt > :now)
        """)
    List<QRCodePayment> findPendingPayments(@Param("now") Instant now);
}