package com.waqiti.analytics.repository;

import com.waqiti.analytics.entity.FraudDetection;
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
 * Fraud detection repository with advanced query methods
 */
@Repository
public interface FraudDetectionRepository extends JpaRepository<FraudDetection, UUID> {
    
    /**
     * Find by transaction ID
     */
    Optional<FraudDetection> findByTransactionId(UUID transactionId);
    
    /**
     * Find fraud events by user
     */
    Page<FraudDetection> findByUserId(UUID userId, Pageable pageable);
    
    /**
     * Find unresolved fraud cases
     */
    @Query("SELECT fd FROM FraudDetection fd WHERE fd.status IN ('DETECTED', 'INVESTIGATING') " +
           "ORDER BY fd.riskLevel DESC, fd.detectionDate ASC")
    Page<FraudDetection> findUnresolvedCases(Pageable pageable);
    
    /**
     * Find high-risk fraud events
     */
    List<FraudDetection> findByRiskLevelAndStatusIn(
        FraudDetection.RiskLevel riskLevel, 
        List<FraudDetection.DetectionStatus> statuses);
    
    /**
     * Count fraud events by type in date range
     */
    @Query("SELECT fd.fraudType, COUNT(fd) FROM FraudDetection fd " +
           "WHERE fd.detectionDate BETWEEN :startDate AND :endDate " +
           "GROUP BY fd.fraudType")
    List<Object[]> countFraudByTypeInDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Calculate fraud rate for user
     */
    @Query("SELECT COUNT(fd) * 100.0 / :totalTransactions FROM FraudDetection fd " +
           "WHERE fd.userId = :userId AND fd.status = 'CONFIRMED'")
    Double calculateUserFraudRate(
        @Param("userId") UUID userId,
        @Param("totalTransactions") Long totalTransactions);
    
    /**
     * Find velocity abuse patterns
     */
    @Query("SELECT fd FROM FraudDetection fd " +
           "WHERE fd.userId = :userId " +
           "AND fd.fraudType = 'VELOCITY_ABUSE' " +
           "AND fd.detectionDate >= :sinceDate " +
           "ORDER BY fd.detectionDate DESC")
    List<FraudDetection> findVelocityAbusePatterns(
        @Param("userId") UUID userId,
        @Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * Find fraud by merchant
     */
    List<FraudDetection> findByMerchantNameContainingIgnoreCase(String merchantName);
    
    /**
     * Get fraud statistics by risk level
     */
    @Query("SELECT fd.riskLevel, COUNT(fd), AVG(fd.riskScore), SUM(fd.transactionAmount) " +
           "FROM FraudDetection fd " +
           "WHERE fd.detectionDate >= :sinceDate " +
           "GROUP BY fd.riskLevel")
    List<Object[]> getFraudStatsByRiskLevel(@Param("sinceDate") LocalDateTime sinceDate);
    
    /**
     * Find false positives for model improvement
     */
    Page<FraudDetection> findByFalsePositiveTrue(Pageable pageable);
    
    /**
     * Update fraud status
     */
    @Modifying
    @Query("UPDATE FraudDetection fd SET fd.status = :newStatus, fd.actionDate = :actionDate, " +
           "fd.actionBy = :actionBy WHERE fd.id = :fraudId")
    void updateFraudStatus(
        @Param("fraudId") UUID fraudId,
        @Param("newStatus") FraudDetection.DetectionStatus newStatus,
        @Param("actionDate") LocalDateTime actionDate,
        @Param("actionBy") String actionBy);
    
    /**
     * Find recent fraud by IP address
     */
    List<FraudDetection> findBySourceIpAddressAndDetectionDateAfter(
        String ipAddress, LocalDateTime afterDate);
    
    /**
     * Find fraud patterns by device
     */
    List<FraudDetection> findByDeviceFingerprintAndStatusNot(
        String deviceFingerprint, FraudDetection.DetectionStatus status);
}