package com.waqiti.payment.repository;

import com.waqiti.payment.entity.FraudCheckRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FraudCheckRecordRepository extends JpaRepository<FraudCheckRecord, UUID> {
    
    Optional<FraudCheckRecord> findByTransactionId(String transactionId);
    
    List<FraudCheckRecord> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    @Query("SELECT f FROM FraudCheckRecord f WHERE f.userId = :userId AND f.createdAt >= :since")
    List<FraudCheckRecord> findRecentByUserId(@Param("userId") String userId, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(f) FROM FraudCheckRecord f WHERE f.userId = :userId AND f.createdAt >= :since")
    long countRecentChecksByUserId(@Param("userId") String userId, @Param("since") LocalDateTime since);
    
    @Query("SELECT f FROM FraudCheckRecord f WHERE f.requiresManualReview = true AND f.approved = false")
    Page<FraudCheckRecord> findPendingManualReviews(Pageable pageable);
    
    @Query("SELECT f FROM FraudCheckRecord f WHERE f.riskLevel = :riskLevel AND f.createdAt >= :since")
    List<FraudCheckRecord> findByRiskLevelSince(@Param("riskLevel") String riskLevel, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(f) FROM FraudCheckRecord f WHERE f.riskLevel IN ('HIGH', 'CRITICAL') AND f.createdAt >= :since")
    long countHighRiskTransactionsSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT AVG(f.riskScore) FROM FraudCheckRecord f WHERE f.createdAt >= :since")
    Double getAverageRiskScoreSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT f FROM FraudCheckRecord f WHERE f.deviceId = :deviceId ORDER BY f.createdAt DESC")
    List<FraudCheckRecord> findByDeviceIdOrderByCreatedAtDesc(@Param("deviceId") String deviceId, Pageable pageable);
    
    @Query("SELECT f FROM FraudCheckRecord f WHERE f.sourceIpAddress = :ipAddress ORDER BY f.createdAt DESC")
    List<FraudCheckRecord> findBySourceIpAddressOrderByCreatedAtDesc(@Param("ipAddress") String ipAddress, Pageable pageable);
    
    @Query("SELECT COUNT(f) FROM FraudCheckRecord f WHERE f.sourceIpAddress = :ipAddress AND f.createdAt >= :since")
    long countByIpAddressSince(@Param("ipAddress") String ipAddress, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(f) FROM FraudCheckRecord f WHERE f.deviceId = :deviceId AND f.createdAt >= :since")
    long countByDeviceIdSince(@Param("deviceId") String deviceId, @Param("since") LocalDateTime since);
    
    @Query("SELECT f FROM FraudCheckRecord f WHERE f.approved = false AND f.riskLevel IN ('HIGH', 'CRITICAL') ORDER BY f.reviewPriority DESC, f.createdAt ASC")
    Page<FraudCheckRecord> findBlockedHighRiskTransactions(Pageable pageable);
    
    @Query("SELECT f FROM FraudCheckRecord f WHERE f.mlModelScore IS NOT NULL AND f.createdAt >= :since")
    List<FraudCheckRecord> findMlScoredTransactionsSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT f.riskLevel, COUNT(f) FROM FraudCheckRecord f WHERE f.createdAt >= :since GROUP BY f.riskLevel")
    List<Object[]> countByRiskLevelSince(@Param("since") LocalDateTime since);
}