package com.waqiti.payment.repository;

import com.waqiti.payment.entity.FundRelease;
import com.waqiti.payment.model.FundReleaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface FundReleaseRepository extends JpaRepository<FundRelease, Long> {
    
    Optional<FundRelease> findByReleaseId(String releaseId);
    
    boolean existsByReleaseIdAndStatus(String releaseId, FundReleaseStatus status);
    
    List<FundRelease> findByMerchantIdAndStatus(String merchantId, FundReleaseStatus status);
    
    List<FundRelease> findByStatus(FundReleaseStatus status);
    
    @Query("SELECT f FROM FundRelease f WHERE f.status = :status AND f.scheduledReleaseTime <= :now")
    List<FundRelease> findScheduledReleasesReadyForProcessing(
        @Param("status") FundReleaseStatus status, 
        @Param("now") Instant now
    );
    
    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FundRelease f " +
           "WHERE f.merchantId = :merchantId " +
           "AND f.status IN ('COMPLETED', 'PROCESSING') " +
           "AND f.createdAt >= :startOfDay " +
           "AND f.createdAt < :endOfDay")
    BigDecimal getTotalReleasedToday(
        @Param("merchantId") String merchantId,
        @Param("startOfDay") Instant startOfDay,
        @Param("endOfDay") Instant endOfDay
    );
    
    default BigDecimal getTotalReleasedToday(String merchantId) {
        Instant now = Instant.now();
        Instant startOfDay = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant endOfDay = startOfDay.plus(1, java.time.temporal.ChronoUnit.DAYS);
        return getTotalReleasedToday(merchantId, startOfDay, endOfDay);
    }
    
    @Query("SELECT f FROM FundRelease f WHERE f.batchId = :batchId ORDER BY f.createdAt")
    List<FundRelease> findByBatchId(@Param("batchId") String batchId);
    
    @Query("SELECT f FROM FundRelease f WHERE f.merchantId = :merchantId " +
           "AND f.createdAt >= :since ORDER BY f.createdAt DESC")
    List<FundRelease> findRecentReleasesByMerchant(
        @Param("merchantId") String merchantId, 
        @Param("since") Instant since
    );
    
    @Query("SELECT f FROM FundRelease f WHERE f.status = :status " +
           "AND f.retryCount < :maxRetries AND f.lastUpdated < :before")
    List<FundRelease> findFailedReleasesForRetry(
        @Param("status") FundReleaseStatus status,
        @Param("maxRetries") int maxRetries,
        @Param("before") Instant before
    );
    
    @Query("SELECT COUNT(f) FROM FundRelease f WHERE f.status = :status")
    long countByStatus(@Param("status") FundReleaseStatus status);
    
    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM FundRelease f " +
           "WHERE f.status = :status AND f.currency = :currency")
    BigDecimal getTotalAmountByStatusAndCurrency(
        @Param("status") FundReleaseStatus status,
        @Param("currency") String currency
    );
    
    @Query("SELECT f FROM FundRelease f WHERE f.orderId = :orderId")
    List<FundRelease> findByOrderId(@Param("orderId") String orderId);
    
    @Query("SELECT f FROM FundRelease f WHERE f.transactionId = :transactionId")
    Optional<FundRelease> findByTransactionId(@Param("transactionId") String transactionId);
}