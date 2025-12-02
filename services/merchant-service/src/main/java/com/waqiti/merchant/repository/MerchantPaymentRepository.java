package com.waqiti.merchant.repository;

import com.waqiti.merchant.domain.MerchantPayment;
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

@Repository
public interface MerchantPaymentRepository extends JpaRepository<MerchantPayment, UUID> {

    Optional<MerchantPayment> findByPaymentId(String paymentId);
    
    List<MerchantPayment> findByMerchantId(UUID merchantId);
    
    Page<MerchantPayment> findByMerchantId(UUID merchantId, Pageable pageable);
    
    List<MerchantPayment> findByCustomerId(UUID customerId);
    
    List<MerchantPayment> findByStatus(MerchantPayment.PaymentStatus status);
    
    List<MerchantPayment> findByMerchantIdAndStatus(UUID merchantId, MerchantPayment.PaymentStatus status);
    
    Optional<MerchantPayment> findByOrderId(String orderId);
    
    Optional<MerchantPayment> findByExternalReference(String externalReference);
    
    @Query("SELECT mp FROM MerchantPayment mp WHERE mp.merchantId = :merchantId AND mp.createdAt BETWEEN :startDate AND :endDate")
    List<MerchantPayment> findByMerchantIdAndDateRange(
            @Param("merchantId") UUID merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT mp FROM MerchantPayment mp WHERE mp.merchantId = :merchantId AND mp.amount >= :minAmount")
    List<MerchantPayment> findByMerchantIdAndAmountGreaterThan(
            @Param("merchantId") UUID merchantId,
            @Param("minAmount") BigDecimal minAmount);
    
    @Query("SELECT mp FROM MerchantPayment mp WHERE mp.status = :status AND mp.expiresAt < :currentTime")
    List<MerchantPayment> findExpiredPayments(
            @Param("status") MerchantPayment.PaymentStatus status,
            @Param("currentTime") LocalDateTime currentTime);
    
    @Query("SELECT mp FROM MerchantPayment mp WHERE mp.status IN ('PENDING', 'AUTHORIZED') AND mp.createdAt < :cutoffDate")
    List<MerchantPayment> findStalePayments(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    @Query("SELECT SUM(mp.amount) FROM MerchantPayment mp WHERE mp.merchantId = :merchantId AND mp.status = 'SETTLED' AND mp.settledAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalSettledAmount(
            @Param("merchantId") UUID merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(mp.feeAmount) FROM MerchantPayment mp WHERE mp.merchantId = :merchantId AND mp.status = 'SETTLED' AND mp.settledAt BETWEEN :startDate AND :endDate")
    BigDecimal getTotalFeesCollected(
            @Param("merchantId") UUID merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(mp) FROM MerchantPayment mp WHERE mp.merchantId = :merchantId AND mp.createdAt BETWEEN :startDate AND :endDate")
    long getTransactionCount(
            @Param("merchantId") UUID merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT mp.paymentMethod, COUNT(mp) FROM MerchantPayment mp WHERE mp.merchantId = :merchantId GROUP BY mp.paymentMethod")
    List<Object[]> getPaymentMethodDistribution(@Param("merchantId") UUID merchantId);
    
    @Query("SELECT mp FROM MerchantPayment mp WHERE mp.fraudScore > :threshold")
    List<MerchantPayment> findHighRiskPayments(@Param("threshold") Integer threshold);
    
    @Query("SELECT mp FROM MerchantPayment mp WHERE mp.chargebackRisk > :threshold")
    List<MerchantPayment> findHighChargebackRiskPayments(@Param("threshold") Integer threshold);
    
    @Query("SELECT mp FROM MerchantPayment mp WHERE mp.ipAddress = :ipAddress AND mp.createdAt > :since")
    List<MerchantPayment> findRecentPaymentsByIpAddress(
            @Param("ipAddress") String ipAddress,
            @Param("since") LocalDateTime since);
    
    @Query("SELECT mp FROM MerchantPayment mp WHERE mp.deviceFingerprint = :deviceFingerprint AND mp.createdAt > :since")
    List<MerchantPayment> findRecentPaymentsByDeviceFingerprint(
            @Param("deviceFingerprint") String deviceFingerprint,
            @Param("since") LocalDateTime since);
    
    @Query("SELECT DATE(mp.createdAt), COUNT(mp), SUM(mp.amount) FROM MerchantPayment mp WHERE mp.merchantId = :merchantId AND mp.createdAt BETWEEN :startDate AND :endDate GROUP BY DATE(mp.createdAt)")
    List<Object[]> getDailyTransactionSummary(
            @Param("merchantId") UUID merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT mp FROM MerchantPayment mp WHERE mp.status = 'PENDING' AND mp.merchantId = :merchantId ORDER BY mp.createdAt ASC")
    List<MerchantPayment> findPendingPaymentsByMerchant(@Param("merchantId") UUID merchantId);
    
    @Query("SELECT mp FROM MerchantPayment mp WHERE mp.settlementBatchId = :batchId")
    List<MerchantPayment> findBySettlementBatchId(@Param("batchId") String batchId);
    
    boolean existsByProcessorTransactionId(String processorTransactionId);
}